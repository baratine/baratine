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

package com.caucho.v5.bartender.heartbeat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.bartender.BartenderBuilder;
import com.caucho.v5.bartender.BartenderBuilderPod;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.BartenderSystemBare;
import com.caucho.v5.bartender.BartenderSystemFull;
import com.caucho.v5.bartender.ClusterBartender;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.bartender.pod.PodHeartbeatImpl;
import com.caucho.v5.bartender.pod.PodHeartbeatService;
import com.caucho.v5.bartender.pod.PodLocalImpl;
import com.caucho.v5.bartender.pod.PodLocalService;
import com.caucho.v5.bartender.pod.ServerPod;
import com.caucho.v5.bartender.pod.UpdatePod;
import com.caucho.v5.bartender.pod.UpdatePodBuilder;
import com.caucho.v5.io.SocketSystem;
import com.caucho.v5.util.L10N;

import io.baratine.config.Config;
import io.baratine.service.ServiceRef;


public class BartenderBuilderHeartbeat extends BartenderBuilder
{
  private static final L10N L = new L10N(BartenderBuilderHeartbeat.class);
  
  private final RootHeartbeat _root;

  private HeartbeatImpl _heartbeatImpl;
  
  private HashMap<String,PodBuilderConfig> _podMap = new HashMap<>();
  
  //private HashMap<String,PodConfig> _podConfigMap = new HashMap<>();

  private PodLocalImpl _podServiceImpl;

  private BartenderSystem _bartender;

  private PodHeartbeatService _podHeartbeat;

  private HeartbeatService _heartbeat;
  private HeartbeatServiceLocal _heartbeatLocal;

  //private PodManagerService _podManager;

  private PodLocalService _podService;

  private PodHeartbeatImpl _podHeartbeatImpl;

  private Config _config;
  
  /**
   * @param address self server address
   * @param portPublic self server port
   * @param cluster self server port
   * @param displayName self server display name
   * @param machinePort index used to calculate the machine hash
   */
  public BartenderBuilderHeartbeat(Config config)
  {
    Objects.requireNonNull(config);
    
    _config = config;
    
    String address = config.get("server.address", "127.0.0.1");
    int port = config.get("server.port", int.class, 8080);
    int machinePort = port;
    int portPublic = port;
    int portBartender = 0;
    
    boolean isSSL = false;
    String displayName = address + ":" + port;
    String cluster = config.get("server.cluster", "cluster");
    
    ServerHeartbeatBuilder selfBuilder = new ServerHeartbeatBuilder();
    
    String machineHash = createUniqueServerHash(machinePort);

    _root = new RootHeartbeat(address, portPublic, isSSL, portBartender,
                              cluster, displayName, 
                              machineHash,
                              selfBuilder);
  }
  
  /**
   * @param address self server address
   * @param portPublic self server port
   * @param cluster self server port
   * @param displayName self server display name
   * @param machinePort index used to calculate the machine hash
   */
  public BartenderBuilderHeartbeat(Config config, 
                                   String address,
                                   int portPublic,
                                   boolean isSSL,
                                   int portBartender,
                                   String cluster,
                                   String displayName,
                                   int machinePort,
                                   ServerHeartbeatBuilder selfBuilder)
  {
    Objects.requireNonNull(config);
    Objects.requireNonNull(address);
    Objects.requireNonNull(cluster);
    
    _config = config;
    
    String machineHash = createUniqueServerHash(machinePort);

    _root = new RootHeartbeat(address, portPublic, isSSL, portBartender,
                              cluster, displayName, 
                              machineHash,
                              selfBuilder);
  }
  
  /**
   * Create an external/configured server
   */
  @Override
  public ServerHeartbeat server(String address,
                                int port,
                                boolean isSSL,
                                String clusterId,
                                String displayName,
                                boolean isDynamic)
  {
    Objects.requireNonNull(address);
    Objects.requireNonNull(clusterId);
    
    if (address.isEmpty()) {
      address = "127.0.0.1";
    }

    /*
    if (port <= 0) {
      throw new IllegalArgumentException();
    }
    */
    
    ClusterHeartbeat cluster = createCluster(clusterId);
    
    ServerHeartbeat server;
    
    if (isDynamic) {
      server = cluster.createDynamicServer(address, port, isSSL);
    }
    else {
      server = cluster.createServer(address, port, isSSL);
    }
    
    if (displayName != null) {
      server.setDisplayName(displayName);
    }
    
    if (! isDynamic) {
      cluster.addSeedServer(server);
    }
    
    return server;
  }
  
