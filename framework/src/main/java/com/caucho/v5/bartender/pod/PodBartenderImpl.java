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

import java.util.Objects;

import com.caucho.v5.bartender.ClusterBartender;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.heartbeat.ClusterHeartbeat;

public class PodBartenderImpl implements PodBartender
{
  private final String _name;
  private final ClusterBartender _cluster;
  private final String _id;
  
  private final PodBartender.PodType _type;
  
  private final ServerBartender []_servers;
  private final NodePod []_nodes;
  private final int _depth;
  
  private final NodePod []_vnodes;

  private String _tag;
  private long _sequence;
  
  private UpdatePod _updatePod;
  
  PodBartenderImpl(UpdatePod updatePod,
                   ClusterHeartbeat cluster)
  {
    _name = updatePod.getPodName();
    
    Objects.requireNonNull(_name);
    Objects.requireNonNull(cluster);
    
    if (_name.indexOf('.') >= 0) {
      throw new IllegalStateException();
    }
    
    _cluster = cluster;
    _type = updatePod.getType();
    
    boolean isOff = _type == PodType.off;
    
    if (_name.equals("local")) {
      _id = "local.local";
    }
    else {
      _id = _name + '.' + _cluster.id();
    }

    _tag = updatePod.getTag();
    
    _depth = updatePod.getDepth();
    
    if (_depth <= 0 && ! isOff) {
      throw new IllegalStateException();
    }
    
    String[] servers = updatePod.getServers();
    
    _servers = new ServerBartender[servers.length];
    
    for (int i = 0; i < servers.length; i++) {
      String serverId = servers[i];

      if (serverId != null && ! "".equals(serverId)) {
        _servers[i] = cluster.createServer(serverId);
      }
      /*
      else {
        _servers[i] = new ServerPod(i);
      }
      */
    }
    
    if (_type == PodType.web) {
      _servers[0] = cluster.getRoot().getServerSelf();
    }
    
    int nodeCount = updatePod.getNodeCount();
    
    if (nodeCount <= 0 && ! isOff) {
      throw new IllegalStateException();
    }
    
    _nodes = new NodePod[nodeCount];
    
    for (int i = 0; i < nodeCount; i++) {
      int []owners = updatePod.getShard(i);
      
      _nodes[i] = new NodePod(this, i, owners);
    }

    _vnodes = new NodePod[updatePod.getVnodeCount()];
    
    for (int i = 0; i < _vnodes.length; i++) {
      int nodeIndex = updatePod.indexOfVnode(i);
      
      _vnodes[i] = _nodes[nodeIndex];
    }
    
    _sequence = updatePod.getSequence();
    _tag = updatePod.getTag();
    
    _updatePod = updatePod;
  }

  @Override
  public boolean isValid()
  {
    return _vnodes.length > 0;
  }

  @Override
  public String getId()
  {
    return _id;
  }
  
  @Override
  public String name()
  {
    return _name;
  }
  
  @Override
  public ServerBartender []getServers()
  {
    return _servers;
  }

  @Override
  public long getSequence()
  {
    return _sequence;
  }

  @Override
  public String getTag()
  {
    return _tag;
  }

  @Override
  public long getCrc()
  {
    return _updatePod.getCrc();
  }
  
  @Override
  public PodBartender.PodType getType()
  {
    return _type;
  }
  
  public String getClusterId()
  {
    return _cluster.id();
  }

  @Override
  public ServerBartender server(int i)
  {
    return _servers[i];
  }
  
  @Override
  public int serverCount()
  {
    return _servers.length;
  }

  @Override
  public int findServerIndex(ServerBartender selfServer)
  {
    return findServerIndex(selfServer.getId());
  }

  @Override
  public int findServerIndex(String serverId)
  {
    ServerBartender []servers = getServers();
    int serverSize = servers.length;
    
    for (int i = 0; i < serverSize; i++) {
      ServerBartender server = servers[i];

      if (server != null && serverId.equals(server.getId())) {
        return i;
      }
    }
    
    return -1;
  }
  
  @Override
  public int nodeCount()
  {
    return _nodes.length;
  }
  
  @Override
  public int getVnodeCount()
  {
    return _vnodes.length;
  }

  @Override
  public NodePod getNode(int hash)
  {
    if (_vnodes.length == 0) {
      // throw new IllegalStateException(L.l("pod {0} is closed", getId()));
      return null;
    }
    
    int vnodeIndex = Math.abs(hash) % _vnodes.length;
    
    NodePod node = _vnodes[vnodeIndex];
    
    return node;
  }

