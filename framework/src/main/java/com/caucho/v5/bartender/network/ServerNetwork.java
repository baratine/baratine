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

package com.caucho.v5.bartender.network;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ClusterBartender;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.config.Configs;
import com.caucho.v5.config.Configurable;
import com.caucho.v5.config.types.Period;
import com.caucho.v5.network.NetworkSystemBartender;
import com.caucho.v5.network.balance.ClientSocketFactory;
import com.caucho.v5.network.port.PortTcp;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;

import io.baratine.config.Config;

/**
 * Defines a member of the cluster, corresponds to <server> in the conf file.
 *
 * A {@link ServerConnector} obtained with {@link #getServerConnector} is used to actually
 * communicate with this ClusterServer when it is active in another instance of
 * Resin .
 */
public final class ServerNetwork
{
  private static final L10N L = new L10N(ServerNetwork.class);
  private static final Logger log
    = Logger.getLogger(ServerNetwork.class.getName());

  private final NetworkSystemBartender _networkSystem;
  private final ServerBartender _serverBartender;

  // unique identifier for the server within the cluster
  private String _serverClusterId;
  // unique identifier for the server within the cluster
  private String _serverSessionId;
  // unique identifier for the server within all Resin clusters
  private String _serverDomainId;

  // the champ admin name
  private String _champAddress;
  
  private boolean _isRemotePod;

  //
  // config parameters
  //

  private int _loadBalanceConnectionMin = 0;
  private long _loadBalanceIdleTime = 60000L;
  private long _loadBalanceBusyRecoverTime = 15000L;
  private long _loadBalanceRecoverTime = 15000L;
  private long _loadBalanceSocketTimeout = 600000L;
  private long _loadBalanceWarmupTime = 60000L;

  private long _loadBalanceConnectTimeout = 5000L;

  private int _loadBalanceWeight = 100;
  private boolean _isBackup;
  
  private long _clusterIdleTime = 3 * 60000L;
  private long _clusterSocketTimeout = 10 * 60000L;
  
  /*
  private ConfigProgram _portDefaults = new ContainerProgram();

  private ContainerProgram _serverProgram
    = new ContainerProgram();
    */

  private String _stage;
  private ArrayList<String> _pingUrls = new ArrayList<String>();
  
  private ArrayList<PortTcp> _listeners
    = new ArrayList<>();

  // runtime
  
  private String _address;
  private int _port;

  private AtomicReference<SocketPool> _clusterSocketPool
    = new AtomicReference<>();
  
  private AtomicReference<SocketPool> _loadBalanceSocketPool
    = new AtomicReference<>();
  private Config _env;

  //private final ServerHeartbeatState _heartbeatState;

  // admin

  // private ServerNetworkAdmin _admin = new ServerNetworkAdmin(this);

  ServerNetwork(NetworkSystemBartender networkSystem,
                ServerBartender serverBartender)
  {
    Objects.requireNonNull(networkSystem);
    Objects.requireNonNull(serverBartender);
    
    _env = Configs.config().get();
    
    _networkSystem = networkSystem;
    _serverBartender = serverBartender;
    
    /*
    // XXX: active isn't quite right here
    if (serverBartender.getPod() != networkSystem.getSelfServer().getPod()) {
      _isRemotePod = true;
      // _isHeartbeatActive.set(true);
    }
    */
    
    //_heartbeatState = new ServerHeartbeatState(this);

    // XXX: _serverClusterId = getServerAddress(getIndex(), getPod().getIndex());
    // XXX: _serverSessionId = getSessionAddress(getIndex(), getPod().getIndex());

    String clusterId = serverBartender.getCluster().id();

    _serverDomainId = _serverClusterId + "." + clusterId.replace('.', '_');

    _champAddress = "bartender://" + serverBartender.getId(); // + ".admin.resin";
    _port = serverBartender.port();
    
    if (! isExternal()) {
      _address = serverBartender.getAddress();
    }
    else if (serverBartender.isSelf()) {
      _address = lookupLocalAddress();
    }
    else {
      _address = "127.0.0.2";
    }
  }
  
