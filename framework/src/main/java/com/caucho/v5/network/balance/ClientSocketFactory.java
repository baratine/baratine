/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Baratine is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Baratine is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Baratine; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.network.balance;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.health.meter.ActiveMeter;
import com.caucho.v5.health.meter.CountMeter;
import com.caucho.v5.health.meter.MeterActiveTime;
import com.caucho.v5.health.meter.MeterService;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.QDate;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.ReadStream;
import com.caucho.v5.vfs.ReadWritePair;
import com.caucho.v5.vfs.VfsOld;

/**
 * A pool of connections to a server.
 * 
 * <h3>Fail Recover Time</h3>
 * 
 * The fail recover time is dynamic. The first timeout is 1s. After the 1s,
 * the client tries again. If that fails, the timeout is doubled until
 * reaching the maximum _loadBalanceRecoverTime.
 */
public class ClientSocketFactory implements ClientSocketFactoryApi
{
  private static final Logger log
    = Logger.getLogger(ClientSocketFactory.class.getName());
  private static final L10N L = new L10N(ClientSocketFactory.class);

  // number of chunks in the throttling
  private static final int WARMUP_MAX = 16;
  private static final int WARMUP_MIN = -16;
  private static final int []WARMUP_CONNECTION_MAX
    = new int[] { 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 8, 8, 16, 32, 64, 128 };

  private final String _sourceId;
  private final String _targetId;

  private final String _address;
  private final int _port;
  private final boolean _isSecure;

  private String _debugId;
  private String _statCategory;
  private String _statId;

  private PathImpl _tcpPath;
  
  private boolean _isHeartbeatServer;

  private int _maxConnections = Integer.MAX_VALUE / 2;

  private long _loadBalanceConnectTimeout = 5000;
  private long _loadBalanceConnectionMin = 0;
  private long _loadBalanceSocketTimeout = 30000;
  private long _loadBalanceIdleTime = 10000;
  private long _loadBalanceFailRecoverTime = 15000;
  private long _loadBalanceBusyRecoverTime = 15000;
  private long _loadBalanceWarmupTime = 60000;
  private int _loadBalanceWeight = 100;

  private ClientSocket []_idle = new ClientSocket[64];
  private volatile int _idleHead;
  private volatile int _idleTail;
  private int _idleSize = 16;

  private int _streamCount;

  private long _warmupChunkTime = 1;

  private long _failChunkTime = 1;

  private volatile State _state = State.NEW;

  // server start/stop sequence for heartbeat/restarts
  private volatile boolean _isHeartbeatActive;
  
  private final AtomicInteger _startSequenceId
    = new AtomicInteger();

  // current connection count
  private final AtomicInteger _activeCount = new AtomicInteger();
  private final AtomicInteger _startingCount = new AtomicInteger();

  private final AtomicInteger _loadBalanceAllocateCount = new AtomicInteger();

  // numeric value representing the throttle state
  private volatile int _warmupState;
  private volatile int _currentFailCount;

  // load management data
  private volatile long _lastFailConnectTime;
  private volatile long _dynamicFailRecoverTime = 1000L;

  private long _lastFailTime;
  private long _lastBusyTime;

  private long _failTime;
  private volatile long _firstSuccessTime;
  private volatile long _lastSuccessTime;
  private volatile long _prevSuccessTime;
  private volatile double _latencyFactor;

  // statistics
  private MeterActiveTime _requestTimeProbe;
  private ActiveMeter _connProbe;
  private ActiveMeter _idleProbe;
  private CountMeter _connFailProbe;
  private CountMeter _requestFailProbe;
  private CountMeter _requestBusyProbe;

  private volatile long _keepaliveCountTotal;
  private final AtomicLong _connectCountTotal = new AtomicLong();
  private final AtomicLong _failCountTotal = new AtomicLong();
  private volatile long _busyCountTotal;

  private volatile double _cpuLoadAvg;
  private volatile long _cpuSetTime;
  private ServerBartender _server;
  
  public ClientSocketFactory(ServerBartender server)
  {
    this(server, false);
  }
  
  public ClientSocketFactory(ServerBartender server, boolean isSecure)
  {
    this("client", server.getDisplayName(), null, null, server, isSecure); 
  }

