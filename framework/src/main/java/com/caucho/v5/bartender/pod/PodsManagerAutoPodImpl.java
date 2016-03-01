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

import java.io.IOException;
import java.util.Comparator;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.heartbeat.ClusterHeartbeat;
import com.caucho.v5.bartender.heartbeat.ServerHeartbeat;
import com.caucho.v5.bartender.pod.UpdatePod.UpdateNode;
import com.caucho.v5.io.TempOutputStream;
import com.caucho.v5.vfs.Crc64OutputStream;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.VfsOld;
import com.caucho.v5.vfs.WriteStream;


/**
 * PodsServiceCluster manages cluster pods by keeping them in sync with their
 * configuration in /config/pods.
 */
class PodsManagerAutoPodImpl
{
  private static final Logger log
    = Logger.getLogger(PodsManagerAutoPodImpl.class.getName());
  
  static final String PATH_AUTOPOD = PodsManagerServiceImpl.PATH_AUTOPOD;
  
  private PodsManagerServiceImpl _podsManager;
  
  private PodBartender _podRoot;

  private long _lastConfigDigest ;
  
  private NodePodAmp _nodeRoot;
  
  public PodsManagerAutoPodImpl(PodsManagerServiceImpl podsManager)
  {
    Objects.requireNonNull(podsManager);
    
    _podsManager = podsManager;
    
    String clusterRootId = "cluster_root";
    
    _podRoot = getBartender().findPod(clusterRootId);
    _nodeRoot = _podRoot.getNode(0);
  }

  BartenderSystem getBartender()
  {
    return _podsManager.getBartender();
  }

  PodsManagerConfigImpl getPodsConfig()
  {
    return _podsManager.getPodsConfig();
  }
  
  private ServerHeartbeat getServerSelf()
  {
    return (ServerHeartbeat) getBartender().serverSelf();
  }
  
  private ClusterHeartbeat getCluster()
  {
    return getServerSelf().getCluster();
  }
  
  private int getServerIndex(ServerHeartbeat server, UpdatePod update)
  {
    String []servers = update.getServers();
    
    for (int i = 0; i < servers.length; i++) {
      if (server.getId().equals(servers[i])) {
        return i;
      }
    }
    
    return -1;
  }

  void update()
  {
    // update /config/pods
    getPodsConfig().update();
    
    // update /config/pods/20-autopod.cf
    updateAutoPod();
    
    // update /config/pods based on 20-autopod.cf
    getPodsConfig().update();
    
    getBartender().getPodHeartbeat().updatePods(getPodsConfig().updatePods());
  }
  
  private void updateAutoPod()
  {
    if (! isOwner()) {
      return;
    }
    
    if (! isHubWritable()) {
      return;
    }
    
    //System.out.println("UPDATE_BFS: " + BartenderSystem.getCurrentSelfServer());
    
    PathImpl path;
    
    String configDir = "/config/pods";
    path = VfsOld.lookup("bfs://" + configDir + "/" + PATH_AUTOPOD);
    
    updateAutoPod(path);
    
  }
  
