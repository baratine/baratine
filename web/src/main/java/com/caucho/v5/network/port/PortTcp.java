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

package com.caucho.v5.network.port;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.thread.ThreadPool;
import com.caucho.v5.health.meter.ActiveMeter;
import com.caucho.v5.health.meter.CountMeter;
import com.caucho.v5.health.meter.MeterService;
import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.SSLFactory;
import com.caucho.v5.io.ServerSocketBar;
import com.caucho.v5.io.SocketBar;
import com.caucho.v5.io.SocketSystem;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.util.Alarm;
import com.caucho.v5.util.AlarmListener;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.FreeRingDual;
import com.caucho.v5.util.Friend;
import com.caucho.v5.util.L10N;

/**
 * Represents a protocol connection.
 */
//@Configurable
public class PortTcp implements PortSocket
{
  private static final L10N L = new L10N(PortTcp.class);

  private static final Logger log
    = Logger.getLogger(PortTcp.class.getName());

  private static final int KEEPALIVE_MAX = 65536;

  private static final ActiveMeter _keepaliveThreadMeter
    = MeterService.createActiveMeter("Caucho|Port|Keepalive Thread");

  private final AtomicInteger _connectionCount;
  private final AtomicLong _connectionSequence;

  // started at 128, but that seems wasteful since the active threads
  // themselves are buffering the free connections
  private FreeRingDual<ConnectionTcp> _idleConn
    = new FreeRingDual<>(256, 2 * 1024);

  // The owning server
  // private ProtocolDispatchServer _server;

  private ThreadPool _threadPool = ThreadPool.current();

  //private IdleThreadManager _connThreadPool;

  private ClassLoader _classLoader
    = Thread.currentThread().getContextClassLoader();

  // The id
  private String _serverId = "";

  // The address
  private String _address;

  // The port
  private final int _port;

  // path for unix sockets
  private Path _unixPath;

  // URL for debugging
  private String _url;

  // The protocol
  private final Protocol _protocol;

  // The SSL factory, if any
  private SSLFactory _sslFactory;

  // Secure override for load-balancers/proxies
  private boolean _isSecure;

  private InetAddress _socketAddress;

  private int _acceptListenBacklog = 4000;

  private int _keepaliveMax = -1;

  private long _keepaliveTimeMax = 10 * 60 * 1000L;
  private long _keepaliveTimeout = 120 * 1000L;

  private boolean _isKeepaliveAsyncEnable = true;
  private long _keepalivePollThreadTimeout = 1000;

  // default timeout
  private long _socketTimeout = 120 * 1000L;

  private long _suspendReaperTimeout = 60000L;

  private long _requestTimeout = -1;

  private boolean _isTcpNoDelay = true;
  private boolean _isTcpKeepalive;
  private boolean _isTcpCork;

  private boolean _isEnableJni = true;

  // the server socket
  private ServerSocketBar _serverSocket;

  // the throttle
  private ThrottleSocket _throttle;

  // the selection manager
  private PollTcpManagerBase _pollManager;

  // active set of all connections
  private ConcurrentHashMap<ConnectionTcp,ConnectionTcp> _activeConnectionSet
    = new ConcurrentHashMap<>();
  
  private AcceptTcp _acceptTask;

  private final AtomicInteger _activeConnectionCount = new AtomicInteger();

  // server push (comet) suspend set
  //private Set<ConnectionTcp> _suspendConnectionSet
  //  = Collections.synchronizedSet(new HashSet<ConnectionTcp>());

  // active requests that are closing after the request like an access-log
  // but should not trigger a new thread launch.
  //private final AtomicInteger _shutdownRequestCount = new AtomicInteger();

  // reaper alarm for timed out comet requests
  private Alarm _suspendAlarm;

  // statistics
  private final PortStats _stats = new PortStats(this);