  public ClientSocketFactory(String sourceId,
                             String targetId,
                             String statCategory,
                             String statId,
                             ServerBartender server,
                             boolean isSecure)
  {
    Objects.requireNonNull(server);
    
    _server = server;
    _sourceId = sourceId;

    if ("".equals(targetId))
      targetId = "default";

    _targetId = targetId;
    _debugId = _sourceId + "->" + _targetId;
    _address = server.getAddress();    
    _port = server.port();
    _isSecure = isSecure;

    _statCategory = statCategory;

    if (statId != null && ! "".equals(statId) && ! statId.startsWith("|"))
      statId = "|" + statId;

    _statId = statId;
  }

  /**
   * Returns the user-readable id of the target server.
   */
  @Override
  public String getId()
  {
    return _targetId;
  }

  /**
   * Returns the debug id.
   */
  @Override
  public String getDebugId()
  {
    return _debugId;
  }

  /**
   * Returns the hostname of the target server.
   */
  @Override
  public String getAddress()
  {
    return _address;
  }

  /**
   * Gets the port of the target server.
   */
  @Override
  public int getPort()
  {
    return _port;
  }

  /**
   * Foreign server is in the pod's heartbeat range.
   */
  public void setHeartbeatServer(boolean isHeartbeatServer)
  {
    _isHeartbeatServer = isHeartbeatServer;
  }
  /**
   * The socket timeout when connecting to the target server.
   */
  public long getLoadBalanceConnectTimeout()
  {
    return _loadBalanceConnectTimeout;
  }

  /**
   * The socket timeout when connecting to the target server.
   */
  public void setLoadBalanceConnectTimeout(long timeout)
  {
    _loadBalanceConnectTimeout = timeout;
  }

  /**
   * The minimum connections for green load balancing.
   */
  public long getLoadBalanceConnectionMin()
  {
    return _loadBalanceConnectionMin;
  }

  /**
   * The minimum connections for green load balancing.
   */
  public void setLoadBalanceConnectionMin(int connectionMin)
  {
    _loadBalanceConnectionMin = connectionMin;
  }

  /**
   * The socket timeout when reading from the target server.
   */
  public long getLoadBalanceSocketTimeout()
  {
    return _loadBalanceSocketTimeout;
  }

  /**
   * The socket timeout when reading from the target server.
   */
  public void setLoadBalanceSocketTimeout(long timeout)
  {
    _loadBalanceSocketTimeout = timeout;
  }

  /**
   * How long the connection can be cached in the free pool.
   */
  public long getLoadBalanceIdleTime()
  {
    return _loadBalanceIdleTime;
  }

  /**
   * How long the connection can be cached in the free pool.
   */
  public void setLoadBalanceIdleTime(long timeout)
  {
    _loadBalanceIdleTime = timeout;
  }

  /**
   * Returns how long the connection will be treated as dead.
   */
  public void setLoadBalanceRecoverTime(long timeout)
  {
    _loadBalanceFailRecoverTime = timeout;
  }
  
  public long getLoadBalanceRecoverTime()
  {
    return _loadBalanceFailRecoverTime;
  }

  /**
   * Returns how long the connection will be treated as dead.
   */
  public void setLoadBalanceBusyRecoverTime(long timeout)
  {
    _loadBalanceBusyRecoverTime = timeout;
  }
  
  public long getLoadBalanceBusyRecoverTime()
  {
    return _loadBalanceBusyRecoverTime;
  }

  /**
   * Returns the time in milliseconds for the slow start throttling.
   */
  public void setLoadBalanceWarmupTime(long timeout)
  {
    _loadBalanceWarmupTime = timeout;
  }
  
  public long getLoadBalanceWarmupTime()
  {
    return _loadBalanceWarmupTime;
  }

  /**
   * The load balance weight.
   */
  public int getLoadBalanceWeight()
  {
    return _loadBalanceWeight;
  }

  /**
   * The load balance weight.
   */
  public void setLoadBalanceWeight(int weight)
  {
    _loadBalanceWeight = weight;
  }

  /**
   * Returns the server start/stop sequence. Each enable/disable
   * increments the sequence, allowing old streams to be purge.
   */
  public int getStartSequenceId()
  {
    return _startSequenceId.get();
  }