  /**
   * Create an external/configured server
   */
  @Override
  public ServerHeartbeat serverDyn(String address,
                                   int port,
                                   boolean isSSL,
                                   String clusterId,
                                   String displayName)
  {
    Objects.requireNonNull(address);
    Objects.requireNonNull(clusterId);

    /*
    if (port <= 0) {
      throw new IllegalArgumentException();
    }
    */
    
    ClusterHeartbeat cluster = createCluster(clusterId);
    
    ServerHeartbeat server = cluster.createServer(address, port, isSSL);
    
    if (displayName != null) {
      server.setDisplayName(displayName);
    }
    
    cluster.addDynamicServer(server);
    
    return server;
  }
  
  /**
   * Create a configured pod.
   */
  @Override
  public BartenderBuilderPod pod(String id, String clusterId)
  {
    Objects.requireNonNull(id);
    Objects.requireNonNull(clusterId);
    
    ClusterHeartbeat cluster = createCluster(clusterId);

    return new PodBuilderConfig(id, cluster, this);
  }

  /*
  /**
   * Creates the self server.
   */
  /*
  @Override
  public void serverSelf(String address,
                         int port,
                         String clusterId,
                         String displayName)
  {
    Objects.requireNonNull(address);
    Objects.requireNonNull(clusterId);
    
    ClusterHeartbeat cluster = createCluster(clusterId);
  }
  */
  
  private ClusterHeartbeat createCluster(String name)
  {
    if (name.equals("")) {
      throw new IllegalArgumentException();
    }
    
    return _root.createCluster(name);
  }

  @Override
  public ClusterBartender cluster(String name)
  {
    return _root.createCluster(name);
  }

  @Override
  public ClusterBartender getCluster(String clusterId)
  {
    return _root.findCluster(clusterId);
  }

  /* (non-Javadoc)
   * @see com.caucho.bartender.BartenderBuilder#getServerSelf()
   */
  @Override
  protected ServerSelf getServerSelf()
  {
    return getRoot().getServerSelf();
  }

  @Override
  protected RootHeartbeat getRoot()
  {
    return _root;
  }

  @Override
  protected HeartbeatService getHeartbeat()
  {
    Objects.requireNonNull(_heartbeat);
    
    return _heartbeat; 
  }

  @Override
  protected HeartbeatServiceLocal getHeartbeatLocal()
  {
    return _heartbeatLocal;
  }

  @Override
  public PodHeartbeatService getPodHeartbeat()
  {
    Objects.requireNonNull(_podHeartbeat);
    
    return _podHeartbeat; 
  }

  @Override
  public PodLocalService getPodService()
  {
    Objects.requireNonNull(_podService);
    
    return _podService; 
  }

  /*
  @Override
  public PodManagerService getPodManager()
  {
    Objects.requireNonNull(_podManager);
    
    return _podManager; 
  }
  */

  /*
  @Override
  protected JoinClient buildJoinClient()
  {
    return new JoinClient(getServerSelf(), getRoot());
  }
  */

  void addPod(PodBuilderConfig pod)
  {
    String podName = pod.getId() + "." + pod.getCluster().id();
    
    if (_podMap.get(podName) != null) {
      throw new IllegalStateException(L.l("Duplicate pod '{0}'", pod.getId()));
    }
    
    _podMap.put(podName, pod);
  }

  /*
  void addPod(PodConfig pod)
  {
    String podName = pod.getName();
    
    if (_podConfigMap.get(podName) != null) {
      throw new IllegalStateException(L.l("Duplicate pod '{0}'", pod.getId()));
    }
    
    _podConfigMap.put(podName, pod);
  }
  */
  
  public Iterable<PodBuilderConfig> getPods()
  {
    return _podMap.values();
  }
  
  @Override
  public BartenderSystem build()
  {
    if (getServerSelf() == null) {
      throw new IllegalStateException(L.l("serverSelf is required"));
    }
    
    if (_config.get("bartender.seed.servers") != null) {
      return BartenderSystemFull.createAndAddSystem(this);
    }
    else {
      return BartenderSystemBare.createAndAddSystem(this);
    }
  }
  