  /*
  public static ServerBartender getSelfServer()
  {
    NetworkClusterSystem system = NetworkClusterSystem.getCurrent();
    
    if (system == null)
      throw new IllegalStateException();
    
    return system.getSelfServer();
  }
  */
  
  public static ServerNetwork getCurrent()
  {
    NetworkSystemBartender network = NetworkSystemBartender.current();
    
    return network.getSelfServerCluster();
  }

  /**
   * Gets the server identifier.
   */
  public String getId()
  {
    return _serverBartender .getId();
  }

  public String getDebugId()
  {
    if ("".equals(getId()))
      return "default";
    else
      return getId();
  }

  public ServerBartender getServerBartender()
  {
    return _serverBartender;
  }
  
  /**
   * Returns the server's id within the cluster
   */
  public String getServerClusterId()
  {
    return _serverClusterId;
  }
  
  /**
   * Returns the server's id within the cluster
   */
  public String getServerSessionId()
  {
    return _serverSessionId;
  }

  /**
   * Returns the server's id within all Resin clusters
   */
  public String getServerDomainId()
  {
    return _serverDomainId;
  }

  /**
   * Returns the champ name
   */
  public String getChampAdminName()
  {
    return _champAddress;
  }

  /**
   * Returns the cluster.
   */
  public ClusterBartender getCluster()
  {
    return _serverBartender.getCluster();
  }

  /**
   * Returns the owning pod
   */
  /*
  public RackHeartbeat getPod()
  {
    return _serverBartender.getRack();
  }
  */

  /**
   * Returns true if this server is a triad.
   */
  public boolean isTriad()
  {
    return _serverBartender.getServerIndex() < 3;
  }

  /**
   * Returns the pod owner
   */
  /*
  public TriadOwner getTriadOwner()
  {
    return TriadOwner.getOwner(getIndex());
  }
  */

  /**
   * Returns the server index within the pod.
   */
  public int getIndex()
  {
    return _serverBartender.getServerIndex();
  }

 
  /**
   * Gets the address
   */
  public String getAddress()
  {
    return _address;
  }
  
  public String getExternalAddress()
  {
    if (! isExternal()) {
      return _address;
    }
    
    ClientSocketFactory factory = getClusterSocketFactory();
    
    String result;
    
    if (factory != null) {
      result = factory.getAddress();
    }
    else {
      // returns null to avoid caller connection
      result = null;
    }
    
    return result;
  }
  
  public int getPort()
  {
    return _port;
  }
  
  public void setPort(int port)
  {
    _port = port;
  }
  
  private boolean isExternal()
  {
    //return _serverBartender.isExternal();
    return false;
  }
  
  public boolean isSSL()
  {
    // return _serverBartender.isSSL();
    return false;
  }

  /**
   * Sets true for backups
   */
  public void setBackup(boolean isBackup)
  {
    _isBackup = isBackup;

    if (isBackup)
      setLoadBalanceWeight(1);
  }
  
  public boolean isBackup()
  {
    return _isBackup;
  }

  /**
   * True for a dynamic server
   */
  public boolean isDynamic()
  {
    //return ! _serverBartender.isStatic();
    return true;
  }
  
  //
  // load balance configuration
  //

  /**
   * Sets the loadBalance connection time.
   */
  /*
  public void setLoadBalanceConnectTimeout(Period period)
  {
    _loadBalanceConnectTimeout = period.getPeriod();
  }
  */

  /**
   * Gets the loadBalance connection time.
   */
  public long getLoadBalanceConnectTimeout()
  {
    return _loadBalanceConnectTimeout;
  }

  /**
   * The minimum number of load balance connections for green load balancing.
   */
  @Configurable
  public void setLoadBalanceConnectionMin(int min)
  {
    _loadBalanceConnectionMin = min;
  }

  /**
   * The minimum number of load balance connections for green load balancing.
   */
  public int getLoadBalanceConnectionMin()
  {
    return _loadBalanceConnectionMin;
  }

  /**
   * Sets the loadBalance socket time.
   */
  /*
  public void setLoadBalanceSocketTimeout(Period period)
  {
    _loadBalanceSocketTimeout = period.getPeriod();
  }
  */

