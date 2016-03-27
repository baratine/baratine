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

package com.caucho.v5.bartender.pod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.Direct;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.baratine.InService;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.heartbeat.ClusterHeartbeat;
import com.caucho.v5.bartender.heartbeat.HeartbeatImpl;
import com.caucho.v5.bartender.heartbeat.HeartbeatServiceLocal;
import com.caucho.v5.bartender.heartbeat.RootHeartbeat;
import com.caucho.v5.bartender.heartbeat.ServerHeartbeat;
import com.caucho.v5.lifecycle.Lifecycle;

import io.baratine.service.Result;

@InService(HeartbeatServiceLocal.class)
public class PodLocalImpl
{
  private static final Logger log = Logger.getLogger(PodLocalImpl.class.getName());
  
  // private final BartenderSystem _system;
  
  //private final BartenderServiceImpl _bartender;
  
//  private final HashMap<String,PodBartenderImpl> _podMap = new HashMap<>();
//  private final HashMap<String,PodBartenderProxy> _proxyMap = new HashMap<>();
  
  // private final HashMap<String,PodCluster> _podClusterMap = new HashMap<>();
  
  private final HashMap<String,FailoverNode> _failoverMap = new HashMap<>();
  
  private HeartbeatImpl _heartbeatService;
  
  //private ShardWatch _watch;
  //private FileService _path;
  //private ArrayList<String> _pathPodList = new ArrayList<>();
  
  // private PodOnUpdate _podEvents;

  private ServerHeartbeat _serverSelf;

  private RootHeartbeat _root;

  private UpdatePodSystem _updatePodSystem;
  
  private int _roundRobinPodGenerator;

  private PodLocalService _podServiceManager;

  private BartenderSystem _bartender;

  private FailoverEvents _failoverEvents;

  private long _lastRootSequence;

  private PodHeartbeatImpl _podHeartbeat;

  // private PodManagerImpl _podManager;
  
  private final Lifecycle _lifecycle = new Lifecycle();

  // private List<ServerBartender> _seedServers;
  
  public PodLocalImpl()
  {
    _heartbeatService = null;
  }
  
  public PodLocalImpl(BartenderSystem bartender,
                      PodHeartbeatImpl podHeartbeat)
  {
    Objects.requireNonNull(bartender);
    Objects.requireNonNull(podHeartbeat);

    _bartender = bartender;
    _podHeartbeat = podHeartbeat;
    
    _serverSelf = (ServerHeartbeat) bartender.serverSelf();
    
    /*
    _serverSelf = serverSelf;
    _root = root;
    
    ServiceManagerAmp rampManager = AmpSystem.getCurrentManager();
    
    new SchemePod(bartender, rampManager).bind("pod:");
    
    _heartbeatService = heartbeatService;
    
    EventService eventService = AmpSystem.getCurrent().getEventService();
    _failoverEvents = eventService.lookup(FailoverEvents.class);
    _podEvents = eventService.lookup(PodOnUpdate.class);
    
    eventService.subscribe(ServerBartenderEvents.class,
                           new HeartbeatEventsPod());
    
    _updatePodSystem = new UpdatePodSystem(_podMap.values());
    */
  }
  
  public void start()
  {
    if (! _lifecycle.toActive()) {
      return;
    }
    
    ServicesAmp rampManager = AmpSystem.currentManager();
    
    String clusterId = _serverSelf.getClusterId();
    
    /*
    _podServiceManager = rampManager.lookup("pod://cluster_root" + PodLocalService.PATH)
                                    .as(PodLocalService.class);
                                    */
    
    // _podManager.start();
    _podHeartbeat.start();
  }
  
  public void stop()
  {
    _lifecycle.toDestroy();
  }
  
  public UpdatePodSystem getUpdate()
  {
    return _updatePodSystem;
  }

  public boolean isStartValid()
  {
    return _heartbeatService.isStartValid();
  }

  //
  // service methods
  //  
  
  @Direct
  public PodBartender findPod(String name)
  {
    name = canonPodName(name);

    return _podHeartbeat.getPod(name);
  }
  
  @Direct
  public PodBartender findActivePod(String name)
  {
    name = canonPodName(name);
    
    return _podHeartbeat.getActivePod(name);
  }
  
  @Direct
  public ServerPod findActiveServer(String name)
  {
    FailoverNode failover = _failoverMap.get(name);
    
    if (failover != null) {
      return failover.getServer();
    }
    else {
      return null;
    }
  }
  
  public void onJoinStart()
  {
    _podHeartbeat.onJoinStart();
  }
  
  public Iterable<PodBartender> getPods()
  {
    return _podHeartbeat.getPods();
  }
  
  /*
  public void createPodByBuilder(PodBuilder builder,
                                 Result<PodBartender> result)
  {
    Objects.requireNonNull(builder);

    UpdatePod updatePod = createPodUpdate(builder);
    
    _podHeartbeat.addNewPod(updatePod, result);
  }
  */
  