  @Override
  protected void build(BartenderSystem bartender)
  {
    if (_bartender != null) {
      throw new IllegalStateException();
    }
    
    _bartender = bartender;
    
    ServicesAmp manager = AmpSystem.currentManager();
    
    manager.service("local://").bind("public:///cluster_root.0");
    
    _heartbeatImpl = new HeartbeatImpl(_bartender,
                                              getServerSelf(), 
                                              getRoot(), 
                                              this);
    
    ServiceRef heartbeatRef = manager.newService(_heartbeatImpl)
                                     .address("public://" + HeartbeatService.PATH)
                                     .ref();
    
    HeartbeatLocalImpl heartbeatLocalImpl = new HeartbeatLocalImpl(_bartender,
                                                  _heartbeatImpl);
    
    _heartbeat = heartbeatRef.as(HeartbeatService.class);
    
    _heartbeatLocal = heartbeatRef.pin(heartbeatLocalImpl)
                                  .as(HeartbeatServiceLocal.class);
    
    _podHeartbeatImpl = new PodHeartbeatImpl(_bartender,
                                             getServerSelf(),
                                             _heartbeat,
                                             _heartbeatLocal);
    
    ServiceRefAmp podHeartbeatRef = manager.newService(_podHeartbeatImpl)
                                           .address(PodHeartbeatService.PATH)
                                           .ref();
    
    _podHeartbeat = podHeartbeatRef.as(PodHeartbeatService.class);
    
    /*
    PodManagerImpl podManagerImpl = _podHeartbeatImpl.getManager();
    
    _podManager = podHeartbeatRef.service(podManagerImpl)
                                 .bind("public://" + PodManagerService.PATH)
                                 .as(PodManagerService.class);
                                 */
    
    PodLocalImpl podServiceImpl;
    podServiceImpl = new PodLocalImpl(_bartender, _podHeartbeatImpl);
    
    _podService = podHeartbeatRef.pin(podServiceImpl)
                                 .bind(PodLocalService.PATH)
                                 .as(PodLocalService.class);
  }
  
  @Override
  public void buildPods()
  {
    buildPods(_podHeartbeatImpl);
  }
  
  //
  // initialize predefined pods: the cluster and the cluster hub
  // for each cluster.
  //

  private void buildPods(PodHeartbeatImpl podHeartbeatImpl)
  {
    ServerHeartbeat serverSelf = (ServerHeartbeat) _bartender.serverSelf();
    
    for (PodBuilderConfig pod : _podMap.values()) {
      UpdatePod updatePod = initBuilderPod(pod);
      
      podHeartbeatImpl.initPod(updatePod);
    }
    
    ClusterHeartbeat cluster = serverSelf.getCluster();
    
    podHeartbeatImpl.initPod(initClusterPodHub(cluster));
    podHeartbeatImpl.initPod(initClusterPod(cluster));
    podHeartbeatImpl.initPod(initLocalPod());
    
    /*
    for (PodConfig podConfig : _podConfigMap.values()) {
      podHeartbeatImpl.initPodConfig(podConfig);
    }
    */
  }
  
  public Map<String,PodBartender> podMap() // asdf
  {
    return _podHeartbeatImpl.podMap();
  }
  
  private UpdatePod initBuilderPod(PodBuilderConfig podConfig)
  {
    UpdatePodBuilder podBuilder = new UpdatePodBuilder();
    
    podBuilder.name(podConfig.getId());
    podBuilder.cluster(podConfig.getCluster());
    
    ArrayList<ServerBartender> serverList = podConfig.getServers();
    
    /*
    int depth = Math.min(3, serverList.size());
    
    if (depth <= 0) {
      depth = 3;
    }
    */
    int depth = 3;
    
    podBuilder.depth(depth);
    
    ServerPod[] servers = null;
    
    switch (podConfig.getType()) {
    case solo:
    case lazy:
      podBuilder.primaryCount(1);
      servers = buildClusterServers(podConfig.getCluster(), depth);
      break;
      
    case web:
      podBuilder.primaryCount(1);
      servers = buildClusterServers(podConfig.getCluster(), depth);
      break;
      
    case pair:
      podBuilder.primaryCount(2);
      servers = buildClusterServers(podConfig.getCluster(), depth);
      break;
      
    case triad:
      podBuilder.primaryCount(3);
      servers = buildClusterServers(podConfig.getCluster(), depth);
      break;
      
    case cluster:
      podBuilder.primaryCount(Math.max(3, serverList.size()));
      servers = buildClusterServers(podConfig.getCluster(), 
                                    Math.max(serverList.size(), depth));
      break;
      
    default:
      throw new IllegalStateException(String.valueOf(podConfig.getType()));
    }
    
    int len = Math.min(serverList.size(), servers.length);
    
    podBuilder.type(podConfig.getType());
    podBuilder.pod(servers);
    
    return podBuilder.build();
  }
  