  /**
   * Initialize
   */
  public void init()
  {
    _warmupChunkTime = Math.max(1, _loadBalanceWarmupTime / WARMUP_MAX);
    _failChunkTime = Math.max(1, _loadBalanceFailRecoverTime / WARMUP_MAX);

    String address = getAddress();

    if (address == null)
      address = "localhost";

    HashMap<String,Object> attr = new HashMap<String,Object>();
    attr.put("connect-timeout", _loadBalanceConnectTimeout);
    attr.put("socket-timeout", _loadBalanceSocketTimeout);
    attr.put("no-delay", true);

    if (_isSecure) {
      _tcpPath = VfsOld.lookup("tcps://" + address + ":" + _port, attr);
    }
    else {
      _tcpPath = VfsOld.lookup("tcp://" + address + ":" + _port, attr);
    }

    _state = State.STARTING;
  }

  //
  // statistics
  //

  /**
   * Returns the number of active connections.
   */
  public int getActiveCount()
  {
    return _activeCount.get();
  }

  /**
   * Returns the number of idle connections.
   */
  public int getIdleCount()
  {
    return (_idleHead - _idleTail + _idle.length) % _idle.length;
  }

  /**
   * Returns the number of load balance allocations
   */
  public int getLoadBalanceAllocateCount()
  {
    return _loadBalanceAllocateCount.get();
  }

  /**
   * Allocate a connection for load balancing.
   */
  public void allocateLoadBalance()
  {
    _loadBalanceAllocateCount.incrementAndGet();
  }

  /**
   * Free a connection for load balancing.
   */
  public void freeLoadBalance()
  {
    _loadBalanceAllocateCount.decrementAndGet();
  }

  /**
   * Returns the total number of successful socket connections
   */
  public long getConnectCountTotal()
  {
    return _connectCountTotal.get();
  }

  /**
   * Returns the number of times a keepalive connection has been used.
   */
  public long getKeepaliveCountTotal()
  {
    return _keepaliveCountTotal;
  }

  /**
   * Returns the total number of failed connect attempts.
   */
  public long getFailCountTotal()
  {
    return _failCountTotal.get();
  }

  /**
   * Returns the time of the last failure.
   */
  public Date getLastFailTime()
  {
    return new Date(_lastFailTime);
  }

  /**
   * Returns the time of the last failure.
   */
  public Date getLastFailConnectTime()
  {
    return new Date(_lastFailConnectTime);
  }

  /**
   * Returns the time of the last failure.
   */
  public long getLastSuccessTime()
  {
    return _lastSuccessTime;
  }

  /**
   * Returns the latency factory
   */
  public double getLatencyFactor()
  {
    long now = CurrentTime.getCurrentTime();

    long decayPeriod = 60000;

    long delta = decayPeriod - (now - _lastSuccessTime);

    // decay the latency factor over 60s
    
    if (delta <= 0)
      return 0;
    else
      return (_latencyFactor * delta) / decayPeriod;
  }

  /**
   * Returns the count of busy connections.
   */
  public long getBusyCountTotal()
  {
    return _busyCountTotal;
  }

  /**
   * Returns the time of the last busy.
   */
  public Date getLastBusyTime()
  {
    return new Date(_lastBusyTime);
  }

  /**
   * Sets the CPU load avg (from backend).
   */
  public void setCpuLoadAvg(double load)
  {
    _cpuSetTime = CurrentTime.getCurrentTime();
    _cpuLoadAvg = load;
  }

  /**
   * Gets the CPU load avg
   */
  public double getCpuLoadAvg()
  {
    double avg = _cpuLoadAvg;
    long time = _cpuSetTime;

    long now = CurrentTime.getCurrentTime();

    if (now - time < 10000L)
      return avg;
    else
      return avg * 10000L / (now - time);
  }

  /**
   * Returns true if the server is active.
   */
  @Override
  public final boolean isActive()
  {
    return _state.isLive();
  }
  
  /**
   * Returns true if the target server's heartbeat is active.
   */
  public final boolean isHeartbeatActive()
  {
    return _isHeartbeatActive;
  }

  /**
   * Returns true if the server is dead.
   */
  @Override
  public boolean isDead()
  {
    return ! isActive();
  }

