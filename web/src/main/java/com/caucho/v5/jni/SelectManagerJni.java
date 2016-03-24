/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.jni;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.health.meter.ActiveMeter;
import com.caucho.v5.health.meter.MeterService;
import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.health.shutdown.Shutdown;
import com.caucho.v5.jni.JniUtil.JniLoad;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.network.port.PollController;
import com.caucho.v5.network.port.PollTcpManagerBase;
import com.caucho.v5.network.port.PortSocket;
import com.caucho.v5.util.CurrentTime;

/**
 * Manages non-blocking socket connections.
 */
public class SelectManagerJni extends PollTcpManagerBase
{
  private static final Logger log
    = Logger.getLogger(SelectManagerJni.class.getName());

  private static long FLAG_HUP = 1L << 32;
  private static long FLAG_READ = 1L << 33;

  private static final JniTroubleshoot _jniTroubleshoot;

  private static final AtomicReference<SelectManagerJni> _jniSelectManager
    = new AtomicReference<SelectManagerJni>();

  private static final ActiveMeter _keepaliveAsyncMeter
    = MeterService.createActiveMeter("Caucho|Port|Keepalive Async");

  private static int _gId;

  private static boolean _isEnabled = true;

  private long _maxSelectTime = 60000L;
  private int _selectMax;

  private long _checkInterval = 15000L;

  // network/0284 depends on this value. If it's changed, that QA
  // needs to be changed to match.
  private long _timeoutReapInterval;

  private Thread _thread;

  // private final long []_fdResults = new long[256];
  private final long []_fdResults = new long[1024];
  private final AtomicReferenceArray<PollController> _connections;
  private final AtomicInteger _maxConnection = new AtomicInteger();

  private final AtomicInteger _connectionCount = new AtomicInteger();

  private final AtomicInteger _activeCount = new AtomicInteger();

  private long _fd;

  private final Lifecycle _lifecycle = new Lifecycle();

  private final AtomicInteger _selectTotal = new AtomicInteger();

  private SelectManagerJni()
  {
    int fdMax = 0;

    try {
      fdMax = JniProcess.getFdMax();
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);
      _isEnabled = false;
    }

    if (fdMax > 256) {
      _selectMax = fdMax - 256;
    }
    else if (fdMax > 0) {
      _selectMax = fdMax - 16;
    }

    if (_selectMax <= 0) {
      _isEnabled = false;
      fdMax = 0;
    }

    _connections = new AtomicReferenceArray<>(fdMax);
    