  private UpdatePod initClusterPodHub(ClusterHeartbeat cluster)
  {
    //ServiceManagerAmp rampManager = AmpSystem.currentManager();
    
    UpdatePodBuilder podBuilder = new UpdatePodBuilder();
  
    podBuilder.name("cluster_hub");
    podBuilder.cluster(cluster);

    // int count = Math.min(3, rack.getServerLength());
    
    ServerPod[] servers = buildClusterServers(cluster, 3);

    //PodBuilderConfig podCluster = _podMap.get(cluster.getId());
    
    // System.out.println("PC: " + cluster + " " + cluster.getId());
    
    //if (podCluster != null) {
    //ArrayList<ServerBartender> serversHint = podCluster.getServers();
    // List<ServerHeartbeat> serversHint = cluster.getSeedServers();
    
    int index = 0;
    boolean isFullHint = false;
      
    /*
    for (ServerHeartbeat server : cluster.getSeedServers()) {
        // ServerPod server = servers[i];

      ServerBartender serverCluster = server; // serversHint.get(i);

      if (servers.length <= index) {
      }
      else if (server.isDynamic()) {
      }
      else if (! serverCluster.getId().startsWith("127")) {
        //System.out.println("ZOHP: " + serverCluster.getAddress());
        //server.setHintServerId(serverCluster.getHintServerId());
        servers[index++].setHintServerId(serverCluster.getId());
        isFullHint = true;
      }
      else if (! isFullHint) {
        servers[index++].setHintPort(serverCluster.getPort());
      }
    }
    //}
     */
    
    podBuilder.pod(servers);
  
    // int depth = Math.min(3, handles.length);
    podBuilder.primaryCount(3);
    podBuilder.depth(3);
  
    UpdatePod update = podBuilder.build();
    
    return update;
  }
  
  private UpdatePod initClusterPod(ClusterHeartbeat cluster)
  {
    ServicesAmp rampManager = AmpSystem.currentManager();
    
    RackHeartbeat rack = cluster.findRack("rack");
    
    if (rack == null) {
      return null;
    }
    
    String podName = "cluster." + cluster.id();
    
    PodBuilderConfig podConfig = _podMap.get(podName);
    
    if (podConfig != null) {
      return null;
    }

    UpdatePodBuilder podBuilder = new UpdatePodBuilder();
  
    podBuilder.name("cluster");
    podBuilder.cluster(cluster);

    List<ServerHeartbeat> seedServers = cluster.getServers();
    
    int count = Math.max(3, seedServers.size());
    
    ServerPod[] servers = buildClusterServers(cluster, count);
    
    
    int index = 0;
      
    /*
      for (ServerHeartbeat server : cluster.getSeedServers()) {
        // ServerPod server = servers[i];

        ServerBartender serverCluster = server; // serversHint.get(i);
        
        if (index < servers.length && ! serverCluster.getId().startsWith("127")) {
          //System.out.println("ZOHP: " + serverCluster.getAddress());
          //server.setHintServerId(serverCluster.getHintServerId());
          servers[index++].setHintServerId(serverCluster.getId());
        }
      }
      */
      /*
    for (int i = 0; i < servers.length; i++) {
      ServerPod server = servers[i];
      
      if (i < seedServers.size()) {
        String hintId = seedServers.get(i).getId();
        
        if (! hintId.startsWith("127")) {
          server.setHintServerId(hintId);
        }
      }
    }
    */
    
    podBuilder.pod(servers);
  
    podBuilder.depth(3);
    
    podBuilder.primaryCount(count);
    
    return podBuilder.build();
  }
  