  public boolean isFailed(long now)
  {
    if (now <= _failTime + _dynamicFailRecoverTime) {
      // we are in a possible failure and recover time has not yet expired
      
      if (_failTime <= _lastFailConnectTime) {
        // it was a connect failure, fail immediately
        logFinest("isFailed TRUE: prior connect failure");
        return true;
      }
      
      if (_lastSuccessTime <= _failTime) {
        // only fail for a read if there were no more recent successful reads
        logFinest("isFailed TRUE: prior read failure with no recent successes");
        return true;  
      }
      
      logFinest("isFailed FALSE: because there were recent successes");
    } else if (_failTime > 0) {
      logFinest(L.l("isFailed FALSE: not in window ({0} < {1} + {2})",
                    now, _failTime, _dynamicFailRecoverTime));
    }
    
    return false;
  }
  
  private void logFinest(String msg)
  {
    if (log.isLoggable(Level.FINEST))
      log.finest(getDebugId() + " " + msg);
  }

  public boolean isBusy(long now)
  {
    return (now < _lastBusyTime + _loadBalanceBusyRecoverTime);
  }
  
  /**
   * Enable the client
   */
  @Override
  public void enable()
  {
    start();
  }

  /**
   * Disable the client
   */
  @Override
  public void disable()
  {
    stop();
  }

  /**
   * Returns the lifecycle state.
   */
  @Override
  public String getState()
  {
    updateWarmup();

    return String.valueOf(_state);
  }

  /**
   * Returns true if the server can open a connection.
   */
  public boolean canOpen()
  {
    if (getIdleCount() > 0) {
      return true;
    }
    
    State state = _state;

    if (state == State.ACTIVE)
      return true;
    else if (! state.isEnabled())
      return false;
    else {
      long now = CurrentTime.getCurrentTime();

      if (isFailed(now)) {
        return false;
      }

      return true;
    }
  }

  /**
   * Returns true if the server can open a connection.
   */
  @Override
  public boolean canOpenWarmOrRecycle()
  {
    long now = CurrentTime.getCurrentTime();
    
    if (isFailed(now)) {
      return false;
    }
    
    return getIdleCount() > 0 || canOpenWarm();
  }

  /**
   * Returns true if the server can open a connection.
   */
  @Override
  public boolean canOpenWarm()
  {
    State state = _state;

    if (state == State.ACTIVE) {
      return true;
    }
    else if (state.isEnabled()) {
      long now = CurrentTime.getCurrentTime();

      if (isFailed(now)) {
        return false;
      }

      long firstSuccessTime = _firstSuccessTime;
      int warmupState = 0;

      if (firstSuccessTime > 0) {
        warmupState = (int) ((now - firstSuccessTime) / _warmupChunkTime);
      }

      warmupState -= _currentFailCount;

      if (warmupState < 0) {
        return (_failTime - warmupState * _failChunkTime < now);
      }
      else if (WARMUP_MAX <= warmupState) {
        return true;
      }

      int connectionMax = WARMUP_CONNECTION_MAX[warmupState];

      int idleCount = getIdleCount();
      int activeCount = _activeCount.get() + _startingCount.get();
      int totalCount = activeCount + idleCount;
      
      return totalCount < connectionMax;
    }
    else {
      return false;
    }
  }

  /**
   * Return true if active.
   */
  @Override
  public boolean isEnabled()
  {
    return _state.isEnabled();
  }

  @Override
  public void toBusy()
  {
    long now = CurrentTime.getCurrentTime();
    _lastBusyTime = now;
    _lastFailTime = now;
    _firstSuccessTime = 0;

    _requestBusyProbe.start();

    synchronized (this) {
      _busyCountTotal++;

      _state = _state.toBusy();
    }
  }

  @Override
  public void toFail()
  {
    _failTime = CurrentTime.getCurrentTime();
    _lastFailTime = _failTime;
    _firstSuccessTime = 0;

    getRequestFailProbe().start();

    _failCountTotal.incrementAndGet();

    _state = _state.toFail();

    clearRecycle();
  }

  /**
   * Called when the socket read/write fails.
   */
  @Override
  public void failSocket(long time)
  {
    getRequestFailProbe().start();

    _failCountTotal.incrementAndGet();
    
    logFinest(L.l("failSocket: time={0}, _failTime={1}",
                  time, _failTime));
    
    synchronized (this) {
      if (_failTime < time) {
        degrade(time);
        
        _firstSuccessTime = 0;
        _failTime = time;
        _lastFailTime = _failTime;

        _dynamicFailRecoverTime = Math.min(2 * _dynamicFailRecoverTime,
                                           _loadBalanceFailRecoverTime);
  
        _state = _state.toFail();
      }
    }
  }

