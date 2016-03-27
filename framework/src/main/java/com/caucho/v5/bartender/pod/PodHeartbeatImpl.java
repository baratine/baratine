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

import io.baratine.event.EventsSync;
import io.baratine.service.Direct;
import io.baratine.service.Result;
import io.baratine.service.ServiceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.baratine.InService;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ClusterBartender;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.ServerOnUpdate;
import com.caucho.v5.bartender.heartbeat.ClusterHeartbeat;
import com.caucho.v5.bartender.heartbeat.HeartbeatService;
import com.caucho.v5.bartender.heartbeat.HeartbeatServiceLocal;
import com.caucho.v5.bartender.heartbeat.PodBuilderConfig;
import com.caucho.v5.bartender.heartbeat.ServerHeartbeat;
import com.caucho.v5.bartender.pod.PodBartender.PodType;
import com.caucho.v5.util.CurrentTime;

@InService(HeartbeatServiceLocal.class)
public class PodHeartbeatImpl
{
  private static final Logger log = Logger.getLogger(PodHeartbeatImpl.class.getName());
  
  private static final String CLUSTER_ROOT = "cluster_root";
  
  private final ConcurrentHashMap<String,PodBartenderImpl> _podMap
    = new ConcurrentHashMap<>();
  
  private final ConcurrentHashMap<String,PodBartenderProxy> _podProxyMap
    = new ConcurrentHashMap<>();
  
  private ArrayList<ServerHeartbeat> _serverList = new ArrayList<>();
  
  //private HashMap<String,PodConfig> _podConfigMap = new HashMap<>();
  
  private HashMap<String,UpdatePod> _podInitMap = new HashMap<>();
  private HashMap<String,UpdatePod> _podUpdateMap = new HashMap<>();
  
  // private final HeartbeatService _heartbeatService;
  
  //private final PodManagerImpl _podManager;
  
  private PodOnUpdate _podEvents;

  private ServerHeartbeat _serverSelf;

  private UpdatePodSystem _updatePodSystem;

  // private long _lastRootSequence;

  private BartenderSystem _bartender;

  private HeartbeatServiceLocal _heartbeatLocal;
//   private int _roundRobin;

  private String _clusterRootName;

  private PodBartenderImpl _localPod;

  private PodBartenderProxy _clusterRoot;

  private ArrayList<UpdatePod> _selfUpdatePods = new ArrayList<>();

  // private List<ServerBartender> _seedServers;
  
  public PodHeartbeatImpl(BartenderSystem bartender,
                          ServerHeartbeat serverSelf,
                          HeartbeatService heartbeatService,
                          HeartbeatServiceLocal heartbeatLocal)
  {
    Objects.requireNonNull(bartender);
    Objects.requireNonNull(serverSelf);
    
    _bartender = bartender;

    _serverSelf = serverSelf;
    
    // _podManager = new PodManagerImpl(_bartender, serverSelf, this);
    
    _updatePodSystem = new UpdatePodSystem(_podUpdateMap.values(), _updatePodSystem);

    // _heartbeatService = heartbeatService;
    _heartbeatLocal = heartbeatLocal;
    
    // EventService eventService = AmpSystem.getCurrent().getEventService();
    ServiceManager manager = AmpSystem.currentManager();

    _podEvents = manager.service(PodOnUpdate.ADDRESS)
                        .as(PodOnUpdate.class);
  //  initPods(podBuilderList);
    
    _clusterRoot = getPodProxy(CLUSTER_ROOT + "." + serverSelf.getClusterId());
  }
  
  public UpdatePodSystem getUpdate()
  {
    return _updatePodSystem;
  }
  
  /*
  public PodManagerImpl getManager()
  {
    return _podManager;
  }
  */

  // @OnInit
  public void start()
  {
    ServiceManager manager = AmpSystem.currentManager();
    //EventService events = AmpSystem.getCurrent().getEventService();
    
    EventsSync events = manager.service(EventsSync.class);
    
    events.subscriber(ServerOnUpdate.class, s->onServerUpdate(s));
    
    for (ServerHeartbeat server : _serverSelf.getCluster().getServers()) {
      onServerUpdate(server);
    }
  }

  //
  // service methods
  //  
  
  @Direct
  public UpdatePodSystem getUpdatePodSystem()
  {
    return _updatePodSystem;
  }
  
  
  @Direct
  public PodBartender getPod(String name)
  {
    PodBartenderProxy podProxy = getPodProxy(name);
    // _podProxyMap.get(name);
    
    if (podProxy.getDelegate() == null) {
      PodBartenderImpl pod = _podMap.get(name);
      
      if (pod != null) {
        podProxy.setDelegate(pod);
      }
    }
    
    return podProxy;
  }
  
