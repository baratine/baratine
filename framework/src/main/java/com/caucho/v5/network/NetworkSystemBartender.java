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

package com.caucho.v5.network;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.link.LinkBartenderSystem;
import com.caucho.v5.bartender.network.NetworkBartenderService;
import com.caucho.v5.bartender.network.NetworkBartenderServiceImpl;
import com.caucho.v5.bartender.network.ServerNetwork;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.io.ServerSocketBar;
import com.caucho.v5.io.SocketSystem;
import com.caucho.v5.jni.SelectManagerJni;
import com.caucho.v5.network.port.ConnectionTcp;
import com.caucho.v5.network.port.PollTcpManagerBase;
import com.caucho.v5.network.port.PortTcp;
import com.caucho.v5.subsystem.SubSystemBase;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.util.Alarm;
import com.caucho.v5.util.AlarmListener;
import com.caucho.v5.util.L10N;

import io.baratine.config.Config;
import io.baratine.web.ServiceWebSocket;

/**
 * NetworkClusterService manages the cluster network code, the communication
 * between Resin servers in a cluster. 
 */
public class NetworkSystemBartender extends NetworkSystem
{
  private static final String UID = "/network-address";
  private static final Logger log
    = Logger.getLogger(NetworkSystemBartender.class.getName());
  
  public static final int START_PRIORITY = START_PRIORITY_NETWORK;
  private static final long ALARM_TIMEOUT = 120 * 1000L;
  
  private static final L10N L = new L10N(NetworkSystemBartender.class);
  
  private final ServerBartender _selfServer;
  
  //private RootConfigBoot _configRoot;

  private NetworkBartenderServiceImpl _clusterActor;

  private NetworkBartenderService _clusterService;

  private ServerNetwork _selfServerCluster;
  
  private final ArrayList<PortTcp> _ports
    = new ArrayList<>();
    
  private SelectManagerJni _pollManager;

  private Alarm _alarm;
  // private SocketSystem _socketSystem;
  
  // private NetworkSystem _clusterService;
  
  private boolean _isAllowForeignIp;

  public NetworkSystemBartender(SystemManager systemManager,
                       ServerBartender selfServer,
                       Config config)
  {
    super(systemManager, selfServer, config);
    
    Objects.requireNonNull(selfServer);
    
    _selfServer = selfServer;
    
    ServicesAmp manager = AmpSystem.currentManager();
    
    _clusterActor = new NetworkBartenderServiceImpl(this, 
                                            _selfServer,
                                            config);
    _clusterService = manager.newService(_clusterActor)
                             .as(NetworkBartenderService.class);

    //_socketSystem = SocketSystem.getCurrent();
    // XXX: SocketSystem.createSubSystem(selfServer.getId());
    
    // XXX: ordering
    // configureServer(selfServer);
    
    /*
    _configRoot = serverConfig.getRoot();
    // _cloudServer = serverBartender;

    ServerNetworkConfig networkConfig
      = new ServerNetworkConfig(this, serverConfig);
    
    if (serverConfig != null) {
      serverConfig.configure(networkConfig);
    }
    */
    
    LinkBartenderSystem champSystem
      = SystemManager.getCurrentSystem(LinkBartenderSystem.class);

    if (champSystem != null) {
      champSystem.initNetworkService(this);
    }
  }

  /**
   * Creates a new network cluster service.
   */
  public static NetworkSystemBartender
  createAndAddSystem(SystemManager systemManager,
                     ServerBartender selfServer,
                     Config config)
  {
    NetworkSystemBartender clusterSystem
      = new NetworkSystemBartender(systemManager, selfServer, config);
    
    createAndAddSystem(clusterSystem);
    
    return clusterSystem;
  }

  public static void
  createAndAddSystem(NetworkSystemBartender clusterSystem)
  {
    SystemManager resinSystem = preCreate(NetworkSystemBartender.class);

    resinSystem.addSystem(NetworkSystemBartender.class, clusterSystem);
  }

  /**
   * Returns the current network service.
   */
  public static NetworkSystemBartender current()
  {
    return SystemManager.getCurrentSystem(NetworkSystemBartender.class);
  }
  
  /**
   * Returns the current network service.
   */
  public static ServerBartender currentSelfServer()
  {
    NetworkSystemBartender clusterService = current();
    
    if (clusterService == null)
      throw new IllegalStateException(L.l("{0} is not available in this context",
                                          NetworkSystemBartender.class.getSimpleName()));
    
    return clusterService.selfServer();
  }
  
  /**
   * Returns the self server for the network.
   */
  public ServerBartender selfServer()
  {
    return _selfServer;
  }

  public ServerNetwork getSelfServerCluster()
  {
    return _selfServerCluster;
  }
  
  /*
  public RootConfigBoot getConfig()
  {
    return _configRoot;
  }
  */
  
  /**
   * Returns the active server id.
   */
  public String serverId()
  {
    return selfServer().getId();
  }
  
  /**
   * Returns the cluster port.
   */
  public PortTcp clusterPort()
  {
    return _clusterActor.getClusterPort();
  }

  /*
  public void addClusterPortConfig(ConfigProgram program)
  {
    _clusterActor.addClusterPortConfig(program);
  }
  */
  