  // total keepalive
  private AtomicInteger _keepaliveAllocateCount = new AtomicInteger();
  // thread-based
  private AtomicInteger _keepaliveThreadCount = new AtomicInteger();
  // True if the port has been bound
  private final AtomicBoolean _isBind = new AtomicBoolean();
  private final AtomicBoolean _isPostBind = new AtomicBoolean();

  // The port lifecycle
  private final Lifecycle _lifecycle = new Lifecycle();

  private ServicesAmp _services;

  private PortTcpBuilder _builder;

  public PortTcp(PortTcpBuilder builder)
  {
    _builder = builder;
    
    _keepalivePollThreadTimeout = 60000;
    
    _services = builder.ampManager(); // AmpSystem.getCurrentManager();
    Objects.requireNonNull(_services);
    
    _protocol = builder.protocol();
    Objects.requireNonNull(_protocol);
    
    //_serverSocket = builder.serverSocket();
    
    if (_serverSocket != null) {
      _port = _serverSocket.getLocalPort();
    }
    else {
      _port = builder.port();
    }
    
    _address = builder.address();
    
    if (_address != null) {
      try {
        _socketAddress = InetAddress.getByName(_address);
      } catch (Exception e) {
        log.log(Level.WARNING, e.toString(), e);
      }
    }
    
    _sslFactory = builder.sslFactory();

    _connectionCount = new AtomicInteger();
    _connectionSequence = builder.getConnectionSequence();
  }
  
  @Override
  public ServicesAmp services()
  {
    return _services;
  }

  ClassLoader classLoader()
  {
    return _classLoader;
  }

  /**
   * Returns the protocol handler responsible for generating protocol-specific
   * ProtocolConnections.
   */
  Protocol protocol()
  {
    return _protocol;
  }

  /**
   * Gets the protocol name.
   */
  public String protocolName()
  {
    if (_protocol != null)
      return _protocol.name();
    else
      return null;
  }

  /**
   * Gets the IP address
   */
  public String address()
  {
    return _address;
  }

  /**
   * Gets the port.
   */
  public int port()
  {
    return _port;
  }

  /**
   * Gets the local port (for ephemeral ports)
   */
  public int getLocalPort()
  {
    if (_serverSocket != null)
      return _serverSocket.getLocalPort();
    else
      return _port;
  }

  /**
   * Gets the unix path
   */
  public Path getSocketPath()
  {
    return _unixPath;
  }

  /**
   * Return true for secure
   */
  boolean isSecure()
  {
    return _isSecure || _sslFactory != null;
  }

  //
  // Configuration/Tuning
  //

  /**
   * Returns the max time for a request.
   */
  long getRequestTimeout()
  {
    return _requestTimeout;
  }

  /**
   * Gets the read timeout for the accepted sockets.
   */
  private long getSocketTimeout()
  {
    return _socketTimeout;
  }

  long getKeepaliveTimeout()
  {
    return _keepaliveTimeout;
  }

  private boolean isKeepaliveAsyncEnabled()
  {
    return _isKeepaliveAsyncEnable;
  }

  private long getBlockingTimeoutForPoll()
  {
    long timeout = _keepalivePollThreadTimeout;

    if (timeout <= 10)
      return timeout;
    else if (_threadPool.getFreeThreadCount() < 64)
      return 10;
    else
      return timeout;
  }

  //
  // statistics
  //
  
  /**
   * Returns true if the port is active.
   */
  boolean isActive()
  {
    return _lifecycle.isActive();
  }

  String url()
  {
    if (_url == null) {
      StringBuilder url = new StringBuilder();

      if (_protocol != null)
        url.append(_protocol.name());
      else
        url.append("unknown");
      
      if (isSecure()) {
        url.append("s");
      }
      
      url.append("://");

      if (address() != null)
        url.append(address());
      else
        url.append("*");
      url.append(":");
      url.append(port());

      if (_serverId != null && ! "".equals(_serverId)) {
        url.append("(");
        url.append(_serverId);
        url.append(")");
      }

      _url = url.toString();
    }

    return _url;
  }