  private PodBartenderProxy getPodProxy(String name)
  {
    PodBartenderProxy podProxy = _podProxyMap.get(name);
    
    if (podProxy == null) {
      podProxy = new PodBartenderProxy(name);
      
      _podProxyMap.putIfAbsent(name, podProxy);
      
      podProxy = _podProxyMap.get(name);
    }
    
    return podProxy;
    
  }

  public Map<String, PodBartender> podMap()
  {
    HashMap<String,PodBartender> podMap = new HashMap<>();
    
    podMap.putAll(_podProxyMap);

    return podMap;
  }
  
  @Direct
  public PodBartender getActivePod(String name)
  {
    PodBartenderProxy podProxy = _podProxyMap.get(name);
    
    if (podProxy != null && podProxy.getDelegate() != null) {
        // && podProxy.getType() != PodType.off) {
      return podProxy;
    }
    else {
      return null;
    }
  }
  
  @Direct
  public Iterable<PodBartender> getPods()
  {
    ArrayList<PodBartender> pods = new ArrayList<>();
    
    for (String podName : _podMap.keySet()) {
      pods.add(getPodProxy(podName));
    }
    
    if (_localPod != null) {
      pods.add(getPodProxy(_localPod.getId()));
    }
    
    return pods;
  }
  
  //
  // updates
  //

  public void initPod(UpdatePod updatePod)
  {
    if (updatePod == null) {
      return;
    }
    
    _podInitMap.put(updatePod.getId(), updatePod);

    if ("local".equals(updatePod.getPodName())) {
      initLocal(updatePod);
    }
    else {
      updatePod(updatePod);
    }
  }
  
  public UpdatePod getInitPod(String id)
  {
    return _podInitMap.get(id);
  }
  
  private void initLocal(UpdatePod updatePod)
  {
    ClusterHeartbeat cluster = _serverSelf.getCluster();
    
    PodBartenderImpl pod = new PodBartenderImpl(updatePod, cluster);
    
    _localPod = pod;
    
    PodBartenderProxy podProxy = getPodProxy("local.local");
    
    podProxy.setDelegate(pod);
  }

  public void updatePodSystem(UpdatePodSystem update)
  {
    if (update == null) {
      return;
    }
    
    if (_clusterRoot.getNode(0).isServerOwner(_serverSelf)) {
      return;
    }
    
    for (UpdatePod pod : update.getPods()) {
      updatePod(pod);
    }
    
    sendUpdateHeartbeats();
  }
  
  public void updatePod(UpdatePod update)
  {
    UpdatePod updateFill = updatePodImpl(update);
    
    putPod(updateFill);
  }

  private UpdatePod updatePodImpl(UpdatePod update)
  {
    if (update.getPodName().equals(CLUSTER_ROOT)) {
      throw new IllegalStateException();
    }
    
    if (update.getPodName().equals("local")) {
      throw new IllegalStateException();
    }
    
    UpdatePod oldUpdate = _podUpdateMap.get(update.getId());
    
    if (! _serverSelf.getClusterId().equals(update.getClusterId())) {
      if (oldUpdate == null) {
        return update;
      }
      else if (oldUpdate.compareTo(update) <= 0) {
        return update;
      }
      else {
        return null;
      }
    }
    
    if (oldUpdate != null && update.compareTo(oldUpdate) <= 0) {
      return oldUpdate;
    }
    else {
      return update;
    }
  }

  public void addNewPod(UpdatePod updatePod, Result<PodBartender> result)
  {
    addPod(updatePod);
    
    String id = updatePod.getPodName() + '.' + updatePod.getClusterId();
    
    result.ok(getPod(id));
  }

  public void addPod(UpdatePod updatePod)
  {
    putPod(updatePod);
    
    sendUpdateHeartbeats();
  }

  public void updatePods(ArrayList<UpdatePod> podList)
  {
    _selfUpdatePods = podList;
    
    updateSelfPods();
  }
  
  private void updateSelfPods()
  {
    // only update if cluster_root owner
    if (! _clusterRoot.getNode(0).isServerOwner(_serverSelf)) {
      return;
    }
    
    for (UpdatePod pod : _selfUpdatePods) {
      putPod(pod);
    }
    
    sendUpdateHeartbeats();
  }
  
  public void putPod(UpdatePod update)
  {
    Objects.requireNonNull(update);
    
    UpdatePod oldUpdate = _podUpdateMap.get(update.getId());
    
    long sequence = update.getSequence();
    
    if (oldUpdate == null) {
    }
    else if (oldUpdate.getCrc() != update.getCrc()) {
      sequence = Math.max(oldUpdate.getSequence() + 1, sequence);
    }
    else {
      sequence = Math.max(oldUpdate.getSequence(), sequence);
    }
    
    if (update.getSequence() <= sequence) {
      update = new UpdatePod(update, sequence);
    }

    _podUpdateMap.put(update.getId(), update);
    
    if (oldUpdate == null || oldUpdate.getCrc() != update.getCrc()) {
      _updatePodSystem = new UpdatePodSystem(_podUpdateMap.values(), _updatePodSystem);
    }
    
    updatePodProxy(update);
  }
  