    if (CurrentTime.isTest()) {
      _timeoutReapInterval = 500L;
    }
    else {
      _timeoutReapInterval = 5000L;
    }
  }

  /**
   * Returns a jni select manager.
   */
  public static SelectManagerJni create()
  {
    try {
      if (! isEnabled()) {
        return null;
      }

      SelectManagerJni pollManager = _jniSelectManager.get();
      
      if (pollManager == null) {
        pollManager = new SelectManagerJni();

        // The start is responsible for checking if the select manager
        // is enabled on this system.
        if (pollManager.start()) {
          _jniSelectManager.set(pollManager);
        }
      }

      return _jniSelectManager.get();
    } catch (ConfigException e) {
      log.finer(e.toString());
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);
    }

    return null;
  }

  public static boolean isEnabled()
  {
    return (JniSocketImpl.isEnabled()
            && _jniTroubleshoot.isEnabled()
            && _isEnabled);
  }

  public static String getInitMessage()
  {
    if (! _jniTroubleshoot.isEnabled())
      return _jniTroubleshoot.getMessage();
    else
      return null;
  }

  /**
   * Returns the available keepalive.
   */
  @Override
  public int getFreeKeepalive()
  {
    return _selectMax - _connectionCount.get();
  }

  /**
   * Returns the keepalive count.
   */
  @Override
  public int getSelectCount()
  {
    return _connectionCount.get();
  }

  /**
   * Sets the max select.
   */
  @Override
  public void setSelectMax(int max)
  {
    _selectMax = max;
  }

  /**
   * Sets the max select.
   */
  @Override
  public int pollMax()
  {
    return _selectMax;
  }

  /**
   * Sets the select timeout
   */
  @Override
  public void setSelectTimeout(long timeout)
  {
    _maxSelectTime = timeout;
  }

  /**
   * Returns the check interface.
   */
  public long getCheckInterval()
  {
    return _checkInterval;
  }

  /**
   * Sets the check interval.
   */
  public void setCheckInterval(long checkInterval)
  {
    _checkInterval = checkInterval;
  }
  
  public boolean isActive()
  {
    return _lifecycle.isActive();
  }

  /**
   * Starts the manager.
   */
  @Override
  public boolean start()
  {
    if (! _lifecycle.toStarting()) {
      return false;
    }

    _fd = createNative();

    if (_fd == 0) {
      _lifecycle.toDestroy();
      log.finer(this + " is not available on this system.");
      return false;
    }

    String name = "resin-select-manager-" + _gId++;
    _thread = new Thread(new SelectTask(), name);
    _thread.setDaemon(true);
    _thread.setPriority(Thread.MAX_PRIORITY);

    _thread.start();

    _lifecycle.waitForActive(2000);

    if (log.isLoggable(Level.FINER))
      log.finer(this + " active");

    log.fine("Async/poll keepalive enabled with max sockets = "
             + _selectMax);

    name = "resin-select-manager-timeout-" + _gId++;
    Thread timeoutThread = new Thread(new TimeoutTask(), name);
    timeoutThread.setDaemon(true);
    timeoutThread.start();

    return true;
  }

  /**
   * Adds a keepalive connection.
   *
   * @param conn the connection to register as keepalive
   *
   * @return true if the connection changes to the keepalive state
   */
  @Override
  public PollResult startPoll(PollController conn)
  {
    try {
      boolean isNew = conn.enableKeepaliveIfNew(this);

      if (! enableKeepalive(conn, isNew)) {
        return PollResult.CLOSED;
      }

      if (conn.toKeepaliveStart()) {
        _activeCount.incrementAndGet();

        return PollResult.START;
      }
      else {
        return PollResult.DATA;
      }
    } catch (IOException e) {
      conn.onPollReadClose();

      throw new IllegalStateException(e);
    }
  }

  /**
   * Enables keepalive and checks to see if data is available.
   *
   * @return true if a read would block (keepalive is required)
   */
  private boolean enableKeepalive(PollController conn,
                                  boolean isNew)
      throws IOException
  {
    
    if (_selectMax <= _connectionCount.get()) {
      throw new IllegalStateException(this + " keepalive overflow "
                                      + _connectionCount + " max=" + _selectMax);

      /*
      conn.requestDestroy();

      return false;
      */
    }

    JniSocketImpl socket = (JniSocketImpl) conn.getSocket();

    if (socket == null) {
      throw new IllegalStateException(this + " socket empty for " + conn);
    }

    int nativeFd = socket.getNativeFd();

    if (nativeFd < 0) {
      throw new IllegalStateException(this + " attempted keepalive with closed file descriptor fd=" + nativeFd
                                      + "\n " + socket
                                      + "\n " + conn);
    }
    else if (_connections.length() <= nativeFd) {
      throw new IllegalStateException(this + " select overflow for file descriptor fd=" + nativeFd + " " + conn);
    }

    if (! _lifecycle.isActive()) {
      throw new IllegalStateException("inactive keepalive");
    }

    if (isNew) {
      setMaxConnection(nativeFd);

      _connections.set(nativeFd, conn);

      _connectionCount.incrementAndGet();
    }

    int result = addNative(_fd, nativeFd, isNew);

    // result < 0 would likely be a disconnect

    return result == 0;
  }

  @Override
  public void closePoll(PollController conn)
  {
    removeConnection(conn);
  }

  /**
   * Running process accepting connections.
   */
  private void runSelectTask()
  {
    if (_lifecycle.isActive() || _lifecycle.isAfterStopping()) {
      log.warning(this + " cannot start because an instance is active");
      return;
    }

    initNative(_fd);

    synchronized (_thread) {
      _thread.notify();
    }

    if (! _lifecycle.toActive()) {
      log.warning(this + " invalid starting state");
      return;
    }

    runImpl();
  }

  private void runImpl()
  {
    log.finer(this + " active");

    int interruptCount = 0;
    int exceptionCount = 0;

    long []fdResults = _fdResults;

    while (_lifecycle.isActive()) {
      try {
        long selectWaitTime = 5000L;

        int select = selectNative(_fd, selectWaitTime, fdResults);

        _selectTotal.addAndGet(select);

        for (int i = 0; i < select; i++) {
          wakeConnection(fdResults[i]);
        }

        interruptCount = 0;
        exceptionCount = 0;
      } catch (InterruptedIOException e) {
        log.log(Level.FINER, e.toString(), e);

        Thread.interrupted();

        // If there's some sort of terminal exception, throw it
        if (interruptCount++ > 100) {
          String msg = "closing because too many JniSelectManager exceptions\n  " + e;

          log.log(Level.SEVERE, e.toString(), e);

          Shutdown.shutdownActive(ExitCode.NETWORK, msg);
          break;
        }
      } catch (Throwable e) {
        log.log(Level.FINER, e.toString(), e);

        // If there's some sort of terminal exception, throw it
        if (exceptionCount++ > 100) {
          String msg = "closing because too many JniSelectManager exceptions\n  " + e;

          log.log(Level.SEVERE, e.toString(), e);

          Shutdown.shutdownActive(ExitCode.NETWORK, msg);
          break;
        }
      }
    }

    _thread = null;

    stop();

    log.finer(this + " stopped");
  }

  private void wakeConnection(long select)
  {
    int fd = (int) (select & 0x7fffffffL);
    boolean isHup = (select & FLAG_HUP) != 0;
    boolean isRead = (select & FLAG_READ) != 0;

    PollController conn = _connections.get(fd);

    if (conn != null) {
      if (isHup) {
	  //        conn.onKeepaliveDisconnect();
	conn.onPollRead();
      }
      else if (isRead) {
        conn.onPollRead();
      }
    }
  }

  /**
   * Running process accepting connections.
   */
  private void runTimeoutTask()
  {
    while (isActive()) {
      reapTimeouts();

      try {
        Thread.sleep(_timeoutReapInterval);
      } catch (Exception e) {
      }
    }
  }

  private void reapTimeouts()
  {
    long lastCheckTime = 0;
    long maxSelectTime = _maxSelectTime;
    long checkInterval = getCheckInterval();

    long now = CurrentTime.currentTime();

    // network/0284
    if (now < lastCheckTime) {
      lastCheckTime = 0;
    }

    if (lastCheckTime + checkInterval < now) {
      lastCheckTime = now;

      // adaptively set the keepalive time to keep free connections
      if (3 * _selectMax < _connectionCount.get() * 4) {
        maxSelectTime /= 2;

        if (maxSelectTime <= 0) {
          maxSelectTime = 1000;
        }
      }
      else if (maxSelectTime < _maxSelectTime) {
        maxSelectTime += 100;

        maxSelectTime = Math.min(maxSelectTime, _maxSelectTime);
      }

      int maxConnection = _maxConnection.get();

      for (int i = maxConnection; i >= 0; i--) {
        PollController conn;
        boolean isFree = false;

        conn = _connections.get(i);

        if (conn == null) {
          continue;
        }

        if (isFree) {
          _maxConnection.compareAndSet(maxConnection, i);
        }

        isFree = false;

        // connection has timed out
        if (conn.getIdleExpireTime() < now) {
          if (log.isLoggable(Level.FINER)) {
            log.finer(this + " connection keepalive timeout time="
                      + (now - conn.getIdleStartTime()) + " conn=" + conn);
          }

          conn.onKeepaliveTimeout();
        }
      }
    }

  }

  @Override
  public void onPortClose(PortSocket port)
  {
    wakeConnections(port);
  }

  /**
   * Closing the manager.
   */
  @Override
  public boolean stop()
  {
    if (! _lifecycle.toStopping())
      return false;

    log.finest(this + " stopping");

    closeConnections();

    destroy();

    return true;
  }

  private void wakeConnections(PortSocket port)
  {
    int length = _connections.length();
    for (int i = 0; i < length; i++) {
      PollController conn = _connections.get(i);

      if (conn != null && conn.getPort() == port) {
        conn.onPollReadClose();
      }
    }
  }

  private void closeConnections()
  {
    int length = _connections.length();

    for (int i = 0; i < length; i++) {
      PollController conn = _connections.getAndSet(i, null);

      if (conn != null) {
        _connectionCount.decrementAndGet();

        try {
          conn.onPollReadClose();
        } catch (Throwable e) {
          log.log(Level.WARNING, e.toString(), e);
        }
      }
    }
  }

  private boolean removeConnection(PollController conn)
  {
    if (conn == null) {
      return false;
    }

    JniSocketImpl socket = (JniSocketImpl) conn.getSocket();
    int nativeFd = socket.getNativeFd();
    int index = nativeFd;

    if (index < 0) {
      int size = _connections.length();

      for (int i = 0; i < size; i++) {
        if (_connections.compareAndSet(i, conn, null)) {
          _connectionCount.decrementAndGet();
          index = i;
          break;
        }
      }
    }
    else if (_connections.compareAndSet(index, conn, null)) {
      _connectionCount.decrementAndGet();
    }
    else {
      return false;
    }

    if (index >= 0) {
      remove(conn);

      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Removes the connection from the selection.
   */
  private void remove(PollController conn)
  {
    if (conn == null) {
      return;
    }

    JniSocketImpl socket = (JniSocketImpl) conn.getSocket();
    int nativeFd = socket.getNativeFd();

    if (_lifecycle.isDestroyed()) {
      return;
    }

    _activeCount.incrementAndGet();

    if (nativeFd > 0) {
      try {
        removeNative(_fd, nativeFd);
      } catch (Throwable e) {
        log.log(Level.WARNING, e.toString(), e);
        _lifecycle.toError();
      }
    }
    
    conn.toKeepaliveClose();

    if (_activeCount.decrementAndGet() == 0 && _lifecycle.isDestroyed()) {
      destroy();
    }
  }

  private void setMaxConnection(int newMax)
  {
    int oldMax;

    do {
      oldMax = _maxConnection.get();
    } while (oldMax < newMax && ! _maxConnection.compareAndSet(oldMax, newMax));
  }

  private void destroy()
  {
    // XXX: 4.0.2 - want to reuse after regressions are cleaned in 4.0.3.

    _jniSelectManager.compareAndSet(this, null);

    _lifecycle.toDestroy();

    long fd = 0;
    synchronized (this) {
      if (_activeCount.get() > 0)
        return;

      fd = _fd;
      _fd = 0;
    }

    if (fd != 0) {
      closeNative(fd);

      freeNative(fd);
    }
  }

  @Override
  protected void finalize()
  {
    close();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[max=" + _selectMax + "]";
  }

  private class SelectTask implements Runnable {
    public void run()
    {
      runSelectTask();
    }
  }

  private class TimeoutTask implements Runnable {
    @Override
    public void run()
    {
      runTimeoutTask();
    }
  }

  public native long createNative();
  public native void initNative(long fd);
  public native int addNative(long manager, int fd, boolean isFirst)
    throws IOException;
  public native int removeNative(long manager, int fd) throws IOException;

  public native int selectNative(long manager,
                                 long timeout,
                                 long []resultFds)
    throws IOException;

  public native int closeNative(long manager);
  public native int freeNative(long manager);

  static {
    _jniTroubleshoot
    = JniUtil.load(SelectManagerJni.class,
                   new JniLoad() { 
                     public void load(String path) { System.load(path); }},
                   "baratine");
  }
}