  public void createPodByUpdate(UpdatePod updatePod,
                                 Result<PodBartender> result)
  {
    Objects.requireNonNull(updatePod);
    
    _podHeartbeat.addNewPod(updatePod, result);
  }
  
  public UpdatePod getInitPod(String id)
  {
    return _podHeartbeat.getInitPod(id);
  }
  
  /*
  public UpdatePod createPodUpdate(PodBuilder podBuilder)
  {
    final String name = canonPodName(podBuilder.getName());
    
    UpdatePodBuilder builder = new UpdatePodBuilder();
    
    int p = name.indexOf('.');
    
    if (p >= 0) {
      String podName = name.substring(0, p);
      String clusterId = name.substring(p + 1);
      
      builder.name(podName);
      
      ClusterHeartbeat cluster = (ClusterHeartbeat) _bartender.findCluster(clusterId);
      
      builder.cluster(cluster);
    }
    else {
      builder.name(name);
      builder.cluster(_serverSelf.getCluster());
    }
    
    builder.type(podBuilder.getType());
   
    if (podBuilder.getType() == PodBartender.PodType.off) {
      builder.primaryCount(0);
      builder.pod(new ServerPod[0]);
    }
    else {
      int serverCount = Math.max(podBuilder.getSize(), 3);
   
      builder.setRoundRobin(_roundRobinPodGenerator++);
   
      for (ServiceServer.Server server : podBuilder.getServers()) {
        builder.server(server.getAddress(), server.getPort());
      }
   
      builder.pod(builder.buildServers(_serverSelf.getRack(), serverCount));
   
      builder.primaryCount(podBuilder.getSize());
    }
    
    return builder.build();
  }
  */

  /*
  public void updatePod(UpdatePod update,
                               Result<UpdatePod> result)
  {
    ClusterHeartbeat cluster = _root.findCluster(update.getClusterId());
   
    PodBartenderImpl pod = new PodBartenderImpl(update, cluster);
   
    addPod(pod);
   
    if (result != null) {
      result.complete(pod.getUpdate());
    }
  }
  */
  
  private String canonPodName(String name)
  {
    name = name.replace('-', '_');
    
    if (name.indexOf('.') < 0) {
      name = name + '.' + _serverSelf.getClusterId();
    }
    
    return name;
  }

  /*
  public boolean updatePodsFromHeartbeat()
  {
    long rootSequence = _root.getSequence();
    
    if (rootSequence == _lastRootSequence) {
      return false;
    }
    
    _lastRootSequence = rootSequence;
    
    boolean isUpdate = false;
    
    long lastCrc = _updatePodSystem.getCrc();
    
    for (PodBartenderImpl pod : _podMap.values()) {
      long oldPodCrc = pod.getUpdate().getCrc();
      
      if (pod.updateFromHeartbeat(this, _serverSelf)) {
        isUpdate = true;
            
        onUpdatePod(pod);
      }
    }
    
    if (isUpdate) {
      _updatePodSystem = new UpdatePodSystem(_podMap.values());

      if (_updatePodSystem.getCrc() != lastCrc) {
        _heartbeatService.onPodSystemUpdate();
      }
    }
    
    return isUpdate;
  }
  */

  /*
  private void onUpdatePod(PodBartender pod)
  {
    updatePodFailover(pod);
    
    _podEvents.onUpdate(pod);
  }
  */
  
  //
  // updates
  //

  /**
   * Updates the pod system because of a heartbeat server change.
   * 
   * A new server can be added to an existing pod.
   */
  /*
  public void onUpdateFromPeer(UpdatePodSystem updatePodSystem)
  {
    long lastCrc = _updatePodSystem.getCrc();
                       
    for (UpdatePod updatePod : updatePodSystem.getPods()) {
      onUpdatePodFromPeer(updatePod);
    }
    
    if (_updatePodSystem.getCrc() != lastCrc) {
      _heartbeatService.updatePod(_updatePodSystem);
    }
  }
  */
  
  public void onUpdateFromPeerCluster(String clusterId, 
                                      UpdatePodSystem updatePodSystem)
  {
    // onUpdateFromPeer(updatePodSystem);
    
    /* XXX:
    PodCluster podCluster = _podClusterMap.get(clusterId);
    
    if (podCluster != null) {
      podCluster.updateFromPeer(updatePodSystem);
    }
    else {
      log.finer("Update unknown cluster pod: " + clusterId);
    }
    */
  }
  
  /*
  private void onUpdatePodFromPeer(UpdatePod updatePod)
  {
    PodBartenderImpl oldPod = _podMap.get(updatePod.getId());
    
    if (oldPod != null) {
      if (oldPod.onUpdateFromPeer(updatePod, this)) {
        _updatePodSystem = new UpdatePodSystem(_podMap.values());
        
        onUpdatePod(oldPod);
      }
      
      return;
    }
    
    ClusterHeartbeat cluster = _root.findCluster(updatePod.getClusterId());
    
    PodBartenderImpl newPod = new PodBartenderImpl(updatePod, 
                                                   cluster);
    
    addPod(newPod);
  }
  */