  /**
   * Called when the socket read/write fails.
   */
  @Override
  public void failConnect(long time)
  {
    getConnectionFailProbe().start();

    _failCountTotal.incrementAndGet();
    
    logFinest(L.l("failConnect: time={0}, _failTime={1}",
                  time, _failTime));

    synchronized (this) {
      if (_failTime < time) {
        degrade(time);
        
        _firstSuccessTime = 0;
        _failTime = time;
        _lastFailTime = time;
        _lastFailConnectTime = time;
        
        _dynamicFailRecoverTime *= 2;
        if (_loadBalanceFailRecoverTime < _dynamicFailRecoverTime)
          _dynamicFailRecoverTime = _loadBalanceFailRecoverTime;
  
        _state = _state.toFail();
      }
    }
  }

  /**
   * Called when the server responds with "busy", e.g. HTTP 503
   */
  @Override
  public void busy(long time)
  {
    getRequestBusyProbe().start();

    synchronized (this) {
      degrade(time);
      
      _lastBusyTime = time;
      _firstSuccessTime = 0;

      _currentFailCount++;

      _busyCountTotal++;

      _state = _state.toBusy();
    }
  }
  
  private void degrade(long time)
  {
    // only degrade once per 100ms
    if (time - _failTime >= 100) {
      _currentFailCount++;
      _warmupState--;

      _warmupState = Math.min(WARMUP_MIN, _warmupState);
    }
  }

  /**
   * Called when the server has a successful response
   */
  @Override
  public void success()
  {
    _currentFailCount = 0;
    
    long now = CurrentTime.getCurrentTime();

    if (_firstSuccessTime <= 0) {
      _firstSuccessTime = now;
    }
    
    // reset the connection fail recover time
    _dynamicFailRecoverTime = 1000L;
  }

  /**
   * Enable the client.
   */
  @Override
  public void start()
  {
    // State state = _state;
    
    _state = _state.toStart();

    /*
    if (state != State.ACTIVE)
      _startSequenceId.incrementAndGet();
      */
  }

  /**
   * Disable the client.
   */
  @Override
  public void stop()
  {
    _state = _state.toStandby();

    _firstSuccessTime = 0;

    _startSequenceId.incrementAndGet();
    clearRecycle();
  }

  /**
   * Session only
   */
  @Override
  public void enableSessionOnly()
  {
    _state = _state.toSessionOnly();
  }

  /**
   * Open a stream to the target server, restricted by warmup.
   *
   * @return the socket's read/write pair.
   */
  @Override
  public ClientSocket openWarm()
  {
    State state = _state;

    if (! state.isEnabled()) {
      return null;
    }
    
    /*
    long now = CurrentTime.getCurrentTime();
    
    if (isFailed(now)) {
      return null;
    }
    */

    ClientSocket stream = openRecycle();

    if (stream != null) {
      return stream;
    }

    if (canOpenWarm()) {
      return connect();
    }
    else {
      return null;
    }
  }

  /**
   * Open a stream to the target server object persistence.
   *
   * @return the socket's read/write pair.
   */
  @Override
  public ClientSocket openIfLive()
  {
    if (_state.isClosed()) {
      return null;
    }

    ClientSocket stream = openRecycle();

    if (stream != null)
      return stream;

    long now = CurrentTime.getCurrentTime();

    if (isFailed(now))
      return null;
    else if (_state == State.FAIL && _startingCount.get() > 0) {
      // if in fail state, only one thread should try to connect
      return null;
    }

    return connect();
  }

  /**
   * Open a stream if the target server's heartbeat is active.
   *
   * @return the socket's read/write pair.
   */
  public ClientSocket openIfHeartbeatActive()
  {
    if (_state.isClosed()) {
      return null;
    }
    
    if (! _isHeartbeatActive && _isHeartbeatServer) {
      return null;
    }

    ClientSocket stream = openRecycle();

    if (stream != null)
      return stream;

    return connect();
  }

  /**
   * Open a stream to the target server for a session.
   *
   * @return the socket's read/write pair.
   */
  public ClientSocket openSticky()
  {
    State state = _state;
    if (! state.isSessionEnabled()) {
      return null;
    }

    ClientSocket stream = openRecycle();

    if (stream != null)
      return stream;

    long now = CurrentTime.getCurrentTime();

    if (isFailed(now)) {
      return null;
    }

    if (isBusy(now)) {
      return null;
    }

    return connect();
  }