  /**
   * Starts the port listening.
   */
  public void bind()
    throws Exception
  {
    if (_isBind.getAndSet(true)) {
      return;
    }

    if (_protocol == null) {
      throw new IllegalStateException(L.l("'{0}' must have a configured protocol before starting.", this));
    }

    // server 1e07
    if (_port < 0 && _unixPath == null) {
      return;
    }

    SocketSystem system = SocketSystem.current();

    if (_throttle == null) {
      _throttle = new ThrottleSocket();
    }
    
    //String protocolName = _protocol.name();
    
    String ssl = _sslFactory != null ? "s" : "";

    if (_serverSocket != null) {
      InetAddress address = _serverSocket.getLocalAddress();
      
      if (address != null)
        log.info("listening to " + address.getHostName() + ":" + _serverSocket.getLocalPort());
      else
        log.info("listening to *:" + _serverSocket.getLocalPort());
    }
    /*
    else if (_sslFactory != null && _socketAddress != null) {
      _serverSocket = _sslFactory.create(_socketAddress, _port);

      log.info(protocolName + "s listening to " + _socketAddress.getHostName() + ":" + _port);
    }
    else if (_sslFactory != null) {
      if (_address == null) {
        _serverSocket = _sslFactory.create(null, _port);
        log.info(protocolName + "s listening to *:" + _port);
      }
      else {
        InetAddress addr = InetAddress.getByName(_address);

        _serverSocket = _sslFactory.create(addr, _port);

        log.info(protocolName + "s listening to " + _address + ":" + _port);
      }
    }
    */
    else if (_socketAddress != null) {
      _serverSocket = system.openServerSocket(_socketAddress, _port,
                                              _acceptListenBacklog,
                                              _isEnableJni);
      
      log.info(_protocol.name() + ssl + " listening to " + _socketAddress.getHostName() + ":" + _serverSocket.getLocalPort());
    }
    else {
      _serverSocket = system.openServerSocket(null, _port, _acceptListenBacklog,
                                              _isEnableJni);
      
      log.info(_protocol.name() + ssl + " listening to *:"
               + _serverSocket.getLocalPort());
    }

    assert(_serverSocket != null);

    postBind();
  }

  /**
   * Starts the port listening.
   */
  public void bind(ServerSocketBar ss)
    throws IOException
  {
    Objects.requireNonNull(ss);

    _isBind.set(true);

    if (_protocol == null)
      throw new IllegalStateException(L.l("'{0}' must have a configured protocol before starting.", this));

    if (_throttle == null)
      _throttle = new ThrottleSocket();

    _serverSocket = ss;

    String scheme = _protocol.name();

    if (_address != null)
      log.info(scheme + " listening to " + _address + ":" + _port);
    else
      log.info(scheme + " listening to *:" + _port);

    if (_sslFactory != null) {
      try {
        _serverSocket = _sslFactory.bind(_serverSocket);
      } catch (RuntimeException e) {
        throw e;
      } catch (IOException e) {
        throw e;
      } catch (Exception e) {
        throw new IOException(e);
      }
    }
  }

  private void postBind()
  {
    if (_isPostBind.getAndSet(true)) {
      return;
    }

    if (_serverSocket == null) {
      return;
    }

    _serverSocket.setTcpNoDelay(_isTcpNoDelay);
    _serverSocket.setTcpKeepalive(_isTcpKeepalive);
    _serverSocket.setTcpCork(_isTcpCork);

    _serverSocket.setConnectionSocketTimeout((int) getSocketTimeout());

    if (isKeepaliveAsyncEnabled()) {
      if (_serverSocket.isJni()) {
        _pollManager = _builder.pollManager();
      }
      
      if (_pollManager == null) {
        _pollManager = new PollTcpManagerThread();
        _pollManager.start();
      }
    }
    /*
    else {
      _selectManager = NioSelectManager.create();
    }
    */


    if (_keepaliveMax < 0 && _pollManager != null) {
      _keepaliveMax = _pollManager.pollMax();
    }

    if (_keepaliveMax < 0) {
      _keepaliveMax = KEEPALIVE_MAX;
    }

    //_admin.register();
  }

