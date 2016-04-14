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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.websocket.ProtocolBartender;
import com.caucho.v5.http.protocol.ProtocolHttp;
import com.caucho.v5.io.SocketSystem;
import com.caucho.v5.network.NetworkSystemBartender;
import com.caucho.v5.network.port.PortTcp;
import com.caucho.v5.network.port.PortTcpBuilder;
import com.caucho.v5.util.L10N;

import io.baratine.config.Config;
import io.baratine.service.Service;
import io.baratine.web.ServiceWebSocket;

/**
 * Server to manager the cluster network code.
 */
@Service
public class NetworkBartenderServiceImpl
{
  private static final L10N L = new L10N(NetworkBartenderServiceImpl.class);
  private static final Logger log = 
    Logger.getLogger(NetworkBartenderServiceImpl.class.getName());
  
  private static final long CLUSTER_IDLE_TIME_MAX = Long.MAX_VALUE / 2;
  private static final long CLUSTER_IDLE_PADDING = 5 * 60000;
  
  private final NetworkSystemBartender _system;

  private final ServerBartender _selfServer;
  
  //private RootConfigBoot _config;
  
  private final ConcurrentHashMap<String,ServerNetwork> _serverMap
    = new ConcurrentHashMap<>();
  
  private PortTcp _portPublic;
  private PortTcp _portBartender;
  
  // private HmuxProtocol _hmuxProtocol;
  private ProtocolHttp _httpProtocol;
  private ProtocolHttp _httpProtocolBartender;
  
  private ProtocolBartender _protocolBartender;
  private ProtocolBartender _protocolBartenderPublic;
  
  //private ClusterServerEvents _clusterServerPublisher;
  //private ServerNetworkLinkEvents _clusterLinkPublisher;
  
  //private ContainerProgram _bartenderPortConfig = new ContainerProgram();

  private Config _config;
  //private ServerConfigBoot _serverConfig;

  // private ClusterServer _selfServerCluster;

  public NetworkBartenderServiceImpl(NetworkSystemBartender system,
                                   ServerBartender selfServer,
                                   Config config)
  {
    _system = system;
    
    _selfServer = selfServer;
    //_serverConfig = serverConfig;
    // _selfServer.setSelf(true);

    /*
    if (serverConfig != null) {
      _config = serverConfig.getRoot();
    }
    _args = args;
    */
    
    _config = config;
    
    _protocolBartender = new ProtocolBartender();
    _protocolBartenderPublic = new ProtocolBartender();
    
    configureServer(selfServer);
  }
  
  /**
   * Returns the self server for the network.
   */
  public ServerBartender getSelfServer()
  {
    return _selfServer;
  }
  
  /**
   * Returns the cluster port.
   */
  public PortTcp getClusterPort()
  {
    return _portPublic;
  }
  
  /*
  public void addClusterPortConfig(ConfigProgram program)
  {
    _bartenderPortConfig.addProgram(program);
  }
  */

  public boolean start()
    throws Exception
  {
    // ServerNetwork clusterServer = getServerNetwork(_selfServer);
    
    if (_selfServer.port() >= 0) {
      // _hmuxProtocol = HmuxProtocol.create();
      _httpProtocol = new ProtocolHttp();
      _httpProtocolBartender = new ProtocolHttp();

      _httpProtocol.extensionProtocol(_protocolBartenderPublic);
      _httpProtocolBartender.extensionProtocol(_protocolBartender);
      
      ProtocolHttp protocolPublic = _httpProtocol;
      
      /*
      if (_serverConfig.getPortBartender() < 0) {
        protocolPublic = _httpProtocolBartender;
      }
      */
      
      Config config = _config;
      
      PortTcpBuilder portBuilder = new PortTcpBuilder(config);
      portBuilder.portName("server");
      //portBuilder.serverSocket(_serverConfig.getServerSocket());
      portBuilder.protocol(protocolPublic);
      portBuilder.ampManager(AmpSystem.currentManager());
      
      // XXX: config timing issues, see network/0330, 
      // NetworkServerConfig.getClusterIdleTime()
      
      _portPublic = new TcpPortBartender(portBuilder, _selfServer);
      portBuilder.portDefault(8080);
        
      if (config.get("bartender.port", int.class, 0) >= 0) {
        portBuilder = new PortTcpBuilder(config);
        portBuilder.portName("bartender");
        //portBuilder.serverSocket(_serverConfig.getSocketBartender());
        portBuilder.protocol(_httpProtocolBartender);
        //portBuilder.portDefault(_serverConfig.getPortBartender());
        portBuilder.ampManager(AmpSystem.currentManager());
          
        _portBartender = new TcpPortBartender(portBuilder, _selfServer);
      }
      
      /*
      _bartenderPortConfig.configure(_portPublic);
      _serverConfig.configurePort(_portPublic);
      
      if (_portBartender != null) {
        _bartenderPortConfig.configure(_portBartender);
        
        _serverConfig.configurePort(_portBartender);
      }
      */
    }
    
    startClusterPort();
    
    // validateHub(_selfServer.getPod());
    
    return true;
  }