  /**
   * The local pod refers to the server itself.
   */
  private UpdatePod initLocalPod()
  {
    ServerBartender serverSelf = _bartender.serverSelf();
    
    ServicesAmp rampManager = AmpSystem.currentManager();
    
    UpdatePodBuilder podBuilder = new UpdatePodBuilder();
  
    podBuilder.name("local");
    podBuilder.cluster(_bartender.serverSelf().getCluster());

    // int count = Math.min(3, rack.getServerLength());
    
    ServerPod serverPod = new ServerPod(0, serverSelf);
    
    ServerPod[] servers = new ServerPod[] { serverPod };
    
    podBuilder.pod(servers);
  
    // int depth = Math.min(3, handles.length);
    podBuilder.primaryCount(1);
    podBuilder.depth(1);
  
    UpdatePod updatePod = podBuilder.build();
    
    return new UpdatePod(updatePod, 
                         new String[] { serverSelf.getId() },
                         0);
  }
  
  /**
   * Create cluster pods using the configuration as a hint. Both the cluster
   * and cluster_hub pods use this.
   */
  private ServerPod []buildClusterServers(ClusterHeartbeat cluster,
                                          int serverCount)
  {
    ArrayList<ServerPod> serversPod = new ArrayList<>();
    
    for (ServerHeartbeat server : cluster.getServers()) {
      ServerPod serverPod = new ServerPod(serversPod.size());
      serversPod.add(serverPod);
      
      // XXX: need to manage seed servers
      // serverPod.setHintServerId(server.getId());
      
      // XXX:
      serverPod.setServer(server);
    }
    
    /*
    if (cluster == _serverSelf.getCluster()) {
      if (serversPod.size() < serverCount && ! isServerPresent(serversPod)) {
        ServerPod serverPod = new ServerPod(serversPod.size());
        serversPod.add(serverPod);
      
        serverPod.setServer(_serverSelf);
      }
    }
    */
    
    while (serversPod.size() < serverCount) {
      serversPod.add(new ServerPod(serversPod.size()));
    }
    
    ServerPod []serverArray = new ServerPod[serverCount];
    for (int i = 0; i < serverCount; i++) {
      serverArray[i] = serversPod.get(i);
    }
    
    return serverArray;
  }
  
  /**
   * XXX: issue is server starting by itself when it's not a seed server.
   */
  /*
  private boolean isServerPresent(ArrayList<ServerPod> serversPod)
  {
    ServerBartender serverSelf = _bartender.getServerSelf();
    
    for (ServerPod serverPod : serversPod) {
      ServerBartender server = serverPod.getServer();
      
      if (server != null && server.getPort() == serverSelf.getPort()) {
        return true;
      }

      String hintServer = serverPod.getHint().getServer();
      
      if (hintServer == null) {
        continue;
      }
      
      int p = hintServer.lastIndexOf(':');
      int port = Integer.parseInt(hintServer.substring(p + 1));
      
      if (port == serverSelf.getPort()) {
        return true;
      }
    }
    
    return false;
  }
  */

  private String createUniqueServerHash(int port)
  {
    StringBuilder sb = new StringBuilder();
    
    //long hash = 0;
    
    //hash = Crc64.generate(hash, port);
    
    byte []mac = getHardwareAddress();
    
    if (mac != null) {
      for (int i = 0; i < mac.length; i++) {
        if (sb.length() > 0) {
          sb.append(":");
        }
        sb.append(Integer.toHexString(mac[i] & 0xff));
      }
    }
    
    sb.append(":").append(port);
    
    return sb.toString();
  }
  
  private byte []getHardwareAddress()
  {
    return SocketSystem.current().getHardwareAddress();
  }
  
  /*
  public class PodBuilder {
    private String _id;
    private Type _type;
    private ClusterHeartbeat _cluster;
    private ArrayList<ServerBartender> _serverList;
    
    PodBuilder(String id,
               Type type,
               ClusterHeartbeat cluster,
               ArrayList<ServerBartender> serverList)
    {
      _id = id;
      _type = type;
      _cluster = cluster;
      _serverList = serverList;
    }
  }
  */
}