  /**
   * Gets the loadBalance socket time.
   */
  public long getLoadBalanceSocketTimeout()
  {
    return _loadBalanceSocketTimeout;
  }

  /**
   * Sets the loadBalance max-idle-time.
   */
  /*
  public void setLoadBalanceIdleTime(Period period)
  {
    _loadBalanceIdleTime = period.getPeriod();
  }
  */

  /**
   * Sets the loadBalance idle-time.
   */
  public long getLoadBalanceIdleTime()
  {
    return _loadBalanceIdleTime;
  }

  /**
   * Sets the loadBalance fail-recover-time.
   */
  public void setLoadBalanceRecoverTime(Period period)
  {
    _loadBalanceRecoverTime = period.getPeriod();
  }

  /**
   * Gets the loadBalance fail-recover-time.
   */
  public long getLoadBalanceRecoverTime()
  {
    return _loadBalanceRecoverTime;
  }

  /**
   * Sets the loadBalance busy-recover-time.
   */
  public void setLoadBalanceBusyRecoverTime(Period period)
  {
    _loadBalanceBusyRecoverTime = period.getPeriod();
  }

  /**
   * Gets the loadBalance fail-recover-time.
   */
  public long getLoadBalanceBusyRecoverTime()
  {
    return _loadBalanceBusyRecoverTime;
  }

  /**
   * The cluster idle-time.
   */
  public long getClusterIdleTime()
  {
    return _clusterIdleTime;
  }
  
  @Configurable
  public void setClusterSocketTimeout(Period period)
  {
    _clusterSocketTimeout = period.getPeriod();
  }

  /**
   * The cluster socket-timeout
   */
  public long getClusterSocketTimeout()
  {
    return _clusterSocketTimeout;
  }

  
  //
  // port defaults
  //
  
  /*
  @Configurable
  public PortTcp createHttp()
    throws ConfigException
  {
    PortTcpBuilder portBuilder = new PortTcpBuilder(_env);
    
    ProtocolHttp protocol = new ProtocolHttp();
    portBuilder.protocol(protocol);
    
    PortTcp listener = new PortTcp(portBuilder);
    
    applyPortDefaults(listener);

    // getListenService().addListener(listener);

    return listener;
  }
  */
  
  public void addHttp(PortTcp listener)
  {
    if (listener.port() <= 0) {
      log.fine(listener + " skipping because port is 0.");
      return;
    }
    
    _listeners.add(listener);
  }

  public void addListen(PortTcp listener)
  {
    if (listener.port() <= 0) {
      log.fine(listener + " skipping because port is 0.");
      return;
    }
    
    _listeners.add(listener);
  }

  /*
  @Configurable
  public void add(ProtocolPort protocolPort)
  {
    PortTcpBuilder portBuilder = new PortTcpBuilder(_env);

    portBuilder.protocol(protocolPort.getProtocol());
    
    PortTcp listener = new PortTcp(portBuilder);

    applyPortDefaults(listener);

    protocolPort.getConfigProgram().configure(listener);

    _listeners.add(listener);
  }
  */
  
  public ArrayList<PortTcp> getListeners()
  {
    return _listeners;
  }

  /**
   * Adds a port-default
   */
  /*
  @Configurable
  public void addPortDefault(ContainerProgram program)
  {
    addListenDefault(program);
  }
  */

  /**
   * Adds a listen-default
   */
  /*
  @Configurable
  public void addListenDefault(ConfigProgram program)
  {
    _portDefaults.addProgram(program);
  }

  private void applyPortDefaults(PortTcp port)
  {
    _portDefaults.configure(port);
    
    // port.setKeepaliveSelectEnable(isKeepaliveSelectEnable());
  }
  */


  //
  // Configuration from <server>
  //

  /**
   * Sets the socket's listen property
   */
  /*
  public void setAcceptListenBacklog(ConfigProgram program)
  {
    _portDefaults.addProgram(program);
  }
  */

  /**
   * Sets the minimum spare listen.
   */
  /*
  public void setAcceptThreadMin(ConfigProgram program)
    throws ConfigException
  {
    _portDefaults.addProgram(program);
  }
  */

