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

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.caucho.v5.bartender.RootBartender;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.util.CurrentTime;

public class RootHeartbeat extends RootBartender 
{
  private final ConcurrentHashMap<String,ClusterHeartbeat> _clusterMap
    = new ConcurrentHashMap<>();
    
  private final ConcurrentHashMap<String,ServerHeartbeat> _serverMap
    = new ConcurrentHashMap<>();
    
  private final ConcurrentHashMap<String,ServerHeartbeat> _serverNameMap
    = new ConcurrentHashMap<>();
    
  private final ServerSelf _serverSelf;

  private long _updateSequence;
  
  RootHeartbeat(String addressSelf,
                int portSelf,
                boolean isSSL,
                int portBartender,
                String clusterSelf,
                String displayNameSelf,
                String machineHashSelf,
                ServerHeartbeatBuilder builder)
  {
    ClusterHeartbeat cluster = createCluster(clusterSelf);
    
    _serverSelf = new ServerSelf(addressSelf,
                                 portSelf,
                                 isSSL,
                                 portBartender,
                                 cluster,
                                 displayNameSelf,
                                 machineHashSelf,
                                 builder);
    
    _serverMap.put(_serverSelf.getId(), _serverSelf);
    
    RackHeartbeat rack = cluster.createRack("rack");
    
    rack.createServer(addressSelf, portSelf, isSSL);
    
    ServerHeartbeat serverLoopback = cluster.createServer("127.0.0.1", portSelf, isSSL);
    serverLoopback.setDelegate(_serverSelf);
    serverLoopback.setPortBartender(portBartender);
  }

  @Override
  public ServerSelf getServerSelf()
  {
    return _serverSelf;
  }
  
  //
  // server management
  //
  
  @Override
  public Iterable<ServerHeartbeat> getServers()
  {
    return _serverMap.values(); 
  }

  /**
   * Returns the server with the given canonical id.
   */
  @Override
  public ServerHeartbeat getServer(String serverId)
  {
    ServerHeartbeat server = _serverMap.get(serverId);
    
    return server;
  }

  /**
   * Returns the server with the given display name.
   */
  @Override
  public ServerBartender findServerByName(String name)
  {
    for (ClusterHeartbeat cluster : _clusterMap.values()) {
      ServerBartender server = cluster.findServerByName(name);
      
      if (server != null) {
        return server;
      }
    }
    
    return null;
  }
  
  ServerHeartbeat createServer(String address, 
                               int port, 
                               boolean isSSL,
                               int portBartender,
                               ClusterHeartbeat cluster)
  {
    ServerHeartbeatBuilder builder = new ServerHeartbeatBuilder();
    
    return createServer(address, port, isSSL, portBartender, cluster, 
                        builder);
  }
  
  ServerHeartbeat createDynamicServer(String address, 
                                      int port,
                                      boolean isSSL,
                                      ClusterHeartbeat cluster)
  {
    ServerHeartbeatBuilder builder = new ServerHeartbeatBuilder();
    builder.dynamic(true);
    
    return createServer(address, port, isSSL, 0, cluster, builder);
  }
  
  ServerHeartbeat createServer(String address, 
                               int port,
                               boolean isSSL,
                               int portBartender,
                               ClusterHeartbeat cluster,
                               ServerHeartbeatBuilder builder)
  {
    Objects.requireNonNull(address);
    
    if (address.isEmpty()) {
      throw new IllegalArgumentException("Invalid address: port={0}" + port); 
    }
    
    String id = address + ":" + port;
    
    ServerHeartbeat server = _serverMap.get(id);
    
    if (server == null) {
      server = new ServerHeartbeat(address, port, isSSL, 
                                   portBartender, cluster, null, 
                                   builder);
      
      _serverMap.putIfAbsent(id, server);
      
      server = _serverMap.get(id);
      
      updateSequence();
    }
    
    return server;
  }
  
  //
  // cluster managment.
  //
  
  @Override
  public Iterable<? extends ClusterHeartbeat> getClusters()
  {
    return _clusterMap.values();
  }

  /**
   * Returns the cluster with the given name, creating it if necessary.
   */
  ClusterHeartbeat createCluster(String clusterName)
  {
    ClusterHeartbeat cluster = _clusterMap.get(clusterName);
    
    if (cluster == null) {
      cluster = new ClusterHeartbeat(clusterName, this);
      
      _clusterMap.putIfAbsent(clusterName, cluster);
      
      cluster = _clusterMap.get(clusterName);
    }
    
    return cluster;
  }
  
  void addServer(ServerHeartbeat server)
  {
    _serverMap.put(server.getId(), server);
    _serverNameMap.put(server.getDisplayName(), server);
    
    updateSequence();
  }
  
  public long getSequence()
  {
    return _updateSequence;
  }
  
  void updateSequence()
  {
    _updateSequence = Math.max(_updateSequence + 1, CurrentTime.getCurrentTime());
  }


  @Override
  public ServerHeartbeat findServer(String address, int port)
  {
    for (ServerHeartbeat server : _serverMap.values()) {
      if (address.equals(server.getAddress())
          && port == server.port()) {
        return server;
      }
    }
    
    return null;
  }
  
  @Override
  public ClusterHeartbeat findCluster(String clusterName)
  {
    ClusterHeartbeat cluster = _clusterMap.get(clusterName);
    
    return cluster;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