  /*
  void updatePodProxy(PodBartenderImpl pod)
  {
    String podName = pod.getId() + '.' + pod.getClusterId();
    
    PodBartenderProxy proxy = _proxyMap.get(podName);
    
    if (proxy == null) {
      proxy = new PodBartenderProxy(pod.getId());
      proxy.setDelegate(pod);
      
      _proxyMap.put(podName, proxy);
    }
    
    proxy.setDelegate(pod);
  }
  */
  
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
    for (ServerPod serverPod : serversPod) {
      ServerBartender server = serverPod.getServer();
      
      if (server != null && server.getPort() == _serverSelf.getPort()) {
        return true;
      }

      String hintServer = serverPod.getHint().getServer();
      
      if (hintServer == null) {
        continue;
      }
      
      int p = hintServer.lastIndexOf(':');
      int port = Integer.parseInt(hintServer.substring(p + 1));
      
      if (port == _serverSelf.getPort()) {
        return true;
      }
    }
    
    return false;
  }
  */
  
  //
  // failover
  //
  
  /*
  public void updatePodFailover()
  {
    ArrayList<PodBartender> pods = new ArrayList<>(_podMap.values());
    
    for (PodBartender pod : pods) {
      updatePodFailover(pod);
    }
  }
  */
  
  /*
  private void updatePodFailover(PodBartender pod)
  {
    int nodeCount = pod.getNodeCount();
    
    for (int i = 0; i < nodeCount; i++) {
      NodePodAmp node = pod.getNode(i);

      updatePodFailoverNode(node);
    }
  }
  */
  /*
  private void updatePodFailoverNode(NodePodAmp node)
  {
    int serverCount = node.getServerCount();
    
    if (! _heartbeatService.isJoinComplete()) {
      serverCount = Math.min(1, serverCount);
    }

    PodBartender pod = node.getPod();
    
    for (int i = 0; i < serverCount; i++) {
      ServerBartender server = node.getServer(i);
      
      if (server== null) {
        continue;
      }
      
      if (server.isUp()) {
        FailoverNode failoverNode = new FailoverNode(node.getPod(), 
                                                     node.getNodeIndex(),
                                                     serverPod);
        
        String name = node.getPod().getName() + "." + node.getNodeIndex();
        
        FailoverNode oldNode = _failoverMap.get(name);
        
        if (oldNode == null) {
          // XXX: failback
          _failoverMap.put(name, failoverNode);
          _failoverEvents.onUp(failoverNode);
        }
        else if (pod.getSequence() < oldNode.getPod().getSequence()) {
          continue;
        }
        else if (oldNode.getServer() != serverPod) {
          if (! oldNode.getServer().isUp()) {
            // failover
            _failoverMap.put(name, failoverNode);
            _failoverEvents.onUp(failoverNode);
          }
          else {
            // failback
            _failoverMap.put(name, failoverNode);
            _failoverEvents.onDown(failoverNode);
          }
        }
        
        return;
      }
    }
  }
  */

  //
  // impl
  //
  
  /*
  void addPod(PodBartenderImpl pod)
  {
    String name = pod.getId() + '.' + pod.getClusterId();
    
    PodBartenderImpl oldPod = _podMap.get(name);
    
    if (oldPod != null && pod.getSequence() < oldPod.getSequence()) {
      return;
    }
    
    _podMap.put(name, pod);

    PodBartenderProxy proxy = _proxyMap.get(name);
    
    if (proxy != null) {
      proxy.setDelegate(pod);
    }
    
    _updatePodSystem = new UpdatePodSystem(_podMap.values());
    
    _heartbeatService.updatePod(_updatePodSystem);
    
    onUpdatePod(pod);
  }
  */

  ServerHeartbeat createServer(String serverId)
  {
    int p = serverId.indexOf(':');
    
    String address = serverId.substring(0, p);
    int port = Integer.parseInt(serverId.substring(p + 1));
    
    boolean isSSL = false;

    return _heartbeatService.createServer(address, port, isSSL);
  }

  public ServerHeartbeat findServer(String hintServer)
  {
    ServerHeartbeat server = (ServerHeartbeat) _heartbeatService.getServer(hintServer);
    
    if (server.getRack() != null) {
      return server;
    }
    
    return null;
  }

  public ServerHeartbeat findFreeServer(ServerPod[] serversPod)
  {
    for (ServerHeartbeat server : _heartbeatService.getRack().getServers()) {
      if (server == null) {
        continue;
      }
      
      if (! isServerUsed(serversPod, server)) {
        return server;
      }
    }
    
    return null;
  }
  
  private boolean isServerUsed(ServerPod []serversPod, ServerBartender server)
  {
    for (ServerPod serverPod : serversPod) {
      if (serverPod.getServer() == server) {
        return true;
      }
    }
    
    return false;
  }

  /*
  private class HeartbeatEventsPod implements ServerBartenderEvents {
    @Override
    public void onServerStart(ServerBartender server)
    {
      updatePodFailover();
    }

    @Override
    public void onServerStop(ServerBartender server)
    {
      updatePodFailover();
    }
  }
  */
}