  /**
   * Starts the port listening.
   */
  public void start()
    throws Exception
  {
    if (_port < 0 && _unixPath == null) {
      return;
    }

    if (! _lifecycle.toStarting())
      return;

    boolean isValid = false;
    try {
      bind();
      postBind();

      enable();

      _acceptTask = new AcceptTcp(this, _serverSocket);
      _threadPool.execute(_acceptTask);

      // _connThreadPool.start();

      _suspendAlarm = new Alarm(new SuspendReaper());
      _suspendAlarm.runAfter(_suspendReaperTimeout);

      isValid = true;
    } finally {
      if (! isValid) {
        close();
      }
    }
  }

  /**
   * Starts the port listening for new connections.
   */
  private void enable()
  {
    if (_lifecycle.toActive()) {
      if (_serverSocket != null) {
        _serverSocket.listen(_acceptListenBacklog);
      }
    }
  }

  /**
   * returns the select manager.
   */
  PollTcpManagerBase pollManager()
  {
    return _pollManager;
  }

  /**
   * Returns the next unique connection sequence.
   */
  long nextConnectionSequence()
  {
    return _connectionSequence.incrementAndGet();
  }

  /**
   * Notification when a socket closes.
   */
  void closeSocket(SocketBar socket)
  {
    if (_throttle != null) {
      _throttle.close(socket);
    }
  }

  /**
   * Allocates a keepalive for the connection.
   *
   * @param connectionStartTime - when the connection's accept occurred.
   */
  boolean isKeepaliveAllowed(long connectionStartTime)
  {
    if (! _lifecycle.isActive()) {
      return false;
    }
    else if (connectionStartTime + _keepaliveTimeMax < CurrentTime.currentTime()) {
      return false;
    }
    else if (_keepaliveMax <= _keepaliveAllocateCount.get()) {
      return false;
    }
    /*
    else if (_connThreadPool.isThreadMax()
             && _connThreadPool.isIdleLow()
             && ! isKeepaliveAsyncEnabled()) {
      return false;
    }
    */
    else {
      return true;
    }
  }

  /**
   * When true, use the async manager to wait for reads rather than
   * blocking.
   */
  private boolean isAsyncThrottle()
  {
    return isKeepaliveAsyncEnabled();// && _connThreadPool.isThreadHigh();
  }

  /**
   * Reads data from a keepalive connection
   */
  int keepaliveThreadRead(ReadStream is, long timeoutConn)
    throws IOException
  {
    if (isClosed()) {
      return -1;
    }

    int available = is.availableBuffer();

    if (available > 0) {
      return available;
    }

    long timeout = Math.min(getKeepaliveTimeout(), getSocketTimeout());
    
    if (timeoutConn > 0) {
      timeout = Math.min(timeout, timeoutConn);
    }

    // server/2l02
    int keepaliveThreadCount = _keepaliveThreadCount.incrementAndGet();

    // boolean isSelectManager = getServer().isSelectManagerEnabled();

    try {
      int result;

      if (isKeepaliveAsyncEnabled() && _pollManager != null) {
        timeout = Math.min(timeout, getBlockingTimeoutForPoll());

        if (keepaliveThreadCount > 32) {
          // throttle the thread keepalive when heavily loaded to save threads
          if (isAsyncThrottle()) {
            // when async throttle is active move the thread to async
            // immediately
            return 0;
          }
          else {
            timeout = Math.min(timeout, 100);
          }
        }
      }

      /*
      if (timeout < 0)
        timeout = 0;
        */

      if (timeout <= 0) {
        return 0;
      }

      _keepaliveThreadMeter.start();

      try {
        /*
        if (false && _keepaliveThreadCount.get() < 32) {
          // benchmark perf with memcache
          result = is.fillWithTimeout(-1);
        }
        */

        result = is.fillWithTimeout(timeout);
      } finally {
        _keepaliveThreadMeter.end();
      }

      if (isClosed()) {
        return -1;
      }

      return result;
    } catch (IOException e) {
      if (isClosed()) {
        log.log(Level.FINEST, e.toString(), e);

        return -1;
      }

      throw e;
    } finally {
      _keepaliveThreadCount.decrementAndGet();
    }
  }

