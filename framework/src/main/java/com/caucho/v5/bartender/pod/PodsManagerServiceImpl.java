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

import io.baratine.service.OnInit;
import io.baratine.service.Result;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.ServerOnUpdate;
import com.caucho.v5.bartender.heartbeat.ClusterHeartbeat;
import com.caucho.v5.bartender.heartbeat.ServerHeartbeat;
import com.caucho.v5.bartender.pod.UpdatePod.UpdateNode;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.VfsOld;
import com.caucho.v5.vfs.WriteStream;


/**
 * PodsServiceCluster manages cluster pods by keeping them in sync with their
 * configuration in /config/pods.
 */
public class PodsManagerServiceImpl implements PodsManagerService
{
  private static final Logger log
    = Logger.getLogger(PodsManagerServiceImpl.class.getName());
  
  private final ServerHeartbeat _serverSelf;
  private final ClusterHeartbeat _cluster;
  
  private ArrayList<ServerHeartbeat> _serverList = new ArrayList<>();
  
//  private final BfsFileSync _bfsPath;

  private HashMap<String,PodState> _podStateMap = new HashMap<>();
  
  private final BartenderSystem _bartender;

  private final PodsManagerConfigImpl _podsConfig;
  private final PodsManagerAutoPodImpl _podsAutoPod;
  
  //private PodBartender _podRoot;

  //private long _lastConfigDigest ;
  
  //private PodHeartbeatService _podHeartbeat;

  //private PodsFiles _currentFiles;
  
  private Lifecycle _lifecycle = new Lifecycle();

  //private NodePodAmp _nodeRoot;

  public PodsManagerServiceImpl(BartenderSystem bartender)
  {
    _bartender = bartender;
    _serverSelf = (ServerHeartbeat) _bartender.serverSelf();
    
    _cluster = (ClusterHeartbeat) _serverSelf.getCluster();
    
    // PodLocalService podService = _bartender.getPodService();
    //_podHeartbeat = _bartender.getPodHeartbeat();
    
    _podsConfig = new PodsManagerConfigImpl(this);
    _podsAutoPod = new PodsManagerAutoPodImpl(this);

    // PodBartender podCluster = _bartender.findPod(clusterRootId); 
    
    // if (podCluster != null) {
    //  _nodeClusterRoot = podCluster.getNode(0);
    // }
    
    //String clusterRootId = "cluster_root";
    
    //_podRoot = _bartender.findPod(clusterRootId);
    //_nodeRoot = _podRoot.getNode(0);
  
    /*
    ServiceManagerAmp manager = AmpSystem.getCurrentManager();
    
    _bfsPath = manager.lookup("bfs://" + PodBuilderService.CONFIG_DIR)
                      .as(BfsFileSync.class);
                      */
  }

  boolean isActive()
  {
    return _lifecycle.isActive();
  }

  BartenderSystem getBartender()
  {
    return _bartender;
  }

  PodsManagerConfigImpl getPodsConfig()
  {
    return _podsConfig;
  }
  
  @OnInit
  public void onInit()
  {
    ServiceRefAmp serviceRef = ServiceRefAmp.current();
    
    /*
    _deployService = serviceRef.as(PodsService.class);
    */
    
    // ServiceManagerAmp manager = AmpSystem.getCurrentManager();

    /*
    _rootService = manager.lookup("pod://cluster_root" + PodsServiceCluster.ADDRESS)
                          .as(PodsServiceCluster.class);
                          */
    
    //    serviceRef.pin(this).bind("public://" + PodsManagerService.ADDRESS);
  }
  
  public void onJoinStart()
  {
    _lifecycle.toActive();
    
    // _bfsPath.watch(path->updateBfsPath());
    
    ServiceManagerAmp manager = AmpSystem.currentManager();

    // on server updates the self-server might become the owner
    manager.service(ServerOnUpdate.ADDRESS)
           .subscribe((ServerOnUpdate) s->onServerUpdate(s));
    
    _podsConfig.onJoinStart();
  }

  @Override
  public void update(Result<Void> result)
  {
    // updateBfsPath(result);
    
    _podsConfig.update(result.of(x->{ updateAutoPod(); return null; }));
  }

  private void onServerUpdate(ServerBartender server)
  {
    // updateBfsPath();
    
    updateServer();
  }
  
  public void updateServer()
  {
    ArrayList<ServerHeartbeat> serverList = getServerList();
    
    if (! _serverList.equals(serverList)) {
      _serverList = serverList;
    
      updateAutoPod();
      
      return;
    }
  }