  /**
   * Sets the maximum spare listen.
   */
  /*
  public void setAcceptThreadMax(ConfigProgram program)
    throws ConfigException
  {
    _portDefaults.addProgram(program);
  }
  */

  /**
   * Sets the maximum connections per port
   */
  /*
  public void setConnectionMax(ConfigProgram program)
  {
    _portDefaults.addProgram(program);
  }
  */

  /**
   * Sets the maximum keepalive
   */
  /*
  public void setKeepaliveMax(ConfigProgram program)
  {
    _portDefaults.addProgram(program);
  }
  */

  /**
   * Sets the keepalive timeout
   */
  /*
  public void setKeepaliveTimeout(ConfigProgram program)
  {
    _portDefaults.addProgram(program);
  }
  */

  /**
   * Sets the keepalive connection timeout
   */
  /*
  public void setKeepaliveConnectionTimeMax(ConfigProgram program)
  {
    _portDefaults.addProgram(program);
  }
  */

  /**
   * Sets the select-based keepalive timeout
   */
  /*
  public void setKeepaliveSelectEnable(ConfigProgram program)
  {
    _portDefaults.addProgram(program);
  }
  */

  /**
   * Sets the select-based keepalive timeout
   */
  /*
  public void setKeepaliveSelectMax(ConfigProgram program)
  {
    _portDefaults.addProgram(program);
  }
  */

  /**
   * Sets the select-based keepalive timeout
   */
  /*
  public void setKeepaliveSelectThreadTimeout(ConfigProgram program)
  {
    _portDefaults.addProgram(program);
  }
  */

  /**
   * Sets the suspend timeout
   */
  /*
  public void setSocketTimeout(ConfigProgram program)
  {
    _portDefaults.addProgram(program);
  }
  */

  /**
   * Sets the suspend timeout
   */
  /*
  public void setSuspendTimeMax(ConfigProgram program)
  {
    _portDefaults.addProgram(program);
  }
  */

  public void setStage(String stage)
  {
    _stage = stage;
  }
  
  public String getStage()
  {
    return _stage;
  }
  
  /**
   * Adds a ping url for availability testing
   */
  public void addPingUrl(String url)
  {
    _pingUrls.add(url);
  }

  /**
   * Returns the ping url list
   */
  public ArrayList<String> getPingUrlList()
  {
    return _pingUrls;
  }

  /**
   * Sets the loadBalance warmup time
   */
  public void setLoadBalanceWarmupTime(Period period)
  {
    _loadBalanceWarmupTime = period.getPeriod();
  }

  /**
   * Gets the loadBalance warmup time
   */
  public long getLoadBalanceWarmupTime()
  {
    return _loadBalanceWarmupTime;
  }

  /**
   * Sets the loadBalance weight
   */
  public void setLoadBalanceWeight(int weight)
  {
    _loadBalanceWeight = weight;
  }

  /**
   * Gets the loadBalance weight
   */
  public int getLoadBalanceWeight()
  {
    return _loadBalanceWeight;
  }

  /**
   * Gets the ip.
   */
  public String getIp()
  {
    return getServerBartender().getAddress();
  }

  /**
   * Returns true for the self server
   */
  public boolean isSelf()
  {
    return getServerBartender().isSelf();
  }
  
  public boolean isRemote()
  {
    return ! isSelf();
  }
  
  /**
   * Looks up the local address when given an external address, e.g. for
   * cloud systems that dynamically allocate local addresses.
   */
  private String lookupLocalAddress()
  {
    long timeout = 120 * 1000L;
    
    long expireTime = CurrentTime.getCurrentTime() + timeout;
    
    String address;
    
    while ((address = allocateLocalAddress()) == null
           && CurrentTime.getCurrentTime() < expireTime) {
      try {
        Thread.sleep(1000);
      } catch (Exception e) {
      }
    }
    
    if (address == null) {
      throw new ConfigException(L.l("Cannot find an internal local IP address for server {0}, external IP {1} within 120s."
                                    + " 'external-address=true' is used for cloud networks where the internal address is allocated dynamically."
                                    + " Check that the persistent address has been assigned in the cloud configuration or DNS.",
                                    _serverBartender.getId(),
                                    _serverBartender.getAddress()));
    }
    
    return address;
  }
  
