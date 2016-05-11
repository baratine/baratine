/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Baratine is software; you can redistribute it and/or modify
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
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.health.shutdown.Shutdown;
import com.caucho.v5.io.ClientDisconnectException;
import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.SocketBar;
import com.caucho.v5.io.WriteStream;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.ModulePrivate;

import io.baratine.service.ServiceRef;

/**
 * A protocol-independent TcpConnection.  TcpConnection controls the
 * TCP Socket and provides buffered streams.
 *
 * <p>Each TcpConnection has its own thread.
 */
@ModulePrivate
public class ConnectionTcp implements ConnectionTcpApi, ConnectionTcpProxy
{
  private static final Logger log
    = Logger.getLogger(ConnectionTcp.class.getName());

  private final long _connectionId;  // The connection's id
  private final String _id;
  private final String _name;
  private String _dbgId;

  private final PortTcp _port;
  private final SocketBar _socket;
  private final ConnectionProtocol _protocol;

  private ReadStream _readStream;
  private WriteStream _writeStream;

  //private final PollControllerTcpPoll _pollHandle;
  private final PollController _pollHandle;
  private final ClassLoader _loader;

  private final ConnectionTcpProxy _connProxy;

  private StateConnection _state = StateConnection.FREE;

  private long _idleTimeout;

  private long _connectionSequence;

  private long _connectionStartTime;

  private long _idleStartTime;
  private long _idleExpireTime;

  private ServiceRef _inRef;


  /**
   * Creates a new TcpConnection.
   *
   * @param server The TCP server controlling the connections
   * @param request The protocol Request
   */
  ConnectionTcp(long connId,
                PortTcp port,
                SocketBar socket)
  {
    _socket = socket;

    _writeStream = new WriteStream();
    _writeStream.reuseBuffer(true);

    _readStream = new ReadStream();
    _readStream.reuseBuffer(true);

    _connectionId = connId;

    _port = port;

    _loader = port.classLoader();

    Protocol protocol = port.protocol();

    _protocol = protocol.newConnection(this);

    // _id = listener.getDebugId() + "-" + _idCount;
    _id = protocol.name() + "-" + _port.port() + "-" + _connectionId;

    _inRef = port.services().newService(this).name(_id).ref();
    _connProxy = _inRef.as(ConnectionTcpProxy.class);

    _name = _id;

    //_connectionTask = new TaskConnection(this);
    _pollHandle = port.createPollHandle(this); // new PollControllerTcpPoll(this);
  }

  /**
   * The connection's buffered read stream.  If the ReadStream
   * needs to block, it will automatically flush the corresponding
   * WriteStream.
   */
  @Override
  public final ReadStream readStream()
  {
    return _readStream;
  }

  /**
   * The connection's buffered write stream.  If the ReadStream
   * needs to block, it will automatically flush the corresponding
   * WriteStream.
   */
  @Override
  public final WriteStream writeStream()
  {
    return _writeStream;
  }

  /**
   * Returns the connection id.  Primarily for debugging.
   */
  @Override
  public long id()
  {
    return _connectionId;
  }

  public String getDebugId()
  {
    return _id;
  }

  /**
   * Returns the connection sequence.
   */
  public long sequence()
  {
    return _connectionSequence;
  }

  /**
   * Returns the object name for jmx.
   */
  public String getName()
  {
    return _name;
  }

  public String getThreadName()
  {
    return dbgId();
  }

  /**
   * Returns the port which generated the connection.
   */
  @Override
  public PortTcp port()
  {
    return _port;
  }

  /**
   * Returns the request for the connection.
   */
  public final ConnectionProtocol connProtocol()
  {
    return _protocol;
  }