  void updateConfig()
  {
    updateAutoPod();
  }
  
  private void updateAutoPod()
  {
    _podsAutoPod.update();
    _bartender.getPodHeartbeat().updatePods(_podsConfig.updatePods());
  }
  
  private boolean isHubWritable()
  {
    if (! _lifecycle.isActive()) {
      return false;
    }
    
    PodBartender pod = _bartender.findActivePod("cluster_hub");
    
    // if a server for the hub is up, it's writable
    if (pod != null) {
      NodePodAmp node = pod.getNode(0);
    
      for (int i = 0; i < pod.getDepth(); i++) {
        ServerBartender server = node.server(i);

        if (server != null && server.isUp()) {
          return true;
        }
      }
    }
    
    if (_serverSelf.getSeedIndex() > 0) {
      return true;
    }
    
    // UpdatePod updateHub = _updateMap.get("cluster_hub");
    UpdatePod updateHub = _podsConfig.getUpdate("cluster_hub");
    
    // if hub has a seed server, but isn't active, the hub isn't accessible
    for (String serverName : updateHub.getServers()) {
      if (serverName != null && ! serverName.isEmpty()) {
        return false;
      }
    }
    
    // if there are no seed servers, this is a singleton server with its
    // own hub
    
    return true;
  }
  
  private ArrayList<ServerHeartbeat> getServerList()
  {
    ArrayList<ServerHeartbeat> serverList = new ArrayList<>();
    
    for (ServerHeartbeat server : _cluster.getServers()) {
      serverList.add(server);
    }
    
    Collections.sort(serverList, (x,y)->x.getId().compareTo(y.getId()));
    
    return serverList;
  }

  @Override
  public void onPodAppStart(String podId, String serverId, String tag)
  {
    getPodState(getPodName(podId)).onStart(podId, serverId, tag);

    updateClusterPodState(getPodName(podId));
  }
  
  @Override
  public void onPodAppStop(String podId, String serverId, String tag)
  {
    getPodState(getPodName(podId)).onStop(podId, serverId, tag);
    
    updateClusterPodState(getPodName(podId));
  }
  
  private void updateClusterPodState(String podName)
  {
    if (! isHubWritable()) {
      return;
    }
    
    PathImpl path;
    
    path = VfsOld.lookup("bfs:///cluster/pods/" + podName);
    
    PodState podState = getPodState(podName);
    
    printClusterPodState(path, podState);
  }
  