  void setAllowForeignIp(boolean isAllow)
  {
    _isAllowForeignIp = isAllow;
  }
  
  // XXX: change to a netmask
  public boolean isAllowForeignIp()
  {
    return _isAllowForeignIp;
  }

  public boolean isCluster()
  {
    return true;
  }

  //
  // listeners
  //
  
  public void serviceBartender(String path, Supplier<ServiceWebSocket> serviceFactory)
  {
    //_clusterActor.publishBartender(WebSocketSessionFactory.create(config));
    _clusterActor.serviceBartender(path, serviceFactory);
  }
  
  public void servicePublic(String path, Supplier<ServiceWebSocket> serviceFactory)
  {
    //_clusterActor.publishBartender(WebSocketSessionFactory.create(config));
    _clusterActor.servicePublic(path, serviceFactory);
  }
  
  /*
  public void publishPublic(ServerEndpointConfig config)
  {
    _clusterActor.publishPublic(WebSocketSessionFactory.create(config));
  }
  */
  
  //
  // lifecycle
  //

  @Override
  public int getStartPriority()
  {
    return START_PRIORITY;
  }

  @Override
  public void start()
    throws Exception
  {
    super.start();
    
    _clusterService.start();
    boolean isFirst = true;
    
    _pollManager = SelectManagerJni.create();

    for (PortTcp port : _ports) {
      if (isFirst) {
        log.info("");
      }

      isFirst = false;

      port.bind();
      
      // XXX: delay start until after kraken
      port.start();
    }

    if (! isFirst) {
      log.info("");
    }
    
    _alarm = new Alarm(new NetworkAlarmListener());
    _alarm.runAfter(ALARM_TIMEOUT);
  }

  /**
   * Closes the server.
   */
  @Override
  public void stop(ShutdownModeAmp mode)
    throws Exception
  {
    super.stop(mode);
    
    Alarm alarm = _alarm;
    _alarm = null;

    if (alarm != null) {
      alarm.dequeue();
    }

    for (PortTcp port : _ports) {
      try {
        port.close();
      } catch (Throwable e) {
        log.log(Level.WARNING, e.toString(), e);
      }
    }
    
    _clusterService.stop();
  }

  public ServerNetwork getServer(ServerBartender serverBartender)
  {
    return null;
  }
  
  public static ArrayList<InetAddress> getLocalAddresses()
  {
    return SocketSystem.current().getLocalAddresses();
  }

  @Override
  public void addPort(PortTcp port)
  {
    try {
      if (! _ports.contains(port)) {
        _ports.add(port);
      }
      else {
        System.err.println("Duplicate port: " + _ports + " " + port); 
        return;
        // throw new ConfigException(L.l("duplicate port {0}", port));
      }
      
      if (isActive()) {
        // server/1e00
        port.bind();
        port.start();
      }
    } catch (Exception e) {
      throw ConfigException.wrap(e);
    }
  }

  /**
   * Returns the {@link PortTcp}s for this server.
   */
  public Collection<PortTcp> getPorts()
  {
    return Collections.unmodifiableList(_ports);
  }

  public void bind(String address, int port, ServerSocketBar ss)
    throws IOException
  {
    if ("null".equals(address)) {
      address = null;
    }

    for (int i = 0; i < _ports.size(); i++) {
      PortTcp serverPort = _ports.get(i);

      if (port != serverPort.port()) {
        continue;
      }

      if ((address == null) != (serverPort.address() == null)) {
        continue;
      }
      else if (address == null || address.equals(serverPort.address())) {
        serverPort.bind(ss);

        return;
      }
    }

    throw new IllegalStateException(L.l("No matching port for {0}:{1}",
                                        address, port));
  }

  /**
   * Finds the TcpConnection given the threadId
   */
  public ConnectionTcp findConnectionByThreadId(long threadId)
  {
    for (PortTcp listener : getPorts()) {
      ConnectionTcp conn = listener.findConnectionByThreadId(threadId);

      if (conn != null)
        return conn;
    }

    return null;
  }
  
  //
  // lifecycle
  //

  public static PollTcpManagerBase currentPollManager()
  {
    NetworkSystemBartender system = current();
    
    if (system != null) {
      return system.getPollManager();
    }
    else {
      return null;
    }
  }

  public PollTcpManagerBase getPollManager()
  {
    if (_pollManager == null) {
      _pollManager = SelectManagerJni.create();
    }
    
    return _pollManager;
  }

  /**
   * Handles the alarm.
   */
  private class NetworkAlarmListener implements AlarmListener {
    @Override
    public void handleAlarm(Alarm alarm)
    {
      try {
        for (PortTcp listener : _ports) {
          if (listener.isClosed()) {
            log.severe("Restarting due to closed listener: " + listener);
            // destroy();
            //_controller.restart();
          }
        }
      } catch (Throwable e) {
        log.log(Level.WARNING, e.toString(), e);
        // destroy();
        //_controller.restart();
      } finally {
        alarm = _alarm;

        if (alarm != null)
          alarm.runAfter(ALARM_TIMEOUT);
      }
    }
  }

  /**
   * Closes the server.
   */
  /*
  @Override
  public void stop(ShutdownMode mode)
  {
  }
  */

  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + clusterPort() + "]");
  }
}