  public String getRequestUrl()
  {
    ConnectionProtocol request = connProtocol();

    String url = request.url();

    if (url != null && ! "".equals(url)) {
      return url;
    }

    PortTcp port = port();

    String protocolName = port.protocolName();

    if (protocolName == null) {
      protocolName = "request";
    }

    if (port.address() == null) {
      return protocolName + "://*:" + port.port();
    }
    else {
      return protocolName + "://" + port.address() + ":" + port.port();
    }
  }


  //
  // timeout properties
  //

  /**
   * Sets the idle time for a keepalive connection.
   */
  @Override
  public void setIdleTimeout(long idleTimeout)
  {
    _idleTimeout = idleTimeout;
  }

  /**
   * The idle time for a keepalive connection
   */
  @Override
  public long getIdleTimeout()
  {
    return _idleTimeout;
  }

  //
  // port information
  //

  @Override
  public boolean isPortActive()
  {
    return _port.isActive();
  }

  //
  // state information
  //

  /**
   * Returns the state.
   */
  @Override
  public StateConnection state()
  {
    return _state;
  }

  public final boolean isIdle()
  {
    return _state.isIdle();
  }

  /**
   * Returns true for active.
   */
  public boolean isActive()
  {
    return _state.isActive();
  }

  /**
   * Returns true for closed.
   */
  public boolean isClosed()
  {
    return _state.isClose();
  }

  public final boolean isDestroyed()
  {
    return _state.isDestroy();
  }

  //
  // port/socket information
  //

  /**
   * Returns the connection's socket
   */
  @Override
  public SocketBar socket()
  {
    return _socket;
  }

  @Override
  public InetSocketAddress ipLocal()
  {
    return _socket.ipLocal();
  }

  @Override
  public InetSocketAddress ipRemote()
  {
    return _socket.ipRemote();
  }

  /**
   * Returns the socket's local TCP port.
   */
  @Override
  public int portLocal()
  {
    return _socket.portLocal();
  }

  /**
   * Returns the socket's remote host name.
   */
  @Override
  public String addressRemote()
  {
    return _socket.getRemoteHost();
  }

  /**
   * Adds from the socket's remote address.
   */
  @Override
  public int addressRemote(byte []buffer, int offset, int length)
  {
    return _socket.getRemoteAddress(buffer, offset, length);
  }

  /**
   * Returns the socket's remote port
   */
  @Override
  public int portRemote()
  {
    return _socket.portRemote();
  }

  /**
   * Returns true if the connection is secure, i.e. a SSL connection
   */
  @Override
  public boolean isSecure()
  {
    return _socket.isSecure() || _port.isSecure();
  }

  //
  // SSL api
  //

  /**
   * Returns the cipher suite
   */
  @Override
  public String secureProtocol()
  {
    return _socket.secureProtocol();
  }

  /**
   * Returns the cipher suite
   */
  @Override
  public String cipherSuite()
  {
    return _socket.cipherSuite();
  }

  /**
   * Returns any client certificates.
   * @throws CertificateException
   */
  @Override
  public X509Certificate []clientCertificates()
    throws CertificateException
  {
    return _socket.getClientCertificates();
  }


  //
  // connection information
  //

  /**
   * Returns the time the connection started
   */
  public final long getConnectionStartTime()
  {
    return _connectionStartTime;
  }

  //
  // request information
  //

  /**
   * Returns the idle expire time (keepalive or suspend).
   */
  @Override
  public long idleExpireTime()
  {
    return _idleExpireTime;
  }

  /**
   * Returns the idle start time (keepalive or suspend)
   */
  @Override
  public long idleStartTime()
  {
    return _idleStartTime;
  }

  //
  // statistics state
  //

  /**
   * Returns the user statistics state
   */
  public String displayState()
  {
    return _state.toString();
  }

  @Override
  public ConnectionTcpProxy proxy()
  {
    return _connProxy;
  }

  //
  // transition requests from external threads (thread-safe)
  //