  private String allocateLocalAddress()
  {
    ArrayList<String> addressNames = new ArrayList<String>();

    for (InetAddress addr : NetworkSystemBartender.getLocalAddresses()) {
      String localAddress = getLocalAddress(addr);
      
      if (localAddress != null)
        addressNames.add(localAddress);
    }
      
    Collections.sort(addressNames);
    
    String address = null;
    
    if (addressNames.size() > 0)
      address = addressNames.get(0);

    return address;
  }
  
  private String getLocalAddress(InetAddress addr)
  {
    String address = addr.getHostAddress();
    
    byte []bytes = addr.getAddress();
    
    if (address.equals(_serverBartender.getAddress())) {
      // the external address cannot be the local address
      return null;
    }
    
    if (bytes[0] == 127) {
      // loopback isn't valid
      return null;
    }
    
    if (isLocal(bytes))
      return address;
    else
      return null;
  }
  
  private boolean isLocal(byte []bytes)
  {
    if (bytes.length != 4) {
      return false;
    }
    else if (bytes[0] == 10) {
      return true;
    }
    else if ((bytes[0] & 0xff) == 192 && (bytes[1] & 0xff) == 168) {
      return true;
    }
    else if ((bytes[0] & 0xff) == 172 && (bytes[1] & 0xf0) == 0x10) {
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Returns the socket pool as a pod cluster connector.
   */
  public final ClientSocketFactory getClusterSocketPool()
  {
    return getClusterSocketFactory();
  }

  /**
   * Returns the socket pool as a load-balancer.
   */
  public final ClientSocketFactory getLoadBalanceSocketPool()
  {
    ClientSocketFactory factory = getLoadBalanceSocketFactory();
    
    if (factory == null)
      return null;
    
    if (_serverBartender.getState().isDisableSoft()) {
      factory.enableSessionOnly();
    }
    else if (_serverBartender.getState().isDisabled()){
      // server/269g
      factory.disable();
    }
    else {
      factory.enable();
    }
    
    return factory;
  }
  
  private ClientSocketFactory peekClusterSocketFactory()
  {
    SocketPool socketPool = _clusterSocketPool.get();
    
    if (socketPool != null) {
      return socketPool.getFactory();
    }
    else {
      return null;
    }
  }
  
  private ClientSocketFactory getClusterSocketFactory()
  {
    synchronized (_clusterSocketPool) {
      SocketPool socketPool = _clusterSocketPool.get();

      if (socketPool != null) {
        return socketPool.getFactory();
      }

      if (! isExternal()) {
        return null;
      }

      if (isSelf()) {
        return null;
      }

      /*
      NetworkAddressResult result = null;
      
      result = _networkSystem.getLocalSocketAddress(_serverBartender);

      if (log.isLoggable(Level.FINE)) {
        log.fine(this + " getLocalSocketAddress -> " + result);
      }

      if (result == null) {
        return null;
      }
      */

      ClientSocketFactory factory
        = createClusterPool(_networkSystem.serverId(), 
                            _serverBartender.getAddress(), 
                            _serverBartender.port());

      factory.init();
      factory.start();

      socketPool = new SocketPool(factory);

      if (! _clusterSocketPool.compareAndSet(null, socketPool)) {
        factory.stop();

        socketPool = _clusterSocketPool.get();
      }

      if (socketPool != null) {
        return socketPool.getFactory();
      }
      else {
        return null;
      }
    }
  }
  
  private ClientSocketFactory getLoadBalanceSocketFactory()
  {
    SocketPool socketPool = _loadBalanceSocketPool.get();
    
    if (socketPool != null)
      return socketPool.getFactory();
    
    if (! isExternal())
      return null;
    
    return null;
  }
  
  
  /**
   * Returns true if the server is remote and active.
   */
  public final boolean isActiveRemote()
  {
    ClientSocketFactory pool = getClusterSocketPool();

    return pool != null && pool.isActive();
  }
  
  /**
   * Adds a program.
   */
  /*
  @Configurable
  public void addContentProgram(ConfigProgram program)
  {
    _serverProgram.addProgram(program);
  }
  */

  /**
   * Returns the configuration program for the Server.
   */
  /*
  public ConfigProgram getServerProgram()
  {
    return _serverProgram;
  }
  */
  
  /**
   * Returns the port defaults for the Server
   */
  /*
  public ConfigProgram getPortDefaults()
  {
    return _portDefaults;
  }
  */

  /**
   * Initialize
   */
  public void init()
  {
    /*
    if (! _isClusterPortConfig)
      applyPortDefaults(_clusterPort);

    _clusterPort.init();
    */

    // XXX: load balancer needs to be migrated to cluster
    if (false 
        && ! isSelf()
        && getServerBartender().port() >= 0
        && ! isExternal()) {
      ClientSocketFactory clusterFactory
      = createClusterPool(_networkSystem.serverId(), getAddress(), getPort());
      clusterFactory.init();
      
      _clusterSocketPool.set(new SocketPool(clusterFactory));
      
      ClientSocketFactory loadBalanceFactory
        = createLoadBalancePool(_networkSystem.serverId());
      loadBalanceFactory.init();
      
      _loadBalanceSocketPool.set(new SocketPool(loadBalanceFactory));
    }

    //_admin.register();
  }

  private ClientSocketFactory createLoadBalancePool(String serverId)
  {
    BartenderSystem bartender = BartenderSystem.current();
    ServerBartender server = bartender.getServerHandle(getAddress(), getPort());
    
    ClientSocketFactory pool = new ClientSocketFactory(serverId,
                                                       getId(),
                                                       "Resin|LoadBalanceSocket",
                                                       getStatId(),
                                                       server,
                                                       isSSL());

    pool.setLoadBalanceConnectTimeout(getLoadBalanceConnectTimeout());
    pool.setLoadBalanceConnectionMin(getLoadBalanceConnectionMin());
    pool.setLoadBalanceSocketTimeout(getLoadBalanceSocketTimeout());
    pool.setLoadBalanceIdleTime(getLoadBalanceIdleTime());
    pool.setLoadBalanceRecoverTime(getLoadBalanceRecoverTime());
    pool.setLoadBalanceBusyRecoverTime(getLoadBalanceBusyRecoverTime());
    pool.setLoadBalanceWarmupTime(getLoadBalanceWarmupTime());
    pool.setLoadBalanceWeight(getLoadBalanceWeight());
    
    return pool;
  }

  private ClientSocketFactory createClusterPool(String serverId,
                                                String address,
                                                int port)
  {
    if (true) {
      // XXX: load balancing needs to move out
      throw new UnsupportedOperationException();
    }
    
    if (port <= 0) {
      port = getPort();
    }
    
    BartenderSystem bartender = BartenderSystem.current();
    ServerBartender server = bartender.getServerHandle(address, port);
    
    ClientSocketFactory pool = new ClientSocketFactory(serverId,
                                                       getId(),
                                                       "Resin|ClusterSocket",
                                                       getStatId(),
                                                       server,
                                                       isSSL());
    
    pool.setLoadBalanceSocketTimeout(getClusterIdleTime());
    pool.setLoadBalanceIdleTime(getClusterIdleTime());
    
    // XXX: refactor
    //if (getServerBartender().getPod() == _networkSystem.getSelfServer().getPod())
    //  pool.setHeartbeatServer(true);
    
    return pool;
  }

  private String getStatId()
  {
    String targetCluster = getCluster().id();

    int index = getIndex();

    return String.format("%02x:%s", index, targetCluster);
  }

  public boolean isRemotePod()
  {
    return _isRemotePod;
  }
  
  /**
   * Test if the server is active, i.e. has received an active message.
   */
  public boolean isHeartbeatActive()
  {
    return _serverBartender.isUp();
  }

  /**
   * Returns the last state change timestamp.
   */
  public long getStateTimestamp()
  {
    // return _heartbeatState.getStateTimestamp();
    return 0;
  }
  
  public long getLastHeartbeatTime()
  {
    //return _heartbeatState.getLastHeartbeatTime();
    
    return 0;
  }
  
  public String getHeartbeatState()
  {
    return _serverBartender.getState().toString();
  }

  /**
   * Notify that a start event has been received.
   */
  boolean onHeartbeatStart()
  {
    ClientSocketFactory clusterSocketPool = peekClusterSocketFactory();
    
    if (clusterSocketPool != null) {
      clusterSocketPool.notifyHeartbeatStart();
    }

    return true;
  }

  /**
   * Notify that a stop event has been received.
   */
  boolean onHeartbeatStop()
  {
    SocketPool clusterSocketPool;
    
    if (isExternal()) {
      clusterSocketPool = _clusterSocketPool.getAndSet(null);
    }
    else {
      clusterSocketPool = _clusterSocketPool.get();
    }

    if (clusterSocketPool != null) {
      clusterSocketPool.getFactory().notifyHeartbeatStop();
    }

    /*
    if (! _heartbeatState.notifyHeartbeatStop()) {
      return false;
    }
    */
    
    log.fine("notify-heartbeat-stop " + this);

    return true;
  }
  
  public void updateHeartbeatTimeout(long timeout)
  {
    // _heartbeatState.updateTimeout(timeout);
  }
  
  void onHeartbeatTimeout()
  {
    if (_networkSystem.isActive())
      log.warning(this + " notify-heartbeat-timeout (check peer for possible freeze)");
    else
      log.fine(this + " notify-heartbeat-timeout (check peer for possible freeze)");
    
    // XXX: _serverBartender.onHeartbeatStop();
    
    // getServerPublisher().onServerStop(this);
  }

  /**
   * Starts the server.
   */
  public void stopServer()
  {
    // _heartbeatState.notifyHeartbeatStop();

    SocketPool pool = _clusterSocketPool.get();
    
    if (pool != null) {
      pool.getFactory().notifyHeartbeatStop();
    }
  }

  /**
   * Adds the primary/backup/third digits to the id.
   */
  public void generateIdPrefix(StringBuilder cb)
  {
    cb.append(getServerSessionId());
  }

  //
  // admin
  //

  /**
   * Returns the admin object
   */
  /*
  public ClusterServerMXBean getAdmin()
  {
    return _admin;
  }
  */

  /**
   * Close any ports.
   */
  public void close()
  {
    SocketPool loadBalancePool = _loadBalanceSocketPool.get();
    
    if (loadBalancePool != null)
      loadBalancePool.getFactory().close();
    
    SocketPool clusterPool = _clusterSocketPool.get();
    
    if (clusterPool != null)
      clusterPool.getFactory().close();
  }
  
  public static String getServerAddress(int index, int podIndex)
  {
    StringBuilder sb = new StringBuilder();
    
    sb.append("s");
    sb.append(index);
    sb.append(".p");
    sb.append(podIndex);
    
    return sb.toString();
  }
  
  public static String getSessionAddress(int index, int podIndex)
  {
    StringBuilder sb = new StringBuilder();
    
    sb.append(encode(index));
    sb.append(encode(podIndex));
    sb.append(encode(podIndex / 64));

    return sb.toString();
  }
  
  public static void generateSessionAddress(StringBuilder sb, 
                                            int index, 
                                            int podIndex)
  {
    sb.append(encode(index));
    sb.append(encode(podIndex));
    sb.append(encode(podIndex / 64));
  }
  
  private static char encode(int index)
  {
    index = index % 64;
    
    if (index < 26) {
      return (char) (index + 'a');
    }
    else if (index < 52) {
      return (char) (index - 26 + 'A');
    }
    else if (index < 62) {
      return (char) (index - 52 + '0');
    }
    else if (index == 62) {
      return (char) '+';
    }
    else {
      return (char) '-';
    }
  }

  @Override
  public String toString()
  {
    return (getClass().getSimpleName() + "[id=" + getId()
            + "," + getAddress() + ":" + getPort() + "]");
  }
  
  static class SocketPool {
    private final ClientSocketFactory _factory;
    
    SocketPool(ClientSocketFactory factory)
    {
      _factory = factory;
    }
    
    ClientSocketFactory getFactory()
    {
      return _factory;
    }
  }
}