  /**
   * Open a stream to the target server for the load balancer.
   *
   * @return the socket's read/write pair.
   */
  @Override
  public ClientSocket open()
  {
    State state = _state;
    
    if (! state.isInit())
      return null;

    ClientSocket stream = openRecycle();

    if (stream != null)
      return stream;

    return connect();
  }

  /**
   * Returns a valid recycled stream from the idle pool to the backend.
   *
   * If the stream has been in the pool for too long (> live_time),
   * close it instead.
   *
   * @return the socket's read/write pair.
   */
  private ClientSocket openRecycle()
  {
    long now = CurrentTime.getCurrentTime();
    ClientSocket stream = null;

    synchronized (this) {
      if (_idleHead != _idleTail) {
        stream = _idle[_idleHead];
        long freeTime = stream.getIdleStartTime();

        _idle[_idleHead] = null;
        _idleHead = (_idleHead + _idle.length - 1) % _idle.length;

        // System.out.println("RECYCLE: " + stream + " " + (freeTime - now) + " " + _loadBalanceIdleTime);
        
        if (now < freeTime + _loadBalanceIdleTime) {
          _activeCount.incrementAndGet();
          _keepaliveCountTotal++;

          stream.clearIdleStartTime();
          stream.toActive();

          return stream;
        }
      }
    }

    if (stream != null) {
      if (log.isLoggable(Level.FINER))
        log.finer(this + " close idle " + stream
                  + " expire=" + QDate.formatISO8601(stream.getIdleStartTime() + _loadBalanceIdleTime));

      stream.closeImpl();
    }

    return null;
  }

  /**
   * Connect to the backend server.
   *
   * @return the socket's read/write pair.
   */
  private ClientSocket connect()
  {
    if (_maxConnections <= _activeCount.get() + _startingCount.get()) {
      if (log.isLoggable(Level.WARNING)) {
        log.warning(this + " connect exceeded max-connections"
                    + "\n  max-connections=" + _maxConnections
                    + "\n  activeCount=" + _activeCount.get()
                    + "\n  startingCount=" + _startingCount.get());
      }
      
      return null;
    }

    _startingCount.incrementAndGet();

    State state = _state;
    
    if (! state.isInit()) {
      _startingCount.decrementAndGet();
      
      IllegalStateException e = new IllegalStateException(L.l("'{0}' connection cannot be opened because the server pool has not been started.", this));

      log.log(Level.WARNING, e.toString(), e);

      throw e;
    }
    
    if (getPort() <= 0) {
      return null;
    }
    
    long connectionStartTime = CurrentTime.getCurrentTime();

    try {
      ReadWritePair pair = openTCPPair();
      ReadStream rs = pair.getReadStream();
      rs.setEnableReadTime(true);
      
      //rs.setAttribute("timeout", new Integer((int) _loadBalanceSocketTimeout));

      _activeCount.incrementAndGet();
      
      _connectCountTotal.incrementAndGet();

      ClientSocket stream = new ClientSocket(this, _streamCount++,
                                               rs, pair.getWriteStream());

      if (log.isLoggable(Level.FINER))
        log.finer("connect " + stream);

      if (_firstSuccessTime <= 0) {
        if (_state.isStarting()) {
          if (_loadBalanceWarmupTime > 0)
            _state = State.WARMUP;
          else
            _state = State.ACTIVE;

          _firstSuccessTime = CurrentTime.getCurrentTime();
        }

        if (_warmupState < 0)
          _warmupState = 0;
      }

      return stream;
    } catch (IOException e) {
      if (log.isLoggable(Level.FINEST))
        log.log(Level.FINEST, this + " " + e.toString(), e);
      else
        log.finer(this + " " + e.toString());

      failConnect(connectionStartTime);

      return null;
    } finally {
      _startingCount.decrementAndGet();
    }
  }

  /**
   * We now know that the server is live, e.g. if a sibling has
   * contacted us.
   */
  @Override
  public void wake()
  {
    synchronized (this) {
      if (_state == State.FAIL) {
        _state = State.STARTING;
      }

      _failTime = 0;
    }
  }