  /**
   * Wake a connection from a select/poll keepalive.
   */
  @Override
  public void requestPollRead()
  {
    /*
    if (log.isLoggable(Level.FINEST)) {
      log.finest("request-wake " + getName()
                 + " (count=" + _port.getThreadCount()
                 + ", idle=" + _port.getIdleThreadCount() + ")");
    }
    */

    try {
      requestLoop();
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }

    /*
    if (_stateRef.get().toWake(_stateRef)) {
      offer(getConnectionTask());
    }
    */
  }

  /**
   * Wake a connection from a select/poll keepalive.
   */
  @Override
  public void requestTimeout()
  {
    System.out.println("REQ_TO:" + this);
    requestCloseRead();
  }

  /**
   * Wake a connection from a select/poll close.
   */
  @Override
  public void requestCloseRead()
  {
    _state = _state.toCloseRead();

    try {
      requestLoop();
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  /**
   * Wake a connection.
   *
   * The connection may be idle because it's received a close-read but
   * HTTP is still processing. When the HTTP write completes, it will
   * wake the read thread.
   */
  @Override
  public void requestWake()
  {
    try {
      _state = _state.toWake();

      requestLoop();
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }

    /*
    if (_stateRef.get().toWake(_stateRef)) {
      offer(getConnectionTask());
    }
    */
  }

  /**
   * Destroys the connection()
   */
  @Override
  public final void requestDestroy()
  {
    destroy();
  }

  //
  // request handling
  //

  @Override
  public void requestAccept()
  {
    try {
      _state = _state.toAccepted();

      initSocket();
      _pollHandle.initKeepalive();

      if (log.isLoggable(Level.FINE)) {
        _dbgId = (ipLocal()
                  + "<" + ipRemote()
                  + "#" + _connectionId);
      }

      _protocol.onAccept();

      requestLoop();
    } catch (Exception e) {
      e.printStackTrace();

      throw new RuntimeException(e);
    }
  }

  /**
   * Handles a new connection/socket from the client.
   */
  private StateConnection requestLoop()
    throws IOException
  {
    StateConnection state = _state;
    StateConnection tailState = state;
    //Thread thread = Thread.currentThread();

    //startThread(thread);

    try {
      while (! _port.isClosed()) {
        switch (state) {
        case IDLE:
          tailState = state;
          return tailState;

        case ACTIVE:
          ServiceRef.flushOutbox();
          state = dispatchRequest();
          break;

        case READ:
          ServiceRef.flushOutbox();
          state = processPoll();
          break;

        case POLL:
          System.out.println("POLL: " + this + " " + port().pollManager());
          ServiceRef.flushOutbox();
          tailState = state;
          return tailState;

        case CLOSE_READ_S:
          ServiceRef.flushOutboxAndExecuteLast();
          tailState = state;
          return tailState;

        case CLOSE_READ_A:
          state = closeRead();
          break;

        case CLOSE:
          _state = state;
          tailState = state;
          ServiceRef.flushOutboxAndExecuteLast();
          close();
          tailState = _state;
          return tailState;

        case FREE:
          tailState = state;
          ServiceRef.flushOutboxAndExecuteLast();
          System.out.println("FREE: " + this);
          return tailState;

          /*
        case TIMEOUT:
          ServiceRef.flushOutbox();
          //state = timeoutRequest();
          return null;
          break;
          */

          /*
        case DESTROY:
          ServiceRef.flushOutbox();
          destroy();
          tailState = state;
          return tailState;
          */

        default:
          System.out.println("UNKNOWN-STATE: " + state + " " + this);
          _state = tailState = StateConnection.DESTROY;
          ServiceRef.flushOutbox();
          destroy();
          throw new IllegalStateException(String.valueOf(state));
        }
      }
    } catch (ClientDisconnectException e) {
    //  state = NextState.DESTROY;

      _port.stats().addLifetimeClientDisconnectCount();

      if (log.isLoggable(Level.FINER)) {
        log.finer(dbgId() + e);
      }
    } catch (InterruptedIOException e) {
      if (log.isLoggable(Level.FINEST)) {
        log.log(Level.FINEST, dbgId() + e, e);
      }
    } catch (IOException e) {
      if (log.isLoggable(Level.FINE)) {
        log.log(Level.FINE, dbgId() + e, e);
      }
    } catch (OutOfMemoryError e) {
      String msg = "TcpSocketLink OutOfMemory";

      Shutdown.shutdownOutOfMemory(msg);
    } catch (Throwable e) {
      if (log.isLoggable(Level.FINE)) {
        log.log(Level.FINE, dbgId() + e, e);
      }
    } finally {
      _state = tailState;
      //thread.setContextClassLoader(_loader);

      //finishThread(tailState);

      //ServiceRef.flushOutboxAndExecuteLast();
    }

    return state;
  }

  private StateConnection dispatchRequest()
    throws IOException
  {
    Thread thread = Thread.currentThread();

    try {
      thread.setContextClassLoader(_loader);

      long requestTimeout = port().getRequestTimeout();

      long now = CurrentTime.currentTime();

      if (requestTimeout > 0) {
        long expireTime = now + requestTimeout;
        _socket.setRequestExpireTime(expireTime);

        _idleExpireTime = expireTime;
      }
      else {
        _idleExpireTime = now + 600 * 1000L;
      }

      StateConnection stateNext = connProtocol().service();

      _state = _state.next(stateNext);

      return _state;
    }
    finally {
      thread.setContextClassLoader(_loader);

      _socket.setRequestExpireTime(0);
    }
  }

  private StateConnection closeRead()
    throws IOException
  {
    // Thread thread = Thread.currentThread();

    try {
      StateConnection stateNext = connProtocol().onCloseRead();

      StateConnection oldState = _state;
      _state = _state.next(stateNext);

      return _state;
    }
    catch (Exception e) {
      e.printStackTrace();
      throw e;
    }
    finally {
      _socket.setRequestExpireTime(0);
    }
  }

  //
  // keepalives: read blocking and polling
  //

  /**
   * Starts a keepalive, either returning available data or
   * returning false to close the loop
   *
   * If keepaliveRead() returns true, data is available.
   * If it returns false, either the connection is closed,
   * or the connection has been registered with the select.
   */
  private StateConnection processPoll()
    throws IOException
  {
    PortTcp port = _port;

    if (port.isClosed()) {
      return StateConnection.DESTROY;
    }

    if (readStream().available() > 0) {
      return StateConnection.ACTIVE;
    }

    long timeout = _idleTimeout;

    _idleStartTime = CurrentTime.currentTime();
    _idleExpireTime = _idleStartTime + timeout;

    // _state = _state.toKeepalive(this);

    PollTcpManagerBase pollManager = port.pollManager();

    // use poll manager if available
    if (pollManager == null) {
      port().stats().addLifetimeKeepaliveCount();

      return threadPoll(timeout);
    }

    if (! _pollHandle.isKeepaliveStarted()) {
      ServiceRef.flushOutbox();

      if (_port.keepaliveThreadRead(readStream(), _idleTimeout) > 0) {
        return StateConnection.ACTIVE;
      }
      else if (_idleExpireTime <= CurrentTime.currentTime()) {
        return StateConnection.TIMEOUT;
      }
    }

    /*
    if (! _state.toPollRequested(_stateRef)) {
      return _stateRef.get().getNextState();
    }
      // _state = _state.toKeepaliveSelect();
       * */


      // keepalive to select manager succeeds
    switch (pollManager.startPoll(_pollHandle)) {
    case START: {
      if (log.isLoggable(Level.FINEST)) {
        log.finest(dbgId() + "keepalive (poll)");
      }

      port().stats().addLifetimeKeepaliveCount();
      port().stats().addLifetimeKeepalivePollCount();

      return StateConnection.POLL;
    }

    case DATA: {
      if (log.isLoggable(Level.FINEST)) {
        log.finest("keepalive data available (poll) [" + dbgId() + "]");
      }

      //_state = _state.toWake();
      //System.out.println("DATA: " + _state);
      /*
        if (_stateRef.get().toPollSleep(_stateRef)) {
          throw new IllegalStateException();
        }
       */

      return StateConnection.ACTIVE;
    }

    case CLOSED: {
      if (log.isLoggable(Level.FINEST)) {
        log.finest(dbgId() + " keepalive close (poll)");
      }

      //_state = _state.toWake();

      /*
        if (_stateRef.get().toPollSleep(_stateRef)) {
          throw new IllegalStateException();
        }
       */

      return StateConnection.CLOSE_READ_A;
    }

    default:
      throw new IllegalStateException();
    }
  }

  private StateConnection threadPoll(long timeout)
  {
    // long timeout = getPort().getKeepaliveTimeout();
    long expires = timeout + CurrentTime.getCurrentTimeActual();

    if (log.isLoggable(Level.FINEST)) {
      log.finest(dbgId() + " keepalive (thread, " + timeout + "ms)");
    }

    ServiceRef.flushOutbox();

    do {
      try {
        long delta = expires - CurrentTime.getCurrentTimeActual();

        if (delta < 0) {
          delta = 0;
        }

        long result = readStream().fillWithTimeout(delta);

        if (result > 0) {
          return StateConnection.ACTIVE;
        }
        else if (result < 0) {
          return StateConnection.CLOSE_READ_A;
        }
      } catch (SocketTimeoutException e) {
        log.log(Level.FINEST, e.toString(), e);
      } catch (IOException e) {
        log.log(Level.FINEST, e.toString(), e);
        break;
      }
    } while (CurrentTime.getCurrentTimeActual() < expires);

    // close();
    // killKeepalive("thread-keepalive timeout (" + timeout + "ms)");

    return StateConnection.CLOSE;
  }

  //
  // Callbacks from the request processing tasks
  //

  @Override
  public int fillWithTimeout(long timeout) throws IOException
  {
    return readStream().fillWithTimeout(timeout);
  }

  //
  // state transitions
  //

  /**
   * Initialize the socket for a new connection
   */
  private void initSocket()
    throws IOException
  {
    _idleTimeout = _port.getKeepaliveTimeout();

    _port.ssl(_socket);

    writeStream().init(_socket.stream());

    // ReadStream cannot use getWriteStream or auto-flush
    // because of duplex mode
    // ReadStream is = getReadStream();
    _readStream.init(_socket.stream());

    if (log.isLoggable(Level.FINEST)) {
      log.finest(dbgId() + "starting connection " + this
                 + ", total=" + _port.getConnectionCount());
    }
  }

  @Override
  public void clientDisconnect()
  {
    // killKeepalive("client disconnect");

    /*
    AsyncControllerTcp async = _async;

    if (async != null) {
      async.complete();
    }
    */
  }

  //
  // close operations
  //

  /**
   * Closes the connection.
   */
  private void closeConnection()
  {
    disconnect();


    if (log.isLoggable(Level.FINE)) {
      _dbgId = (port().address() + ":" + port().port() + "-" + _connectionId);
      Thread.currentThread().setName(_dbgId);
    }
  }

  @Override
  public void disconnect()
  {
    if (_state.isClose()) {
      return;
    }

    _state = _state.toClose();

    // synchronized because shutdown can call disconnect on multiple threads
    synchronized (this) {
      _protocol.onCloseRead();

      // XXX: ka state
      _pollHandle.destroy();

      try {
        writeStream().close();
      } catch (Throwable e) {
        log.log(Level.FINER, e.toString(), e);
      }

      try {
        ReadStream readStream = readStream();

        if (readStream != null) {
          readStream.close();
        }
      } catch (Throwable e) {
        log.log(Level.FINER, e.toString(), e);
      }

      PortTcp port = port();

      SocketBar socket = _socket;

      if (port != null) {
        port.closeSocket(socket);
      }

      try {
        socket.close();
      } catch (Throwable e) {
        log.log(Level.FINER, e.toString(), e);
      }

      try {
        connProtocol().onClose();
      } catch (Throwable e) {
        log.warning(e.toString());

        if (log.isLoggable(Level.FINER)) {
          log.log(Level.FINER, e.toString(), e);
        }
      }

      if (log.isLoggable(Level.FINER)) {
        if (port != null)
          log.finer("closing connection " + dbgId()
                    + ", total=" + port.getConnectionCount());
        else
          log.finer("closing connection " + id());
      }
    }
  }

  /**
   * Called after close_read
   */
  //@Override
  private void close()
  {
    if (! _state.isClose()) {
      System.out.println("Expected close at: " + _state);;
      Thread.dumpStack();
      throw new IllegalStateException(_state.toString());
    }

    //_state = _state.toFree();

    // synchronized because shutdown can call disconnect on multiple threads
    synchronized (this) {
      //_protocol.onCloseRead();

      // XXX: ka state
      _pollHandle.destroy();

      try {
        writeStream().close();
      } catch (Throwable e) {
        log.log(Level.FINER, e.toString(), e);
      }

      try {
        readStream().close();
      } catch (Throwable e) {
        log.log(Level.FINER, e.toString(), e);
      }

      PortTcp port = port();

      SocketBar socket = _socket;

      if (port != null) {
        port.closeSocket(socket);
      }

      try {
        socket.close();
      } catch (Throwable e) {
        log.log(Level.FINER, e.toString(), e);
      }

      try {
        connProtocol().onClose();
      } catch (Throwable e) {
        log.warning(e.toString());

        if (log.isLoggable(Level.FINER)) {
          log.log(Level.FINER, e.toString(), e);
        }
      }

      // XXX: recycle

      if (log.isLoggable(Level.FINER)) {
        if (port != null)
          log.finer("closing connection " + dbgId()
                    + ", total=" + port.getConnectionCount());
        else
          log.finer("closing connection " + id());
      }

      StateConnection oldState = _state;
      _state = _state.toFree();

      if (_state.isFree()) {
        _port.freeConnection(this);
      }
      else {
        System.out.println("UNFREE: " + oldState + " " + this);
      }
    }
  }

  /**
   * Destroy kills the connection and drops it from the connection pool.
   *
   * Destroy should only occur if the connection state machine has failed or
   *
   */
  private void destroy()
  {
    if (log.isLoggable(Level.FINEST)) {
      log.finest(this + " destroying connection");
    }

    try {
      _socket.forceShutdown();
    } catch (Throwable e) {
    }

    try {
      closeConnection();
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);
    }

    // XXX:
    _port.removeConnection(this);
  }

  private String dbgId()
  {
    if (_dbgId == null) {
      /*
      Object serverId = Environment.getAttribute("caucho.server-id");

      if (serverId != null)
        _dbgId = (getClass().getSimpleName() + "[id=" + getId()
                  + ",seq=" + _connectionSequence
                  + "," + serverId + "] ");
      else
        _dbgId = (getClass().getSimpleName() + "[id=" + getId() + "] ");
        */
      if (_port != null) {
        _dbgId = _port.url() + '-' + id();
      }
      else {
        _dbgId = getClass().getSimpleName() + '-' + id();
      }
    }

    return _dbgId;
  }

  @Override
  public int hashCode()
  {
    return (int) _connectionId;
  }

  public boolean equals(Object obj)
  {
    return this == obj;
  }

  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
             + "[id=" + dbgId() + "," + _port.url()
             + ",seq=" + _connectionSequence
             + "," + _state
             + "]");
  }

  enum Task {
    ACCEPT,
    KEEPALIVE;
  }
}