  /**
   * Returns true if the port is closed.
   */
  public boolean isClosed()
  {
    return _lifecycle.getState().isDestroyed();
  }

  //
  // statistics
  //

  PortStats stats()
  {
    return _stats;
  }
  
  /**
   * Returns the number of connections
   */
  int getConnectionCount()
  {
    return _activeConnectionCount.get();
  }

  /**
   * Find the TcpConnection based on the thread id (for admin)
   */
  public ConnectionTcp findConnectionByThreadId(long threadId)
  {
    ArrayList<ConnectionTcp> connList
      = new ArrayList<ConnectionTcp>(_activeConnectionSet.keySet());

    /*
    for (ConnectionTcp conn : connList) {
      if (conn.getThreadId() == threadId)
        return conn;
    }
    */

    return null;
  }

  ConnectionTcp newConnection()
  {
    ConnectionTcp startConn = _idleConn.allocate();

    if (startConn == null) {
      int connId = _connectionCount.incrementAndGet();
      SocketBar socket = _serverSocket.createSocket();

      /*
      if (CurrentTime.isTest() && ! log.isLoggable(Level.FINER)) {
        connId = 1;
      }
      */

      startConn = new ConnectionTcp(connId, this, socket);
    }

    _activeConnectionSet.put(startConn,startConn);
    _activeConnectionCount.incrementAndGet();

    return startConn;
  }

  /**
   * Closes the stats for the connection.
   */
  @Friend(ConnectionTcp.class)
  void freeConnection(ConnectionTcp conn)
  {
    if (removeConnection(conn)) {
      _idleConn.free(conn);
    }
    else if (isActive()) {
      // Thread.dumpStack();
      System.out.println("Possible Double Close: " + this + " " + conn);
    }

    //_connThreadPool.wake();
  }
  
  boolean removeConnection(ConnectionTcp conn)
  {
    if (_activeConnectionSet.remove(conn) != null) {
      _activeConnectionCount.decrementAndGet();
      
      return true;
    }
    else {
      return false;
    }
    
  }

  void ssl(SocketBar socket)
  {
    SSLFactory sslFactory = _sslFactory;
    
    if (sslFactory != null) {
      socket.ssl(sslFactory);
    }
  }

  /**
   * Shuts the Port down.  The server gives connections 30
   * seconds to complete.
   */
  public void close()
  {
    if (! _lifecycle.toDestroy()) {
      return;
    }
    
    try {
      closeImpl();
    } catch (Exception e) {
      _lifecycle.toDestroy();
    }
  }
  