  @Override
  public final int getDepth()
  {
    return _depth;
  }
  
  public int getOwnerIndex(int hash, int index)
  {
    return getNode(hash).owner(index);
  }
  
  public UpdatePod getUpdate()
  {
    return _updatePod;
  }
  
  /**
   * Update the pod when the heartbeat detects server changes.
   */
  /*
  boolean updateFromHeartbeat(PodLocalImpl podService,
                              ServerHeartbeat serverSelf)
  {
    boolean isUpdate = false;

    for (ServerPod serverPod : _servers) {
      //if (serverPod.getServer() != null) {
        // XXX: need to deal with down.
        //continue;
      //}
      
      ServerHeartbeat server = null;
      String hintServerId = serverPod.getHintServerId();

      if (hintServerId != null) {
        server = podService.createServer(hintServerId);
        
        if (server.getRack() == null) {
          server = server.getDelegate();
        }
        
        if (server != null) {
          removeNonHintServer(_servers, server);
        }
      }
      else if (serverPod.getServer() == null
               && _cluster == serverSelf.getCluster()) {
        server = podService.findFreeServer(_servers);
      }

      if (server == null || server == serverPod.getServer()) {
        continue;
      }
      
      serverPod.setServer(server);

      isUpdate = true;
    }

    if (isUpdate) {
      _sequence = nextSequence(podService);
      _updatePod = new UpdatePod(this);
    }
    
    return isUpdate;
  }
  */
  
  /*
  private long nextSequence(PodLocalImpl podService)
  {
    if (podService.isStartValid()) {
      return Math.max(_sequence + 1, CurrentTime.getCurrentTime());
    }
    else {
      return _sequence + 1;
    }
  }
  
  private void removeNonHintServer(ServerPod []servers, 
                                   ServerHeartbeat server)
  {
    // if a server is discovered to be a hint server, remove any instances
    // where it was used as a dynamic server.
    
    for (ServerPod serverPod : servers) {
      if (serverPod.getServer() == server 
          && serverPod.getHintServerId() == null) {
        serverPod.setServer(null);
      }
    }
  }
  */

  /**
   * Update the pod from an update call from a peer server.
   */
  /*
  private boolean onUpdateFromPeer(UpdatePod updatePod, 
                        PodLocalImpl podServiceImpl)
  {
    if (! isUpdate(updatePod)) {
      return false;
    }
    
    boolean isUpdate = false;
    
    String []updateServers = updatePod.getServers();
    
    for (int i = 0; i < updateServers.length; i++) {
      ServerPod serverPod = _servers[i];
      ServerBartender server = serverPod.getServer();
      
      String serverIdUpdate = updateServers[i];
      
      boolean isServerUpdate = false;
      
      if (serverIdUpdate == null || "".equals(serverIdUpdate)) {
        // if update doesn't specify a server, use the current one
      }
      else if (server == null) {
        // if current server is unset, use the update
        isServerUpdate = true;
      }
      else if (! server.getId().equals(serverIdUpdate)) {
        isServerUpdate = true;
      }
      
      if (isServerUpdate) {
        server = podServiceImpl.createServer(serverIdUpdate);
        // XXX: update tests
        serverPod.setServer(server);
          
        isUpdate = true;
      }
      
      for (int j = 0; j < _servers.length; j++) {
        if (i != j) {
          serverPod = _servers[j];
        
          // remove duplicate servers
          if (serverIdUpdate.equals(serverPod.getServerId())) {
            serverPod.setServer(null);
            isUpdate = true;
          }
        }
      }
    }
    
    _sequence = updatePod.getSequence();
    
    if (isUpdate) {
      //System.out.println("UP2: " + this);
      //_sequence = Math.max(_sequence + 1, CurrentTime.getCurrentTime());
      _updatePod = new UpdatePod(this);
    }

    return isUpdate;
  }
  */
  
  /*
  private boolean isUpdate(UpdatePod updateNew)
  {
    UpdatePod updateSelf = getUpdate();
    
    if (updateSelf.getSequence() < updateNew.getSequence()) {
      return true;
    }
    else if (updateNew.getSequence() < updateSelf.getSequence()) {
      return false;
    }
    else {
      return updateSelf.getCrc() < updateNew.getCrc();
    }
  }
  */
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName() + "[" + name() + "." + getClusterId() + "]");
  }
}