  public void serviceBartender(String path, Supplier<ServiceWebSocket> serviceFactory)
  {
    _protocolBartender.publish(path, serviceFactory);
  }

  public void servicePublic(String path, Supplier<ServiceWebSocket> serviceFactory)
  {
    _protocolBartenderPublic.publish(path, serviceFactory);
  }

  /**
   * Closes the server.
   */
  public boolean stop()
    throws Exception
  {
    /*
    if (_clusterServerPublisher != null) {
      _clusterServerPublisher.onServerStop(_selfServer.getServerBartender());
    }
    */
    
    try {
      if (_portPublic != null) {
        _portPublic.close();
      }
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
    }
    
    try {
      if (_portBartender != null) {
        _portBartender.close();
      }
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
    }
    
    return true;
  }

  private ServerNetwork getServerNetwork(ServerBartender selfServer)
  {
    return _serverMap.get(selfServer.getId());
  }
  
  /**
   * Configures the server.
   */
  private void configureServer(ServerBartender server)
  {
    // XXX: mixing server/client
    
    ServerNetwork serverNet = _serverMap.get(server.getId());
    
    if (serverNet == null) {
      serverNet = new ServerNetwork(_system, server);
      
      configServer(serverNet, server);

      serverNet.init();
      
      _serverMap.put(server.getId(), serverNet);
    }
  }
  
  private void configServer(Object bean, ServerBartender server)
  {
    //RootConfigBoot config = _config;
    /*
    if (config != null) {
      // XXX: config.configServer(bean, serverSelf);
    }
    */
    
    // _config.configServer(server, cloudServer);
  }

  /**
   * Start the cluster port
   */
  private void startClusterPort()
    throws Exception
  {
    bindPort(_portPublic);
    bindPort(_portBartender);
  }
  
  private void bindPort(PortTcp port)
    throws Exception
  {
    if (port == null) {
      return;
    }
    
    ServerNetwork serverNet = getServerNetwork(_selfServer);
    
    // long idleTime = serverNet.getClusterIdleTime() + CLUSTER_IDLE_PADDING;
    long idleTime = TimeUnit.SECONDS.toMillis(61) + CLUSTER_IDLE_PADDING;

    /*
    if (_serverConfig.getClusterPortTimeout() > 0) {
      idleTime = _serverConfig.getClusterPortTimeout();
    }
    */
    
    /* XXX:
    port.setKeepaliveConnectionTimeMaxMillis(CLUSTER_IDLE_TIME_MAX);
    port.setKeepaliveTimeoutMillis(idleTime);
    
    // port.setSocketTimeoutMillis(serverNet.getClusterSocketTimeout());
    port.setSocketTimeoutMillis(idleTime);
    */
    
    // port.setProtocol(_httpProtocol);
    //port.init();
    
    validateClusterServer(port, serverNet);
    
    port.bind();
    port.start();
    
    // XXX:
    //if (serverNet.getPort() == 0) {
    //  serverNet.setPort(port.getLocalPort());
    //}
  }

  private void validateClusterServer(PortTcp listener,
                                     ServerNetwork server)
  {
    if (listener == null || server == null)
      return;
    

    /*
    if (listener.getSocketTimeout() <= server.getLoadBalanceIdleTime()) {
      throw new ConfigException(L.l("{0}: load-balance-idle-time {1} must be less than socket-timeout {2}",
                                    server, 
                                    server.getLoadBalanceIdleTime(),
                                    listener.getSocketTimeout()));
    }
    */

    // server/26r0
    /*
    if (server.getLoadBalanceSocketTimeout() <= listener.getSocketTimeout()) {
      throw new ConfigException(L.l("{0}: load-balance-socket-timeout {1} must be greater than socket-timeout {2}",
                                    server, 
                                    server.getLoadBalanceSocketTimeout(),
                                    listener.getSocketTimeout()));
    }
    */

    /*
    if (listener.getKeepaliveTimeout() <= server.getLoadBalanceIdleTime()) {
      throw new ConfigException(L.l("{0}: load-balance-idle-time {1} must be less than keepalive-timeout {2}",
                                    server, 
                                    server.getLoadBalanceIdleTime(),
                                    listener.getKeepaliveTimeout()));
    }
    */
  }
  
  public static ArrayList<InetAddress> getLocalAddresses()
  {
    return SocketSystem.current().getLocalAddresses();
  }

  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + _portPublic + "]");
  }
}
