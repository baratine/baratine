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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.heartbeat.ClusterHeartbeat;
import com.caucho.v5.bartender.heartbeat.ServerHeartbeat;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.VfsOld;

import io.baratine.files.BfsFileSync;
import io.baratine.service.Result;
import io.baratine.timer.Timers;


/**
 * PodsServiceCluster manages cluster pods by keeping them in sync with their
 * configuration in /config/pods.
 */
class PodsManagerConfigImpl
{
  private static final Logger log
    = Logger.getLogger(PodsManagerConfigImpl.class.getName());
  

  private final PodsManagerServiceImpl _podsServiceImpl;
  
  private final BfsFileSync _bfsPath;
  
  //private final HashMap<String,PodConfig> _podConfigMapInitial = new HashMap<>();
  
  //private final HashMap<String,PodConfig> _podConfigMap = new HashMap<>();
  private final HashMap<String,UpdatePod> _updateMap = new HashMap<>();
  
  private final HashSet<String> _fileSet = new HashSet<>();
  
  private BartenderSystem _bartender;

  // private PodBartender _podRoot;

  // private long _lastConfigDigest ;
  
  //private PodsFiles _currentFiles;
  
  public PodsManagerConfigImpl(PodsManagerServiceImpl podsServiceImpl)
  {
    _podsServiceImpl = podsServiceImpl;
    
    initPods();
    
    ServicesAmp manager = AmpSystem.currentManager();
    
    String configDir = "/config/pods"; // PodBuilderService.CONFIG_DIR)
    _bfsPath = manager.service("bfs://" + configDir) 
                      .as(BfsFileSync.class);

    // PodBartender podCluster = _bartender.findPod(clusterRootId); 
    
    // if (podCluster != null) {
    //  _nodeClusterRoot = podCluster.getNode(0);
    // }
    
    // String clusterRootId = "cluster_root";
    
    // _podRoot = _bartender.findPod(clusterRootId);
    // _nodeRoot = _podRoot.getNode(0);
  }
  
  private void initPods()
  {
    // PodLocalService podService = _bartender.getPodService();
    PodHeartbeatService podHeartbeat = _podsServiceImpl.getBartender().getPodHeartbeat();

    /*
    PodConfig clusterHubPod = new PodConfig();
    clusterHubPod.setName("cluster_hub");
    clusterHubPod.setType(PodType.triad);
    clusterHubPod.setCostComparator(new HubComparator());
    _podConfigMapInitial.put(clusterHubPod.getName(), clusterHubPod);
    
    PodConfig clusterConfig = podHeartbeat.getPodConfig("cluster");
    
    if (clusterConfig != null) {
      for (ServerPodConfig server : clusterConfig.getServers()) {
        clusterHubPod.addServer(server);
      }
    }
    
    PodConfig clusterPod = new PodConfig();
    clusterPod.setName("cluster");
    clusterPod.setType(PodType.cluster);
    clusterPod.setCostComparator(new HubComparator());
    _podConfigMapInitial.put(clusterPod.getName(), clusterPod);
    
    for (PodConfig podConfig : podHeartbeat.getPodConfigs()) {
      if (podConfig.getName().equals("cluster")
          || podConfig.getName().equals("cluster_hub")) {
        podConfig.setCostComparator(new HubComparator());
      }
      
      _podConfigMapInitial.put(podConfig.getName(), podConfig);
    }
    */
  }
  
  ServerHeartbeat getServerSelf()
  {
    return (ServerHeartbeat) getBartender().serverSelf();
  }
  
  ClusterHeartbeat getCluster()
  {
    return getServerSelf().getCluster();
  }
  
  BartenderSystem getBartender()
  {
    return _podsServiceImpl.getBartender();
  }
  
  void onJoinStart()
  {
    _bfsPath.watch(path->updateBfsPath());
    
    ServicesAmp manager = AmpSystem.currentManager();
    
    Timers timer = manager.service("timer://").as(Timers.class);
    
    // XXX: check every 60s because watch might be unreliable (cloud/0642)
    timer.runEvery(x->updateBfsPath(), 60, TimeUnit.SECONDS, Result.ignore());
    
    updateBfsPath();
  }

  void update(Result<Void> result)
  {
    updateBfsPath(result);
  }
  
  void update()
  {
    updateBfsPath();
  }