  private void printClusterPodState(PathImpl path, PodState podState)
  {
    try (WriteStream out = path.openWrite()) {
      PodBartender pod = podState.getPod();
      String podName = podState.getName();
      
      out.print("{ \"podName\" : \"" + podName + "\"");
      
      String tag = pod.getTag();
      if (tag != null && ! tag.isEmpty()) {
        out.print(",\n  \"tag\" : \"" + tag + "\"");
      }
      
      boolean isActive = isPodActive(podState, pod);
      String state = isActive ? "active" : "off";
    
      out.print(",\n  \"state\" : \"" + state + "\"");
      
      out.println(",\n  \"nodes\" : [");
      
      printClusterPodNodeState(out, podState, "pods/" + podName);
      
      for (int i = 0; i < pod.nodeCount(); i++) {
        out.print(",\n");
        
        printClusterPodNodeState(out, podState, "pods/" + podName + "." + i);
      }
      
      out.println("\n  ]");
      
      out.println("}");
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
  }
  
  private boolean isPodActive(PodState podState, PodBartender pod)
  {
    String podName = pod.name();
    String tag = pod.getTag();
    
    if (tag == null) {
      tag = "";
    }
    
    int nodeCount = pod.nodeCount();
    
    if (nodeCount == 0) {
      return false;
    }
    
    for (int i = 0; i < nodeCount; i++) {
      String id = "pods/" + podName + "." + i;
      
      PodNodeState nodeState = podState.getPodNodeState(id);
      NodePodAmp nodePod = pod.getNode(i);
      
      if (! isPodNodeActive(nodeState, nodePod, tag)) {
        return false;
      }
    }
    
    return true;
  }
  
  private boolean isPodNodeActive(PodNodeState nodeState, 
                                  NodePodAmp nodePod, 
                                  String tag)
  {
    for (int i = 0; i < nodePod.serverCount(); i++) {
      ServerBartender server = nodePod.server(i);
      
      if (server != null) {
        String serverId = server.getId();
        
        PodNodeServerState state = nodeState.getServerState(serverId);
        
        if (state.isActive()) {
          return tag.equals(state.getTag());
        }
      }
    }
    
    return false;
  }
  
  private void printClusterPodNodeState(WriteStream out, 
                                        PodState podState,
                                        String nodeId)
    throws IOException
  {
    PodNodeState nodeState = podState.getPodNodeState(nodeId);
    
    out.print("    { \"id\" : \"" + nodeId + "\"");
    out.print(",\n      \"servers\" : [");
    
    boolean isFirst = true;
    for (PodNodeServerState server : nodeState.getServers()) {
      if (! isFirst) {
        out.print(",");
      }
      isFirst = false;
      
      out.print("\n        { \"id\" : \"" + server.getId() + "\"");
      
      String state = server.isActive() ? "active" : "off";
      out.print(",\n          \"state\" : \"" + state + "\"");
      
      String tag = server.getTag();
      
      if (tag != null) {
        out.print(",\n          \"tag\" : \"" + tag + "\"");
      }
      
      out.print("}");
    }
    
    out.print("\n      ]");
    
    out.print("\n    }");
  }
  
  private String getPodName(String nodeId)
  {
    if (! nodeId.startsWith("pods/")) {
      throw new IllegalArgumentException(nodeId);
    }
    
    int p = nodeId.indexOf('/');
    String podNode = nodeId.substring(p + 1);
    
    int q = podNode.indexOf('.');
    
    if (q > 0) {
      return podNode.substring(0, q);
    }
    else {
      return podNode;
    }
  }
  
  private PodState getPodState(String podName)
  {
    PodState podState = _podStateMap.get(podName);
    
    if (podState == null) {
      PodBartender pod = _bartender.findPod(podName);
      
      podState = new PodState(podName, pod);
      
      _podStateMap.put(podName, podState);
    }
    
    return podState;
  }
  
  private static class PodState {
    private final String _podName;
    private final PodBartender _pod;
    
    private final HashMap<String,PodNodeState> _nodeMap = new HashMap<>();
    
    PodState(String podName,
             PodBartender pod)
    {
      _podName = podName;
      _pod = pod;
    }
    
    String getName()
    {
      return _podName;
    }
    
    PodBartender getPod()
    {
      return _pod;
    }
    
    void onStart(String id, String serverId, String tag)
    {
      getPodNodeState(id).onStart(serverId, tag);
    }
    
    void onStop(String id, String serverId, String tag)
    {
      getPodNodeState(id).onStop(serverId, tag);
    }
    
    PodNodeState getPodNodeState(String nodeName)
    {
      PodNodeState nodeState = _nodeMap.get(nodeName);
      
      if (nodeState == null) {
        nodeState = new PodNodeState(nodeName);
        
        _nodeMap.put(nodeName, nodeState);
      }
      
      return nodeState;
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _podName + "]";
    }
  }
  
  private static class PodNodeState {
    private final String _nodeName;
    
    private HashMap<String,PodNodeServerState> _serverMap = new HashMap<>();
    
    PodNodeState(String nodeName)
    {
      _nodeName = nodeName;
    }
    
    void onStart(String serverId, String tag)
    {
      getServerState(serverId).onStart(tag);
    }
    
    void onStop(String serverId, String tag)
    {
      getServerState(serverId).onStop(tag);
    }
    
    Iterable<PodNodeServerState> getServers()
    {
      return _serverMap.values();
    }
    
    PodNodeServerState getServerState(String serverName)
    {
      PodNodeServerState serverState = _serverMap.get(serverName);
      
      if (serverState == null) {
        serverState = new PodNodeServerState(serverName);
        
        _serverMap.put(serverName, serverState);
      }
      
      return serverState;
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _nodeName + "]";
    }
  }
  
  private static class PodNodeServerState {
    private final String _id;
    
    private String _tag = "";
    private boolean _isActive;
    
    PodNodeServerState(String id)
    {
      _id = id;
    }
    
    String getId()
    {
      return _id;
    }
    
    boolean isActive()
    {
      return _isActive;
    }
    
    String getTag()
    {
      return _tag;
    }
    
    void setTag(String tag)
    {
      if (tag == null) {
        tag = "";
      }
      
      _tag = tag;
    }
    
    void onStart(String tag)
    {
      setTag(tag);
      _isActive = true;
    }
    
    void onStop(String tag)
    {
      setTag(tag);
      _isActive = false;
    }
  }
}