  /**
   * Free the read/write pair for reuse.  Called only from
   * ClusterStream.free().
   *
   * @param stream the stream to free
   */
  void free(ClientSocket stream)
  {
    success();

    _activeCount.decrementAndGet();

    synchronized (this) {
      int size = (_idleHead - _idleTail + _idle.length) % _idle.length;

      if (_state != State.CLOSED && size < _idleSize) {
        _idleHead = (_idleHead + 1) % _idle.length;
        _idle[_idleHead] = stream;

        stream = null;
      }

      long now = CurrentTime.getCurrentTime();

      long prevSuccessTime = _prevSuccessTime;

      if (prevSuccessTime > 0) {
        _latencyFactor = (0.95 * _latencyFactor
                          + 0.05 * (now - prevSuccessTime));
      }

      if (_activeCount.get() > 0)
        _prevSuccessTime = now;
      else
        _prevSuccessTime = 0;

      _lastSuccessTime = now;
      
      if (log.isLoggable(Level.FINEST)) { 
        logFinest(L.l("free: _lastSuccessTime={0}, _failTime={1}",
                      now, _failTime));
      }
    }

    updateWarmup();

    long now = CurrentTime.getCurrentTime();
    long maxIdleTime = _loadBalanceIdleTime;
    ClientSocket oldStream = null;

    do {
      oldStream = null;

      synchronized (this) {
        if (_idleHead != _idleTail) {
          int nextTail = (_idleTail + 1) % _idle.length;

          oldStream = _idle[nextTail];

          if (oldStream != null
              && oldStream.getIdleStartTime() + maxIdleTime < now) {
            _idle[nextTail] = null;
            _idleTail = nextTail;
          }
          else
            oldStream = null;
        }
      }

      if (oldStream != null)
        oldStream.closeImpl();
    } while (oldStream != null);

    if (stream != null) {
      stream.closeImpl();
    }
  }

  private void updateWarmup()
  {
    synchronized (this) {
      if (! isEnabled())
        return;

      long now = CurrentTime.getCurrentTime();
      int warmupState = _warmupState;

      if (warmupState >= 0 && _firstSuccessTime > 0) {
        warmupState = (int) ((now - _firstSuccessTime) / _warmupChunkTime);

        // reset the connection fail recover time
        _dynamicFailRecoverTime = 1000L;

        if (WARMUP_MAX <= warmupState) {
          warmupState = WARMUP_MAX;
          _state = _state.toActive();
        }
      }

      _warmupState = warmupState;
    }
  }

  /**
   * Closes the read/write pair for reuse.  Called only
   * from ClusterStream.close().
   */
  void close(ClientSocket stream)
  {
    if (log.isLoggable(Level.FINER))
      log.finer("close " + stream);

    _activeCount.decrementAndGet();
  }

  /**
   * Notify that a heartbeat start has occurred.
   */
  @Override
  public void notifyHeartbeatStart()
  {
    _isHeartbeatActive = true;
    // _startSequenceId.incrementAndGet();

    clearRecycle();
    wake();
  }

  /**
   * Notify that a heartbeat stop has occurred.
   */
  @Override
  public void notifyHeartbeatStop()
  {
    _isHeartbeatActive = false;
    _startSequenceId.incrementAndGet();

    clearRecycle();
    toFail();
  }

  /**
   * Clears the recycled connections, e.g. on detection of backend
   * server going down.
   */
  public void clearRecycle()
  {
    ArrayList<ClientSocket> recycleList = null;

    synchronized (this) {
      _idleHead = _idleTail = 0;

      for (int i = 0; i < _idle.length; i++) {
        ClientSocket stream;

        stream = _idle[i];
        _idle[i] = null;

        if (stream != null) {
          if (recycleList == null)
            recycleList = new ArrayList<ClientSocket>();

          recycleList.add(stream);
        }
      }
    }

    if (recycleList != null) {
      for (ClientSocket stream : recycleList) {
        stream.closeImpl();
      }
    }
  }

  /**
   * Close the client
   */
  public void close()
  {
    synchronized (this) {
      if (_state == State.CLOSED)
        return;

      _state = State.CLOSED;
    }

    synchronized (this) {
      _idleHead = _idleTail = 0;
    }

    for (int i = 0; i < _idle.length; i++) {
      ClientSocket stream;

      synchronized (this) {
        stream = _idle[i];
        _idle[i] = null;
      }

      if (stream != null)
        stream.closeImpl();
    }
  }