  /*
  public PodConfig getPodConfigInit(String podName)
  {
    return _podConfigMapInitial.get(podName);
  }

  public Iterable<PodConfig> getPodConfigs()
  {
    ArrayList<String> podNames = new ArrayList<>(_podConfigMap.keySet());
    
    Collections.sort(podNames);
    
    ArrayList<PodConfig> podConfigList = new ArrayList<>();

    for (String name : podNames) {
      PodConfig pod = _podConfigMap.get(name);
      
      podConfigList.add(pod);
    }
    
    return podConfigList;
  }
  */

  public UpdatePod getUpdate(String podName)
  {
    return _updateMap.get(podName);
  }

  public Iterable<UpdatePod> getUpdates()
  {
    return _updateMap.values();
  }

  /*
  public Iterable<UpdatePod> getUpdatePods()
  {
    ArrayList<String> podNames = new ArrayList<>(_podConfigMap.keySet());
    Collections.sort(podNames);

    ArrayList<UpdatePod> updatePods = new ArrayList<>();
    
    for (String podName : podNames) {
      PodConfig pod = _podConfigMap.get(podName);
    
      updatePods.add(updatePod(pod));
    }
    
    return updatePods;
  }
  */
  
  /**
   * Updates the deployed list for bfs configuration of the pods.
   * 
   * Any file in /config/pods named *.cf is a pod config file.
   */
  private void updateBfsPath()
  {
    _bfsPath.list(Result.on(list->updateBfsPath(list)));
  }

  /**
   * Updates the deployed list for bfs configuration of the pods.
   * 
   * Any file in /config/pods named *.cf is a pod config file.
   */
  private void updateBfsPath(Result<Void> result)
  {
    _bfsPath.list(result.of(list->{ updateBfsPath(list); return null; }));
  }
  
  /**
   * Updates the deployed list for bfs configuration of the pods.
   * 
   * Any file in /config/pods named *.cf is a pod config file.
   */
  private void updateBfsPath(String []list)
  {
    if (list == null) {
      list = new String[0];
    }
    
    PodsConfig podsConfig = parseConfig(list);
    
    if (podsConfig != null) {
      updatePodConfig(podsConfig);
    }
    
    updatePods();
    
    if (podsConfig != null && podsConfig.isBaseModified()) {
      _podsServiceImpl.updateConfig();
    }
  }
  
  private void updatePodConfig(PodsConfig podsConfig)
  {
    /*
    _podConfigMap.clear();
    
    for (PodConfig pod : podsConfig.getPods()) {
      _podConfigMap.put(pod.getName(), pod);
    }
    */
  }
  
  private PodsConfig parseConfig(String []list)
  {
    Arrays.sort(list);
    
    /*
    PodsFiles files = new PodsFiles(_bfsPath, list);
    
    if (files.equals(_currentFiles)) {
      return null;
    }
    
    _currentFiles = files;
    
    PodsConfig podsConfig = new PodsConfig();
    
    if (! files.equalsIgnoreAutopod(_currentFiles)) {
      podsConfig.setBaseModified(true);
    }
    
    for (PodConfig podConfig : _podConfigMapInitial.values()) {
      podsConfig.addPodImpl(podConfig.clone());
    }
    
    // _podMap.put(clusterPod.getName(), clusterPod);
    
    long hash = 0;

    for (String name : list) {
      if (! name.endsWith(".cf")) {
        continue;
      }
      
      BfsFileSync file = _bfsPath.lookup(name);
      
      if (! name.equals(PodsManagerServiceImpl.PATH_AUTOPOD)) {
        hash = 65521 * hash + file.getStatus().getVersion();
      }
        
      if (! _fileSet.contains(name)) {
        _fileSet.add(name);
        
        file.watch(x->updateBfsPath());
      }
      
      parseConfigFile(podsConfig, name);
    }
    */
    
    //return podsConfig;
    return null;
  }

  /*
   * Parse the /config/pods/foo.cf configuration file. The configuration
   * is merged with the running PodsConfig.
   */
  private void parseConfigFile(PodsConfig podsConfig, String path)
  {
    String configDir = "/config/pods";
    
    PathImpl configPath = VfsOld.lookup("bfs://" + configDir + "/" + path);
    
    /*
    ConfigContext config = new ConfigContext();

    try {
      podsConfig.setCurrentDepend(new Depend(configPath));

      config.configure2(podsConfig, configPath);
    } catch (Exception e) {
      e.printStackTrace();
      log.warning(e.toString());
      
      podsConfig.setConfigException(e);
    }
    */
  }
  