  private void updatePodProxy(UpdatePod update)
  {
    Objects.requireNonNull(update);
    
    ClusterBartender clusterBar = _bartender.findCluster(update.getClusterId());
    ClusterHeartbeat cluster = (ClusterHeartbeat) clusterBar;
    
    PodBartenderImpl pod;
    
    try {
      pod = new PodBartenderImpl(update, cluster);
    } catch (Exception e) {
      e.printStackTrace();
      throw e;
    }
    
    String name = pod.name() + '.' + pod.getClusterId();

    PodBartenderImpl oldPod = _podMap.get(name);
    
    if (oldPod != null && pod.getSequence() < oldPod.getSequence()) {
      return;
    }
    
    _podMap.put(name, pod);
    
    PodBartenderProxy podProxy = getPodProxy(name);

    podProxy.setDelegate(pod);

    if (oldPod == null || oldPod.getSequence() != pod.getSequence()) {
      _podEvents.onUpdate(podProxy);
    }
  }
  
  /**
   * Compare and merge the cluster_root with an update.
   * 
   * cluster_root is a special case because the root cluster decides on
   * the owning server for the entire cluster.
   */
  private void updateClusterRoot()
  {
    ArrayList<ServerHeartbeat> serverList = new ArrayList<>();
    
    for (ServerHeartbeat server : _serverSelf.getCluster().getServers()) {
      serverList.add(server);
    }
    
    Collections.sort(serverList, (x,y)->compareClusterRoot(x,y));
    
    UpdatePodBuilder builder = new UpdatePodBuilder();
    builder.name("cluster_root");
    builder.cluster(_serverSelf.getCluster());
    builder.type(PodType.solo);
    builder.depth(16);
    
    for (ServerHeartbeat server : serverList) {
      builder.server(server.getAddress(), server.port());
    }
    
    long sequence = CurrentTime.currentTime();
    
    sequence = Math.max(sequence, _clusterRoot.getSequence() + 1);
    
    builder.sequence(sequence);

    UpdatePod update = builder.build();
    
    updatePodProxy(update);
  }
  
  private int compareClusterRoot(ServerHeartbeat serverA, 
                                 ServerHeartbeat serverB)
  {
    int seedIndexA = serverA.getSeedIndex();
    
    if (seedIndexA <= 0) {
      seedIndexA = Integer.MAX_VALUE / 4;
    }
    
    int seedIndexB = serverB.getSeedIndex();
    
    if (seedIndexB <= 0) {
      seedIndexB = Integer.MAX_VALUE / 4;
    }
    
    int cmp = seedIndexA - seedIndexB;
    
    if (cmp != 0) {
      return cmp;
    }
    
    return serverA.getId().compareTo(serverB.getId());
  }
  
  /**
   * Send a heartbeat with the updated pods to other servers in the cluster.
   * 
   * This call wakes the heartbeat service to send the actual heartbeat.
   */
  private void sendUpdateHeartbeats()
  {
    HeartbeatServiceLocal heartbeat = _bartender.getHeartbeatLocal();
   
    if (heartbeat != null) {
      heartbeat.updateHeartbeats();
    }
  }
  
  private void onServerUpdate(ServerBartender serverBar)
  {
    ServerHeartbeat server = (ServerHeartbeat) serverBar;

    boolean isSelfCluster = (server.getCluster() == _serverSelf.getCluster());
    
    if (isSelfCluster && ! _serverList.contains(server)) {
      _serverList.add(server);
      
      updateClusterRoot();
    }
    
    ArrayList<UpdatePod> updateList = new ArrayList<>(_podUpdateMap.values());
    
    for (UpdatePod update : updateList) {
      putPod(update);
    }
  }

  /**
   * Update the sequence for all init pods after the join has completed.
   */
  void onJoinStart()
  {
    //long now = CurrentTime.getCurrentTime();
    ArrayList<UpdatePod> updatePods = new ArrayList<>();
    
    updateClusterRoot();

    /*
    for (UpdatePod updatePod : _podUpdateMap.values()) {
      if (updatePod.getSequence() == 0) {
        updatePods.add(new UpdatePod(updatePod, now));
      }
    }
    */
    
    /*
    for (UpdatePod updatePod : updatePods) {
      updatePod(updatePod);
    }
    */
    
    updatePods.addAll(_podUpdateMap.values());
    
    for (UpdatePod updatePod : updatePods) {
      updatePod(updatePod);
    }
    
    // XXX:
  }

  /*
  public void initPodConfig(PodConfig podConfig)
  {
    _podConfigMap.put(podConfig.getName(), podConfig);
  }
  
  public Iterable<PodConfig> getPodConfigs()
  {
    return _podConfigMap.values();
  }
  
  public PodConfig getPodConfig(String podName)
  {
    return _podConfigMap.get(podName);
  }
  */
}
