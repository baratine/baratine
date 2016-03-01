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

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

import com.caucho.v5.bartender.ServerBartender;

public class RackHeartbeat
{
  private final String _id;
  private final ClusterHeartbeat _cluster;
  
  private RegionHeartbeat _region;
  
  private ServerHeartbeat[]_servers = new ServerHeartbeat[0];
  private int _serverLength;
  
  private UpdateRackHeartbeat _update;
  
  RackHeartbeat(String id,
                ClusterHeartbeat cluster)
  {
    Objects.requireNonNull(cluster);
    Objects.requireNonNull(id);
    
    _cluster = cluster;
    _id = id;
    
    _update = new UpdateRackHeartbeat(this);
  }
  
  public String getId()
  {
    return _id;
  }
  
  public ClusterHeartbeat getCluster()
  {
    return _cluster;
  }

  /*
  public int getIndex()
  {
    return _index;
  }
  */
  
  public RegionHeartbeat getRegion()
  {
    return _region;
  }

  /*
  public ClusterHeartbeat getCluster()
  {
    return getRegion().getCluster();
  }
  */
  
  public RootHeartbeat getRoot()
  {
    return getCluster().getRoot();
  }

  public ServerHeartbeat[]getServers()
  {
    return _servers;
  }

  public int getServerLength()
  {
    return _serverLength;
  }

  public ServerHeartbeat getServer(int i)
  {
    ServerHeartbeat[] servers = _servers;
    
    if (i < servers.length) {
      return servers[i];
    }
    else {
      return null;
    }
  }

  ServerHeartbeat createServer(String address, int port, boolean isSSL)
  {
    ServerHeartbeat server = findServer(address, port);

    if (server != null) {
      return server;
    }
    
    ServerHeartbeat []servers = _servers;
    
    int serverIndex = findFreeServerIndex();
    
    if (serverIndex < servers.length) {
      server = servers[serverIndex];
    }
    
    if (server == null) {
      ClusterHeartbeat cluster = getCluster();
      
      server = cluster.createServer(address, port, isSSL);
      server.toKnown();
      
      _serverLength = Math.max(serverIndex + 1, _serverLength);
      
      if (servers.length < _serverLength) {
        servers = new ServerHeartbeat[_serverLength];
        
        System.arraycopy(_servers, 0, servers, 0, _servers.length);
        _servers = servers;
      }
      
      servers[serverIndex] = server;
      
      // canonical sorted order for all servers in the rack
      Arrays.sort(servers, new ServerComparator());
      
      for (int i = 0; i < servers.length; i++) {
        ServerHeartbeat serverRack = servers[i];
        
        serverRack.setRack(this, i);
      }
      
      getCluster().addDynamicServer(server);

      /*
      getRoot().addServer(server);
      getCluster().addServer(server);
      
      server.initServiceRef();
      */
    }
    
    server.toKnown();
    getCluster().getRoot().updateSequence();
    
    update();

    return server;
  }

  ServerHeartbeat createServer(UpdateServerHeartbeat serverUpdate)
  {
    boolean isSSL = false;
    
    return createServer(serverUpdate.getAddress(), 
                        serverUpdate.getPort(),
                        isSSL);
  }
  
  ServerHeartbeat addDynamicServer(String address, int port, boolean isSSL,
                                   String displayName)
  {
    ServerHeartbeat server = findServer(address, port);

    if (server != null) {
      return server;
    }
    
    server = createServer(address, port, isSSL);
    server.setDisplayName(displayName);    
    // push update
    
    update();
    
    return server;
  }

  int getNextServerIndex()
  {
    return findFreeServerIndex();
  }
  
  private int findFreeServerIndex()
  {
    for (int i = 0; i < _servers.length; i++) {
      if (_servers[i] == null) {
        return i;
      }
    }
    
    return _servers.length;
  }

  ServerHeartbeat findServer(String address, int port)
  {
    for (ServerHeartbeat server : getServers()) {
      if (server == null) {
        continue;
      }
      
      if (address.equals(server.getAddress()) && port == server.port()) {
        return server;
      }
    }
    return null;
  }

  ServerHeartbeat findServer(String id)
  {
    for (ServerHeartbeat server : getServers()) {
      if (server == null) {
        continue;
      }
      
      if (id.equals(server.getId())) {
        return server;
      }
    }
    return null;
  }
  
  private void removeServer(ServerHeartbeat server)
  {
    int len = _servers.length;
    
    for (int i = 0; i < len; i++) {
      if (_servers[i] == server) {
        getCluster().removeDynamicServer(server);
        
        _servers[i] = null;
      }
    }
  }
  
  /**
   * Update the rack with a heartbeat message.
   */
  void updateRack(HeartbeatImpl heartbeat,
                  UpdateRackHeartbeat updateRack)
  {
    for (UpdateServerHeartbeat serverUpdate : updateRack.getServers()) {
      if (serverUpdate == null) {
        continue;
      }
 
      update(serverUpdate);
      // updateTargetServers();
 
      ServerHeartbeat peerServer = findServer(serverUpdate.getAddress(),
                                              serverUpdate.getPort());
 
      if (peerServer.isSelf()) {
        continue;
      }
      
      heartbeat.updateServer(peerServer, serverUpdate);
      /*
      String externalId = serverUpdate.getExternalId();

      heartbeat.updateExternal(peerServer, externalId);

      if (peerServer.onHeartbeatUpdate(serverUpdate)) {
        if (peerServer.isUp()) {
          onServerStart(peerServer);
        }
        else {
          onServerStop(peerServer);
        }
      }
      */
    }

    update();
  }

  void update(UpdateServerHeartbeat serverUpdate)
  {
    ServerHeartbeat server = findServer(serverUpdate.getAddress(),
                                        serverUpdate.getPort());
    
    if (server != null) {
      server.setDisplayName(serverUpdate.getDisplayName());

      /*
      if (serverUpdate.getExternalId() != null) {
        server.setExternalId(serverUpdate.getExternalId());
      }
      */
      
      if (serverUpdate.isUp() != server.isUp()) {
        update();
      }
    }
    else {
      server = createServer(serverUpdate);
    }
    
    String machineHash = server.getMachineHash();

    if (! server.isUp() && findServerByHash(machineHash, true) != null) {
      removeServer(server);
      update();
    }

    if (server.isUp()) {
      ServerHeartbeat oldServer = findServerByHash(machineHash, false);
      
      if (oldServer != null) {
        removeServer(oldServer);
        update();
      }
    }
  }
  
  private ServerHeartbeat findServerByHash(String machineHash,
                                           boolean isUp)
  {
    if (machineHash == null || machineHash.isEmpty()) {
      return null;
    }
    
    for (ServerHeartbeat server : _servers) {
      if (server.getMachineHash() .equals(machineHash)
          && server.isUp() == isUp) {
        return server;
      }
    }
    
    return null;
  }
  
  void update()
  {
    _update = new UpdateRackHeartbeat(this);
  }
  
  public UpdateRackHeartbeat getUpdate()
  {
    return _update;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _id + "]";
  }
  
  private static class ServerComparator implements Comparator<ServerBartender>
  {
    @Override
    public int compare(ServerBartender a, ServerBartender b)
    {
      return a.getId().compareTo(b.getId());
    }
  }
}