  private boolean isHubWritable()
  {
    if (! _podsManager.isActive()) {
      return false;
    }
    
    PodBartender pod = getBartender().findActivePod("cluster_hub");
    
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
    
    if (getServerSelf().getSeedIndex() > 0) {
      return true;
    }
    
    // UpdatePod updateHub = _updateMap.get("cluster_hub");
    UpdatePod updateHub = getPodsConfig().getUpdate("cluster_hub");
    
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

  private void updateAutoPod(PathImpl path)
  {
    TempOutputStream tOs = new TempOutputStream();
    Crc64OutputStream crcOs = new Crc64OutputStream(tOs);
    
    try (WriteStream out = VfsOld.openWrite(crcOs)) {
      out.println("# 20-autopod.cf -- generated by " + getClass().getSimpleName());
    
      /*
      for (PodConfig pod : _podsManager.getPodsConfig().getPodConfigs()) {
        updateAutoPod(out, pod);
      }
      */
    } catch (IOException e) {
      log.log(Level.WARNING, e.toString(), e);
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
//      e.printStackTrace();
      throw e;
    }
    
    long crc = crcOs.getDigest();
    
    if (crc == _lastConfigDigest) {
      return;
    }
    
    try (WriteStream out = path.openWrite()) {
      out.writeStream(tOs.getInputStream());
    } catch (IOException e) {
      log.log(Level.WARNING, e.toString(), e);
    }
    
    _lastConfigDigest = crc;
  }
  
  /**
   * Assign servers to a pod.
   * 
   * The autopod servers are configured using their machine ids.
   */
  /*
  private void updateAutoPod(WriteStream out, PodConfig pod)
    throws IOException
  {
    //if (! pod.isLazy()) {
    //  return;
    //}
    
    out.println("# pod " + pod.getName());
      
    int size = getServerCount(pod);
    
    if (pod.getType() == PodType.cluster && size < 0) {
      size = Math.max(pod.getServerCount(), getCluster().getServers().size());
      size = Math.max(3, size);
    }
    
    ArrayList<ServerHeartbeat> serverList = new ArrayList<>();

    allocateServers(serverList, pod, size);
    
    out.println("pod " + pod.getName() + " type=auto {");

    for (ServerHeartbeat server : serverList) {
      if (server != null) {
        out.println("  server { # " + server.getId() + " [" + server.getDisplayName() + "]");
        out.println("    machine-hash \"" + server.getMachineHash() + "\";");
        out.println("  }");
      }
      else {
        out.println("  server {}");
      }
    }
    
    out.println("}");
  }
  */
  
  /**
   * Allocate servers to a pod.
   */
  /*
  private void allocateServers(ArrayList<ServerHeartbeat> serverListNew, 
                               PodConfig pod,
                               int size)
  {
    Comparator<ServerPodsCost> cmp = pod.getCostComparator();
    
    if (cmp == null) {
      cmp = new PodComparator();
    }

    ArrayList<ServerHeartbeat> serverListAlloc
      = allocateServersForPod(pod, cmp);
      
    while (serverListNew.size() < size) {
      ServerPodConfig serverConfig = pod.getServer(serverListNew.size());
      
      ServerHeartbeat server = allocateServer(serverListNew, 
                                              serverListAlloc,
                                              serverConfig);
      
      serverListNew.add(server);
    }
    
    while (serverListNew.size() > 0
           && serverListNew.get(serverListNew.size() - 1) == null) {
      serverListNew.remove(serverListNew.size() - 1);
    }
  }
  */

  /**
   * Allocate a server to a pod.
   */
  /*
  private ServerHeartbeat allocateServer(ArrayList<ServerHeartbeat> serverListNew,
                                         ArrayList<ServerHeartbeat> serverListAlloc,
                                         ServerPodConfig serverConfig)
  {
    for (ServerHeartbeat server : serverListAlloc) {
      if (isServerMatch(serverConfig, server)
          && ! serverListNew.contains(server)) {
        return server;
      }
    }
    
    return null;
  }
  */
  
  /**
   * Calculate an ordered list of servers that can be assigned to the pod.
   * 
   * @param pod The pod that needs servers
   * @param cmp Comparator to order the servers
   */
  /*
  private ArrayList<ServerHeartbeat> 
  allocateServersForPod(PodConfig pod,
                     Comparator<ServerPodsCost> cmp)
  {
    ArrayList<ServerPodsCost> serverCostList = new ArrayList<>();
    
    for (ServerHeartbeat server : getCluster().getServers()) {
      if (! isServerAlloc(server, pod)) {
        continue;
      }
                          
      ServerPodsCost serverCost = new ServerPodsCost(server, pod);
      
      serverCost.setSeedIndex(calculateSeedIndex(server));
      serverCost.setPrimaryCount(calculatePodPrimaryCount(server));
      
      serverCostList.add(serverCost);
    }
    
    Collections.sort(serverCostList, cmp);
    
    ArrayList<ServerHeartbeat> serverList = new ArrayList<>();
    
    for (ServerPodsCost serverCost : serverCostList) {
      serverList.add(serverCost.getServer());
    }
    
    return serverList;
  }
  
  private boolean isServerAlloc(ServerHeartbeat server, PodConfig pod)
  {
    if (server.getPodSet().contains(pod.getName())) {
      return true;
    }
    else if (server.isPodAny()) {
      return true;
    }
    else if (pod.getName().equals("cluster_hub")) {
      return true;
    }
    else {
      return false;
    }
  }
  
  private int calculatePodPrimaryCount(ServerHeartbeat server)
  {
    int count = 0;
    
    for (UpdatePod updatePod : getPodsConfig().getUpdates()) {
      if (updatePod.getPodName().equals("cluster")
          || updatePod.getPodName().equals("cluster_hub")) {
        continue;
      }
      
      if (isPrimaryServer(server, updatePod)) {
        count++;
      }
    }
    
    return count;
  }
  */
  
  private boolean isPrimaryServer(ServerHeartbeat server, UpdatePod update)
  {
    int index = getServerIndex(server, update);
    
    if (index < 0) {
      return false;
    }
    
    for (int i = 0; i < update.getNodeCount(); i++) {
      UpdateNode node = update.getNode(i);
      
      int primary = node.getServers()[0];
      
      if (index == primary) {
        return true;
      }
    }
    
    return false;
  }
  
  /*
  private int calculateSeedIndex(ServerHeartbeat server)
  {
    PodConfig podConfig = getPodsConfig().getPodConfigInit("cluster");
    
    if (podConfig == null) {
      return Integer.MAX_VALUE;
    }
    
    int seedIndex = server.getSeedIndex();
    
    if (seedIndex > 0) {
      return seedIndex;
    }
    else {
      return Integer.MAX_VALUE;
    }
  }

  private boolean isServerMatch(ServerPodConfig serverConfig, 
                                ServerHeartbeat server)
  {
    if (serverConfig == null) {
      return true;
    }
    
    if (serverConfig.getPort() > 0
        && serverConfig.getPort() != server.port()) {
      return false;
    }
    
    if (serverConfig.getAddress() != null
        && ! serverConfig.getAddress().isEmpty()
        && ! serverConfig.getAddress().equals(server.getAddress())
        && ! serverConfig.getAddress().equals(server.getExternalId())) {
      return false;
    }
    
    if (serverConfig.getMachine() != null
        && ! serverConfig.getMachine().isEmpty()
        && ! serverConfig.getMachine().equals(server.getMachineHash())) {
      return false;
    }
    
    return true;
  }
  */
  
  /*
  private int getServerCount(PodConfig pod)
  {
    if (pod.getType() == null) {
      return 3;
    }
    
    switch (pod.getType()) {
    case off:
      return 0;
      
    case solo:
    case lazy:
    case pair:
    case triad:
      return 3;
      
    case cluster:
      if (pod.getSize() > 0) {
        return pod.getSize();
      }
      else if (pod.getServerCount() > 0 && ! pod.getName().equals("cluster")) {
        return pod.getServerCount();
      }
      else {
        return -1;
      }
      
    default:
      return pod.getSize();
    }
  }
  */
  
  /**
   * Check if the current server is the active pods owner for the cluster.
   */
  private boolean isOwner()
  {
    return _nodeRoot.isServerOwner(getServerSelf());
  }

  private static class PodComparator implements Comparator<ServerPodsCost> {
    @Override
    public int compare(ServerPodsCost a, ServerPodsCost b)
    {
      ServerHeartbeat serverA = a.getServer();
      ServerHeartbeat serverB = b.getServer();
      
      int cmp;
      
      cmp = (b.isServerAssigned() ? 1 : 0) - (a.isServerAssigned() ? 1 : 0);

      if (cmp != 0) {
        return cmp;
      }
      
      cmp = a.getPrimaryCount() - b.getPrimaryCount();
      
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
