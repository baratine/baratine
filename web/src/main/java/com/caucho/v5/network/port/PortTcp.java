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

import javax.annotation.PostConstruct;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.thread.IdleThreadLauncher;
import com.caucho.v5.amp.thread.ThreadPool;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.health.meter.ActiveMeter;
import com.caucho.v5.health.meter.CountMeter;
import com.caucho.v5.health.meter.MeterService;
import com.caucho.v5.io.ReadBuffer;
import com.caucho.v5.io.SSLFactory;
import com.caucho.v5.io.ServerSocketBar;
import com.caucho.v5.io.SocketBar;
import com.caucho.v5.io.SocketSystem;
import com.caucho.v5.jni.OpenSSLFactory;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.network.ssl.SSLFactoryJsse;
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

  private static final int ACCEPT_IDLE_MIN = 4;
  private static final int ACCEPT_IDLE_MAX = 64;

  private static final int ACCEPT_THROTTLE_LIMIT = 1024;
  private static final long ACCEPT_THROTTLE_SLEEP_TIME = 0;

  private static final int KEEPALIVE_MAX = 65536;

  private static final CountMeter _throttleDisconnectMeter
    = MeterService.createCountMeter("Caucho|Port|Throttle Disconnect Count");

  private static final CountMeter _keepaliveMeter
    = MeterService.createCountMeter("Caucho|Port|Keepalive Count");

  private static final ActiveMeter _keepaliveThreadMeter
    = MeterService.createActiveMeter("Caucho|Port|Keepalive Thread");

  private static final ActiveMeter _suspendMeter
    = MeterService.createActiveMeter("Caucho|Port|Request Suspend");

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

  private int _connectionMax = 1024 * 1024;

  private int _keepaliveMax = -1;

  private long _keepaliveTimeMax = 10 * 60 * 1000L;
  private long _keepaliveTimeout = 120 * 1000L;

  private boolean _isKeepaliveAsyncEnable = true;
  private long _keepalivePollThreadTimeout = 1000;

  // default timeout
  private long _socketTimeout = 120 * 1000L;

  private long _suspendReaperTimeout = 60000L;
  private long _suspendTimeMax = 600 * 1000L;
  // after for 120s start checking for EOF on comet requests
  private long _suspendCloseTimeMax = 120 * 1000L;

  private long _requestTimeout = -1;

  private boolean _isTcpNoDelay = true;
  private boolean _isTcpKeepalive;
  private boolean _isTcpCork;

  private boolean _isEnableJni = true;

  // The virtual host name
  private String _virtualHost;

  //private final AdminPortTcp _admin = new AdminPortTcp(this);

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
  private final AtomicInteger _shutdownRequestCount = new AtomicInteger();

  // reaper alarm for timed out comet requests
  private Alarm _suspendAlarm;

  // statistics

  private final AtomicLong _lifetimeRequestCount = new AtomicLong();
  private final AtomicLong _lifetimeKeepaliveCount = new AtomicLong();
  private final AtomicLong _lifetimeKeepaliveSelectCount = new AtomicLong();
  private final AtomicLong _lifetimeClientDisconnectCount = new AtomicLong();
  private final AtomicLong _lifetimeRequestTime = new AtomicLong();
  private final AtomicLong _lifetimeReadBytes = new AtomicLong();
  private final AtomicLong _lifetimeWriteBytes = new AtomicLong();
  private final AtomicLong _lifetimeThrottleDisconnectCount = new AtomicLong();

  // total keepalive
  private AtomicInteger _keepaliveAllocateCount = new AtomicInteger();
  // thread-based
  private AtomicInteger _keepaliveThreadCount = new AtomicInteger();
  // True if the port has been bound
  private final AtomicBoolean _isBind = new AtomicBoolean();
  private final AtomicBoolean _isPostBind = new AtomicBoolean();

  // The port lifecycle
  private final Lifecycle _lifecycle = new Lifecycle();

  private ServicesAmp _ampManager;

  private PortTcpBuilder _builder;

  public PortTcp(PortTcpBuilder builder)
  {
    /*
    if (CauchoUtil.is64Bit()) {
      // on 64-bit machines we can use more threads before parking in nio
      _keepalivePollThreadTimeout = 60000;
    }
    */
    
    _builder = builder;
    
    _keepalivePollThreadTimeout = 60000;
    
    _ampManager = builder.ampManager(); // AmpSystem.getCurrentManager();
    Objects.requireNonNull(_ampManager);
    
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
  public ServicesAmp ampManager()
  {
    return _ampManager;
  }

  public String getDebugId()
  {
    return getUrl();
  }

  public ClassLoader classLoader()
  {
    return _classLoader;
  }

  /**
   * Returns the protocol handler responsible for generating protocol-specific
   * ProtocolConnections.
   */
  public Protocol protocol()
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
   * Gets the virtual host for IP-based virtual host.
   */
  public String getVirtualHost()
  {
    return _virtualHost;
  }

  /**
   * Sets the SSL factory
   */
  public void setSSL(SSLFactory factory)
  {
    _sslFactory = factory;
  }

  /**
   * Sets the SSL factory
   */
  //@Configurable
  public SSLFactory createOpenssl()
    throws ConfigException
  {
    OpenSSLFactory openSslFactory = new OpenSSLFactory();

    if (protocol() != null) {
      openSslFactory.setNextProtocols(protocol().nextProtocols());
    }
    
    _sslFactory = openSslFactory;

    return _sslFactory;
  }

  /**
   * Sets the SSL factory
   */
  /*
  public JsseSSLFactory createJsse()
  {
    // should probably check that openssl exists
    return new JsseSSLFactory(_env, portName());
  }
  */

  /**
   * Sets the SSL factory
   */
  /*
  public void setJsseSsl(JsseSSLFactory factory)
  {
    _sslFactory = factory;
  }
  */

  /**
   * Gets the SSL factory.
   */
  public SSLFactory getSSL()
  {
    return _sslFactory;
  }

  /**
   * Returns true for ssl.
   */
  public boolean isSSL()
  {
    return _sslFactory != null;
  }

  /**
   * Return true for secure
   */
  public boolean isSecure()
  {
    return _isSecure || _sslFactory != null;
  }

  //
  // Configuration/Tuning
  //

  /**
   * The minimum spare threads.
   */
  /*
  public int getAcceptThreadMin()
  {
    return _connThreadPool.getIdleMin();
  }
  */

  /**
   * The maximum spare threads.
   */
  /*
  public int getAcceptThreadMax()
  {
    return _connThreadPool.getIdleMax();
  }
  */

  /*
  //@Configurable
  public void setPortThreadMax(int max)
  {
    int threadMax = ThreadPool.getThreadPool().getThreadMax();

    if (threadMax < max) {
      log.warning(L.l("<port-thread-max> value '{0}' should be less than <thread-max> value '{1}'",
                      max, threadMax));
    }

    _connThreadPool.setThreadMax(max);
  }
  */

  /*
  public int getPortThreadMax()
  {
    return _connThreadPool.getThreadMax();
  }
  */

    /**
   * Sets the minimum spare idle timeout.
   */
  /*
  //@Configurable
  public void setAcceptThreadIdleTimeout(Duration timeout)
    throws ConfigException
  {
    _connThreadPool.setIdleTimeout(timeout.toMillis());
  }
  */

  /**
   * Sets the minimum spare idle timeout.
   */
  /*
  public long getAcceptThreadIdleTimeout()
    throws ConfigException
  {
    return _connThreadPool.getIdleTimeout();
  }
  */

  /**
   * The operating system listen backlog
   */
  public int getAcceptListenBacklog()
  {
    return _acceptListenBacklog;
  }

  /**
   * Gets the connection max.
   */
  public int getConnectionMax()
  {
    return _connectionMax;
  }

  /**
   * Returns the max time for a request.
   */
  public long getRequestTimeout()
  {
    return _requestTimeout;
  }

  /**
   * Gets the read timeout for the accepted sockets.
   */
  public long getSocketTimeout()
  {
    return _socketTimeout;
  }

  /**
   * Gets the tcp-no-delay property
   */
  public boolean isTcpNoDelay()
  {
    return _isTcpNoDelay;
  }

  /**
   * Sets the tcp-no-delay property
   */
  //@Configurable
  public void setTcpNoDelay(boolean tcpNoDelay)
  {
    _isTcpNoDelay = tcpNoDelay;
  }

  public boolean isTcpKeepalive()
  {
    return _isTcpKeepalive;
  }

  /**
   * Gets the tcp-cork property
   */
  public boolean isTcpCork()
  {
    return _isTcpNoDelay;
  }

  /**
   * Configures the throttle.
   */
  public long getThrottleConcurrentMax()
  {
    if (_throttle != null)
      return _throttle.getMaxConcurrentRequests();
    else
      return -1;
  }

  public boolean isJniEnabled()
  {
    if (_serverSocket != null) {
      return _serverSocket.isJni();
    }
    else {
      return false;
    }
  }

  private ThrottleSocket createThrottle()
  {
    if (_throttle == null) {
      _throttle = new ThrottleSocketImpl();
    }

    return _throttle;
  }

  /**
   * Gets the keepalive max.
   */
  public int getKeepaliveMax()
  {
    return _keepaliveMax;
  }

  /**
   * Gets the keepalive max.
   */
  public long getKeepaliveConnectionTimeMax()
  {
    return _keepaliveTimeMax;
  }

  /**
   * Gets the suspend max.
   */
  public long getSuspendTimeMax()
  {
    return _suspendTimeMax;
  }

  public long getKeepaliveTimeout()
  {
    return _keepaliveTimeout;
  }

  public boolean isKeepaliveAsyncEnabled()
  {
    return _isKeepaliveAsyncEnable;
  }

  public long getKeepaliveSelectThreadTimeout()
  {
    return _keepalivePollThreadTimeout;
  }

  public long getKeepaliveThreadTimeout()
  {
    return _keepalivePollThreadTimeout;
  }

  public long getBlockingTimeoutForPoll()
  {
    long timeout = _keepalivePollThreadTimeout;

    if (timeout <= 10)
      return timeout;
    else if (_threadPool.getFreeThreadCount() < 64)
      return 10;
    else
      return timeout;
  }

  public int pollMax()
  {
    if (getPollManager() != null)
      return getPollManager().pollMax();
    else
      return -1;
  }

  /**
   * Returns the thread launcher for the link.
   */
  /*
  IdleThreadManager getThreadManager()
  {
    return _connThreadPool;
  }
  */

  ThreadPool getThreadPool()
  {
    return _threadPool;
  }

  //
  // statistics
  //

  /**
   * Returns the thread count.
   */
  /*
  public int getThreadCount()
  {
    return _connThreadPool.getThreadCount();
  }
  */

  /**
   * Returns the active thread count.
   */
  /*
  public int getActiveThreadCount()
  {
    return _connThreadPool.getThreadCount() - _connThreadPool.getIdleCount();
  }
  */

  /**
   * Returns the count of idle threads.
   */
  /*
  public int getIdleThreadCount()
  {
    return _connThreadPool.getIdleCount();
  }
  */

  /**
   * Returns the count of start threads.
   */
  /*
  public int getStartThreadCount()
  {
    return _connThreadPool.getStartingCount();
  }
  */

  /**
   * Returns the number of keepalive connections
   */
  public int getKeepaliveCount()
  {
    return _keepaliveAllocateCount.get();
  }

  public Lifecycle getLifecycleState()
  {
    return _lifecycle;
  }

  public boolean isAfterBind()
  {
    return _isBind.get();
  }
  /**
   * Returns true if the port is active.
   */
  public boolean isActive()
  {
    return _lifecycle.isActive();
  }

  /**
   * Returns the active connections.
   */
  /*
  public int getActiveConnectionCount()
  {
    return getActiveThreadCount();
  }
  */

  /**
   * Returns the keepalive connections.
   */
  public int getKeepaliveConnectionCount()
  {
    return getKeepaliveCount();
  }

  /**
   * Returns the number of keepalive connections
   */
  public int getKeepaliveThreadCount()
  {
    return _keepaliveThreadCount.get();
  }

  /**
   * Returns the number of connections in the select.
   */
  public int getSelectConnectionCount()
  {
    if (_pollManager != null)
      return _pollManager.getSelectCount();
    else
      return -1;
  }

  /**
   * Returns the server socket class name for debugging.
   */
  public String getServerSocketClassName()
  {
    ServerSocketBar ss = _serverSocket;

    if (ss != null)
      return ss.getClass().getName();
    else
      return null;
  }

  /**
   * Initializes the port.
   */
  @PostConstruct
  public void init()
    throws ConfigException
  {
    if (! _lifecycle.toInit())
      return;
  }

  public String getUrl()
  {
    if (_url == null) {
      StringBuilder url = new StringBuilder();

      if (_protocol != null)
        url.append(_protocol.name());
      else
        url.append("unknown");
      
      if (isSSL()) {
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
    
    String protocolName = _protocol.name();
    
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

  public void postBind()
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

  public boolean isEnabled()
  {
    return _lifecycle.isActive();
  }

  /**
   * Starts the port listening for new connections.
   */
  public void enable()
  {
    if (_lifecycle.toActive()) {
      if (_serverSocket != null) {
        _serverSocket.listen(_acceptListenBacklog);
      }
    }
  }

  /**
   * Stops the port from listening for new connections.
   */
  public void disable()
  {
    if (_lifecycle.toStop()) {
      if (_serverSocket != null)
        _serverSocket.listen(0);

      if (_port < 0) {
      }
      else if (_address != null)
        log.info(_protocol.name() + " disabled "
                 + _address + ":" + getLocalPort());
      else
        log.info(_protocol.name() + " disabled *:" + getLocalPort());
    }
  }

  /**
   * returns the connection info for jmx
   */
  /*
  TcpConnectionInfo []getActiveConnections()
  {
    List<TcpConnectionInfo> infoList = new ArrayList<TcpConnectionInfo>();

    ConnectionTcp[] connections = new ConnectionTcp[_activeConnectionSet.size()];
    _activeConnectionSet.keySet().toArray(connections);

    for (int i = 0 ; i < connections.length; i++) {
      TcpConnectionInfo connInfo = connections[i].getConnectionInfo();
      if (connInfo != null)
        infoList.add(connInfo);
    }

    TcpConnectionInfo []infoArray = new TcpConnectionInfo[infoList.size()];
    infoList.toArray(infoArray);

    return infoArray;
  }
  */

  /**
   * returns the select manager.
   */
  public PollTcpManagerBase getPollManager()
  {
    return _pollManager;
  }

  /**
   * Accepts a new connection.
   */
  /*
  @Friend(ConnectionTcp.class)
  boolean accept(QSocket socket)
  {
    try {
      IdleThreadManager threadPool = getThreadManager();

      while (! isClosed()) {
        // Thread.interrupted();

        if (_serverSocket.accept(socket)) {
          // System.out.println("REMOTE: " + socket.getRemotePort() + " " + _serverSocket);
          
          if (threadPool.isThreadMax()
              && ! isKeepaliveAsyncEnabled()
              && threadPool.getIdleCount() <= 1) {
            // System.out.println("CLOSED:");
            _throttleDisconnectMeter.start();
            _lifetimeThrottleDisconnectCount.incrementAndGet();
            socket.close();
          }
          else if (_throttle.accept(socket)) {
            if (! isClosed()) {
              return true;
            }
            else {
              socket.close();
            }
          }
          else {
            _throttleDisconnectMeter.start();
            _lifetimeThrottleDisconnectCount.incrementAndGet();
            socket.close();
          }
        }
      }
    } catch (Throwable e) {
      if (_lifecycle.isActive() && log.isLoggable(Level.FINER))
        log.log(Level.FINER, e.toString(), e);
    }

    return false;
  }
  */

  /**
   * Returns the next unique connection sequence.
   */
  public long nextConnectionSequence()
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
   * request threads in a shutdown, but not yet idle.
   */
  void requestShutdownBegin()
  {
    _shutdownRequestCount.incrementAndGet();
  }

  /**
   * request threads in a shutdown, but not yet idle.
   */
  void requestShutdownEnd()
  {
    _shutdownRequestCount.decrementAndGet();
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
  public boolean isAsyncThrottle()
  {
    return isKeepaliveAsyncEnabled();// && _connThreadPool.isThreadHigh();
  }


  /**
   * Marks the keepalive allocation as starting.
   * Only called from ConnectionState.
   */
  void keepaliveAllocate()
  {
    _keepaliveAllocateCount.incrementAndGet();
  }

  /**
   * Marks the keepalive allocation as ending.
   * Only called from ConnectionState.
   */
  void keepaliveFree()
  {
    int value = _keepaliveAllocateCount.decrementAndGet();

    if (value < 0 && ! isClosed()) {
      System.out.println("FAILED keep-alive; " + value);
      Thread.dumpStack();
    }
  }

  /**
   * Reads data from a keepalive connection
   */
  int keepaliveThreadRead(ReadBuffer is, long timeoutConn)
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
   * Suspends the controller (for comet-style ajax)
   *
   * @return true if the connection was added to the suspend list
   */
  /*
  @Friend(StateConnection.class)
  void suspendAttach(ConnectionTcp conn)
  {
    _suspendMeter.start();

    _suspendConnectionSet.add(conn);
  }
  */

  /**
   * Remove from suspend list.
   */
  /*
  @Friend(StateConnection.class)
  boolean suspendDetach(ConnectionTcp conn)
  {
    _suspendMeter.end();

    return _suspendConnectionSet.remove(conn);
  }
  */

  void duplexKeepaliveBegin()
  {
  }

  void duplexKeepaliveEnd()
  {
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

  /**
   * Returns the number of connections
   */
  public int getConnectionCount()
  {
    return _activeConnectionCount.get();
  }

  /**
   * Returns the number of comet connections.
   */
  public int getSuspendCount()
  {
    // return _suspendConnectionSet.size();
    return 0;
  }

  /**
   * Returns the number of duplex connections.
   */
  public int getDuplexCount()
  {
    return 0;
  }

  void addLifetimeRequestCount()
  {
    _lifetimeRequestCount.incrementAndGet();
  }

  public long getLifetimeRequestCount()
  {
    return _lifetimeRequestCount.get();
  }

  void addLifetimeKeepaliveCount()
  {
    _keepaliveMeter.start();
    _lifetimeKeepaliveCount.incrementAndGet();
  }

  public long getLifetimeKeepaliveCount()
  {
    return _lifetimeKeepaliveCount.get();
  }

  void addLifetimeKeepalivePollCount()
  {
    _lifetimeKeepaliveSelectCount.incrementAndGet();
  }

  public long getLifetimeKeepaliveSelectCount()
  {
    return _lifetimeKeepaliveSelectCount.get();
  }

  void addLifetimeClientDisconnectCount()
  {
    _lifetimeClientDisconnectCount.incrementAndGet();
  }

  public long getLifetimeClientDisconnectCount()
  {
    return _lifetimeClientDisconnectCount.get();
  }

  void addLifetimeRequestTime(long time)
  {
    _lifetimeRequestTime.addAndGet(time);
  }

  public long getLifetimeRequestTime()
  {
    return _lifetimeRequestTime.get();
  }

  void addLifetimeReadBytes(long bytes)
  {
    _lifetimeReadBytes.addAndGet(bytes);
  }

  public long getLifetimeReadBytes()
  {
    return _lifetimeReadBytes.get();
  }

  void addLifetimeWriteBytes(long bytes)
  {
    _lifetimeWriteBytes.addAndGet(bytes);
  }

  public long getLifetimeWriteBytes()
  {
    return _lifetimeWriteBytes.get();
  }

  long getLifetimeThrottleDisconnectCount()
  {
    return _lifetimeThrottleDisconnectCount.get();
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
    else if (isActive()){
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

  public void ssl(SocketBar socket)
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

  public String toURL()
  {
    return getUrl();
  }

  /*
  @Override
  protected String getThreadName()
  {
    return "resin-port-" + getAddress() + ":" + getPort();
  }
  */
  
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

  private class ConnectionThreadLauncher implements IdleThreadLauncher
  {
    @Override
    public void launchChildThread(int id)
    {
      try {
        ConnectionTcp conn = newConnection();
        
        System.out.println("NOOO:");
        Thread.dumpStack();

        conn.proxy().requestAccept();
        /*
          System.out.println("FAIL ACCEPT: " + conn);
        }
        */
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
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

          if (! conn.getState().isTimeoutCapable()) {
            continue;
          }
          
          if (conn.idleExpireTime() < now) {
            _timeoutSet.add(conn);
            continue;
          }

          long idleStartTime = conn.idleStartTime();

          // check periodically for end of file
          if (idleStartTime + _suspendCloseTimeMax < now
              && conn.isIdle()
              && conn.isReadEof()) {
            _completeSet.add(conn);
          }
        }

        for (int i = _timeoutSet.size() - 1; i >= 0; i--) {
          ConnectionTcp conn = _timeoutSet.get(i);

          try {
            if (conn.requestCometTimeout()) {
              if (log.isLoggable(Level.FINE))
                log.fine("suspend idle timeout " + conn);
              
            }
          } catch (Exception e) {
            log.log(Level.WARNING, conn + ": " + e.getMessage(), e);
          }
        }

        for (int i = _completeSet.size() - 1; i >= 0; i--) {
          ConnectionTcp conn = _completeSet.get(i);

          if (log.isLoggable(Level.FINE))
            log.fine(this + " async end-of-file " + conn);

          try {
            conn.requestCometComplete();
          } catch (Exception e) {
            log.log(Level.WARNING, conn + ": " + e.getMessage(), e);
          }
          /*
          AsyncController async = conn.getAsyncController();

          if (async != null)
            async.complete();
            */

          // server/1lc2
          // conn.wake();
          // conn.destroy();
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