  private void closeImpl()
  {
    if (log.isLoggable(Level.FINE)) {
      log.fine("closing " + this);
    }
    
    //_connThreadPool.close();

    Alarm suspendAlarm = _suspendAlarm;
    _suspendAlarm = null;

    if (suspendAlarm != null)
      suspendAlarm.dequeue();

    ServerSocketBar serverSocket = _serverSocket;
    _serverSocket = null;

    InetAddress localAddress = null;
    int localPort = 0;
    if (serverSocket != null) {
      localAddress = serverSocket.getLocalAddress();
      localPort = serverSocket.getLocalPort();
    }

    // close the server socket
    if (serverSocket != null) {
      try {
        serverSocket.close();
      } catch (Throwable e) {
      }

      try {
        synchronized (serverSocket) {
          serverSocket.notifyAll();
        }
      } catch (Throwable e) {
      }
    }

    /*
    if (selectManager != null) {
      try {
        selectManager.onPortClose(this);
      } catch (Throwable e) {
      }
    }
    */

    Set<ConnectionTcp> activeSet;

    synchronized (_activeConnectionSet) {
      activeSet = new HashSet<ConnectionTcp>(_activeConnectionSet.keySet());
    }

    for (ConnectionTcp conn : activeSet) {
      try {
        conn.proxy().requestDestroy();
      }
      catch (Exception e) {
        log.log(Level.FINEST, e.toString(), e);
      }
    }

    // wake the start thread
    //_connThreadPool.wake();

    // Close the socket server socket and send some request to make
    // sure the Port accept thread is woken and dies.
    // The ping is before the server socket closes to avoid
    // confusing the threads

    // ping the accept port to wake the listening threads
    if (localPort > 0) {
      int idleCount = 0;//getIdleThreadCount() + getStartThreadCount();

      for (int i = 0; i < idleCount + 10; i++) {
        InetSocketAddress addr;

        /*
        if (getIdleThreadCount() == 0)
          break;
          */

        if (localAddress == null ||
            localAddress.getHostAddress().startsWith("0.")) {
          addr = new InetSocketAddress("127.0.0.1", localPort);
          connectAndClose(addr);

          addr = new InetSocketAddress("[::1]", localPort);
          connectAndClose(addr);
        }
        else {
          addr = new InetSocketAddress(localAddress, localPort);
          connectAndClose(addr);
        }

        try {
          Thread.sleep(10);
        } catch (Exception e) {
        }
      }
    }

    ConnectionTcp conn;
    while ((conn = _idleConn.allocate()) != null) {
      conn.requestDestroy();
    }

    // cloud/0550
    /*
    // clearning the select manager must be after the conn.requestDestroy
    AbstractSelectManager selectManager = _selectManager;
    _selectManager = null;
    */

    log.finest(this + " closed");
  }

  private void connectAndClose(InetSocketAddress addr)
  {
    try {
      SocketSystem socketSystem = SocketSystem.current();

      try (SocketBar s = socketSystem.connect(addr, 100)) {
      }
    } catch (ConnectException e) {
    } catch (Throwable e) {
      log.log(Level.FINEST, e.toString(), e);
    }
  }
  
  @Override
  public int hashCode()
  {
    return toString().hashCode();
  }
  
  @Override
  public boolean equals(Object o)
  {
    if (! (o instanceof PortTcp)) {
      return false;
    }
    
    return toString().equals(o.toString());
  }

  @Override
  public String toString()
  {
    if (_url != null)
      return getClass().getSimpleName() + "[" + _url + "]";
    else
      return getClass().getSimpleName() + "[" + address() + ":" + port() + "]";
  }

  private class SuspendReaper implements AlarmListener
  {
    private ArrayList<ConnectionTcp> _suspendSet
      = new ArrayList<>();

    private ArrayList<ConnectionTcp> _timeoutSet
      = new ArrayList<>();

    private ArrayList<ConnectionTcp> _completeSet
      = new ArrayList<>();

    @Override
    public void handleAlarm(Alarm alarm)
    {
      try {
        _suspendSet.clear();
        _timeoutSet.clear();
        _completeSet.clear();

        long now = CurrentTime.currentTime();

        // wake the launcher in case of freeze
        //_connThreadPool.wake();

        _suspendSet.addAll(_activeConnectionSet.keySet());
        
        for (int i = _suspendSet.size() - 1; i >= 0; i--) {
          ConnectionTcp conn = _suspendSet.get(i);

          if (! conn.state().isTimeoutCapable()) {
            continue;
          }
          
          if (conn.idleExpireTime() < now) {
            _timeoutSet.add(conn);
            continue;
          }
        }

        for (int i = _timeoutSet.size() - 1; i >= 0; i--) {
          ConnectionTcp conn = _timeoutSet.get(i);

          conn.requestTimeout();
        }
      } catch (Throwable e) {
        e.printStackTrace();
      } finally {
        if (! isClosed()) {
          alarm.runAfter(_suspendReaperTimeout);
        }
      }
    }
  }
}