  /**
   * Open a read/write pair to the target srun connection.
   *
   * @return the socket's read/write pair.
   */
  ReadWritePair openTCPPair()
    throws IOException
  {
    return _tcpPath.openReadWrite();
  }

  /**
   * Returns true if can connect to the client.
   */
  public boolean canConnect()
  {
    try {
      wake();

      ClientSocket stream = open();

      if (stream != null) {
        stream.free(stream.getIdleStartTime());

        return true;
      }

      return false;
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);

      return false;
    }
  }

  //
  // BAM API
  //

  //
  // statistics
  //

  public ActiveMeter getConnectionProbe()
  {
    if (_connProbe == null) {
      _connProbe
        = MeterService.createActiveMeter(_statCategory + "|Connection",
                                         _statId);
    }

    return _connProbe;
  }

  public CountMeter getConnectionFailProbe()
  {
    if (_connFailProbe == null) {
      String name = _statCategory + "|Connection Fail|" + _statId;
      _connFailProbe = MeterService.createCountMeter(name);
    }

    return _connFailProbe;
  }

  public MeterActiveTime getRequestTimeProbe()
  {
    if (_requestTimeProbe == null) {
      _requestTimeProbe
        = MeterService.createActiveTimeMeter(_statCategory + "|Request",
                                             "Time", _statId);
    }

    return _requestTimeProbe;
  }

  public CountMeter getRequestFailProbe()
  {
    if (_requestFailProbe == null) {
      String name = _statCategory + "|Request Fail" + _statId;
      _requestFailProbe = MeterService.createCountMeter(name);
    }

    return _requestFailProbe;
  }

  public CountMeter getRequestBusyProbe()
  {
    if (_requestBusyProbe == null) {
      String name = _statCategory + "|Request Busy" + _statId;
      _requestBusyProbe = MeterService.createCountMeter(name);
    }

    return _requestBusyProbe;
  }

  public ActiveMeter getIdleProbe()
  {
    if (_idleProbe == null) {
      _idleProbe
        = MeterService.createActiveMeter(_statCategory + "|Idle",
                                         _statId);
    }

    return _idleProbe;
  }

  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + getDebugId()
            + "," + _address + ":" + _port + "]");
  }

  enum State {
    NEW {
      boolean isInit() { return false; }
      boolean isEnabled() { return false; }
      boolean isSessionEnabled() { return false; }
    },
    
    STANDBY {
      boolean isEnabled() { return false; }
      boolean isSessionEnabled() { return false; }

      State toActive() { return this; }
      State toFail() { return this; }
      State toBusy() { return this; }
      State toSessionOnly() { return this; }
    },
    
    SESSION_ONLY {
      boolean isEnabled() { return false; }

      // XXX: should change to standby?
      State toFail() { return this; }
      State toBusy() { return this; }
    },
    // the following 5 are the active states
    STARTING {
      boolean isStarting() { return true; }
      boolean isLive() { return true; }
    },
    WARMUP {
      boolean isStarting() { return true; }
      boolean isLive() { return true; }

      State toStart() { return this; }
    },
    BUSY {
      boolean isStarting() { return true; }
    },
    FAIL {
      boolean isStarting() { return true; }
      boolean isLive() { return false; }
    },
    ACTIVE {
      boolean isLive() { return true; }

      State toStart() { return this; }
    },
    CLOSED {
      boolean isInit() { return false; }
      boolean isClosed() { return true; }
      boolean isSessionEnabled() { return false; }
      boolean isEnabled() { return false; }
      boolean isLive() { return false; }

      State toStart() { return this; }
      State toActive() { return this; }
      State toBusy() { return this; }
      State toFail() { return this; }
      State toStandby() { return this; }
      State toSessionOnly() { return this; }
    };

    boolean isInit() { return true; }
    boolean isClosed() { return false; }
    boolean isStarting() { return false; }
    boolean isLive() { return false; }
    boolean isSessionEnabled() { return true; }
    boolean isEnabled() { return true; }

    State toStart() { return STARTING; }
    State toActive() { return ACTIVE; }
    State toFail() { return FAIL; }
    State toBusy() { return BUSY; }
    State toStandby() { return STANDBY; }
    State toSessionOnly() { return SESSION_ONLY; }
  }
}