  /**
   * Update the UpdatePod map to match the pod config.
   */
  ArrayList<UpdatePod> updatePods()
  {
    /*
    ArrayList<String> podNames = new ArrayList<>(_podConfigMap.keySet());
    Collections.sort(podNames);

    ArrayList<UpdatePod> updatePods = new ArrayList<>();
    
    for (String podName : podNames) {
      PodConfig pod = _podConfigMap.get(podName);
    
      updatePods.add(updatePod(pod));
    }
    
    _updateMap.clear();
    
    for (UpdatePod update : updatePods) {
      _updateMap.put(update.getPodName(), update);
    }
    
    return updatePods;
    */
    return null;
  }

  /*
  private UpdatePod updatePod(PodConfig podConfig)
  {
    UpdatePod updateNew = createPodUpdate(podConfig);
  
    UpdatePod updateOld = _updateMap.get(podConfig.getName());
  
    if (updateOld != null && updateOld.getCrc() == updateNew.getCrc()) {
      return updateOld;
    }
  
    if (updateOld != null) {
      long now = CurrentTime.getCurrentTime();
      long sequence = Math.max(updateOld.getSequence() + 1, now);
    
      updateNew = new UpdatePod(updateNew, sequence);
    }
    
    return updateNew;
  }
  */
  
  /*
  private UpdatePod createPodUpdate(PodConfig config)
  {
    UpdatePodBuilder builder = new UpdatePodBuilder();
    
    builder.name(config.getName());
    builder.cluster(getCluster());
    
    builder.tag(config.getTag());
    
    long now = CurrentTime.getCurrentTime();
    
    builder.sequence(now);

    if (config.getType() == null) {
      builder.type(PodType.solo);
    }
    //else if (PodType.auto.equals(config.getType())) {
    //  builder.type(PodType.off);
    //}
    else {
      builder.type(config.getType());
    }
    
    if (PodType.off.equals(config.getType())) {
    }
    else if (config.getSize() > 0) {
      builder.primaryCount(config.getSize());
    }
    else if (config.getType() == PodType.cluster) {
      builder.primaryCount(Math.max(3, config.getServerCount()));
    }
    
    //String id = config.getName() + "." + _serverSelf.getCluster().getId();
    
    //UpdatePod initPod = _bartender.getPodService().getInitPod(id);
    
    ArrayList<ServerBartender> serverList = new ArrayList<>();
    
    for (ServerPodConfig serverCfg : config.getServers()) {
      String address = serverCfg.getAddress();
      int port = serverCfg.getPort();
      String machine = serverCfg.getMachine();
      
      ServerBartender server = null;
      
      if (address != null && ! address.isEmpty() && port > 0) {
        server = getCluster().findServerByName(address + ":" + port);
      }
      
      if (server == null) {
        server = getCluster().findServerByMachine(machine);
      }
      
      // cloud/0501
      ServerHeartbeat serverHeartbeat = (ServerHeartbeat) server;
      if (server != null && serverHeartbeat.getDelegate() != null) {
        server = serverHeartbeat.getDelegate();
      }

      if (server != null && ! serverList.contains(server)) {
        serverList.add(server);
        
        builder.server(server.getAddress(), 
                       server.port());
      }
      else {
        builder.server("", 0);
      }
     //   else {
     //   builder.server(serverCfg.getAddress(), 
     //                  serverCfg.getPort());
     // }
    }

   // if (initPod != null) {
    //  int i = 0;
    //  for (ServerHint hint : initPod.getHints()) {
    //    builder.hint(i++, hint);
    //  }
    //}

    //UpdatePod updatePod = builder.build();
    
    //return updatePod;
  }
  */
  
  private static class HubComparator implements Comparator<ServerPodsCost> {
    @Override
    public int compare(ServerPodsCost a, ServerPodsCost b)
    {
      ServerHeartbeat serverA = a.getServer();
      ServerHeartbeat serverB = b.getServer();
      
      int cmp;
      
      cmp = (a.isServerAssigned() ? 1 : 0) - (b.isServerAssigned() ? 1 : 0);
      
      if (cmp != 0) {
        return cmp;
      }
      
      cmp = a.getSeedIndex() - b.getSeedIndex();
      
      if (cmp != 0) {
        return cmp;
      }
      
      return serverA.getId().compareTo(serverB.getId());
    }
  }
}
