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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.caucho.v5.bartender.ClusterBartender;
import com.caucho.v5.bartender.ServerBartender;

public class ClusterHeartbeat extends ClusterBartender
{
  private final RootHeartbeat _root;
  
  private RegionHeartbeat []_regions = new RegionHeartbeat[0];
  
  private ConcurrentHashMap<String,RackHeartbeat> _rackMap
    = new ConcurrentHashMap<>();
  
    private ArrayList<ServerHeartbeat> _seedServers = new ArrayList<>();
    
    private ArrayList<ServerHeartbeat> _dynServers = new ArrayList<>();
  
  ClusterHeartbeat(String id,
                   RootHeartbeat root)
  {
    super(id);
    
    Objects.requireNonNull(root);
    
    _root = root;
  }
  
  @Override
  public RootHeartbeat getRoot()
  {
    return _root;
  }
  
  //
  // server management
  //
  
  public ServerHeartbeat createServer(String id)
  {
    int p = id.lastIndexOf(':');
    
    String address = id.substring(0, p);
    int port = Integer.parseInt(id.substring(p + 1));
    
    boolean isSSL = false;
    
    return createServer(address, port, isSSL);
  }
  
  public ServerHeartbeat createServer(String address, int port, boolean isSSL)
  {
    return _root.createServer(address, port, isSSL, 0, this);
  }
  
  public ServerHeartbeat createDynamicServer(String address, int port, 
                                             boolean isSSL)
  {
    return _root.createDynamicServer(address, port, isSSL, this);
  }
  
  public RegionHeartbeat []getRegions()
  {
    return _regions;
  }

  RegionHeartbeat createRegion(int regionIndex)
  {
    if (_regions.length <= regionIndex) {
      RegionHeartbeat []newRegions = new RegionHeartbeat[regionIndex + 1];
      
      System.arraycopy(_regions, 0, newRegions, 0, _regions.length);
      
      _regions = newRegions;
    }
    
    RegionHeartbeat region = _regions[regionIndex];
    
    if (region == null) {
      region = new RegionHeartbeat(id(), regionIndex, this);
      
      _regions[regionIndex] = region;
    }
    
    return region;
  }
  
  @Override
  public List<ServerHeartbeat> getServers()
  {
    return _dynServers;
  }
  
  Iterable<ServerHeartbeat> getSeedServers()
  {
    return _seedServers;
  }

  void addSeedServer(ServerHeartbeat server)
  {
    if (! _seedServers.contains(server)) {
      _seedServers.add(server);
    }
  }
  
  void addDynamicServer(ServerHeartbeat server)
  {
    if (! _dynServers.contains(server)) {
      _dynServers.add(server);
    }
  }
  
  void removeDynamicServer(ServerHeartbeat server)
  {
    _dynServers.remove(server);
  }
  
  public Iterable<RackHeartbeat> getRacks()
  {
    return _rackMap.values();
  }
  
  //
  // queries
  //

  public RackHeartbeat findRack(String id)
  {
    return _rackMap.get(id);
  }

  
  public RegionHeartbeat findRegion(int regionIndex)
  {
    RegionHeartbeat[] regions = _regions;
    
    if (regionIndex < regions.length) {
      return regions[regionIndex];
    }
    else {
      return null;
    }
  }

  public RackHeartbeat findRack(int regionIndex,
                                int podIndex)
  {
    RegionHeartbeat region = findRegion(regionIndex);
    
    if (region != null) {
      return region.findRack(podIndex);
    }
    else {
      return null;
    }
  }

  public RackHeartbeat createRack(String rackId)
  {
    RackHeartbeat rack = _rackMap.get(rackId);
    
    if (rack == null) {
      rack = new RackHeartbeat(rackId, this);
      
      _rackMap.putIfAbsent(rackId, rack);
      
      rack = _rackMap.get(rackId);
    }
    
    return rack;
  }

  public ServerBartender getServer(int regionIndex,
                                   int podIndex, 
                                   int serverIndex)
  {
    RegionHeartbeat region = findRegion(regionIndex);
    
    if (region != null) {
      return region.getServer(podIndex, serverIndex);
    }
    else {
      return null;
    }
  }
  
  @Override
  public ServerBartender findServerByName(String id)
  {
    for (ServerBartender serverBar : _dynServers) {
      ServerHeartbeat server = (ServerHeartbeat) serverBar;
      
      if (id.equals(server.getId())
          || id.equals(server.getExternalId())
          || id.equals(server.getDisplayName())) {
        return server;
      }
    }
    
    for (ServerBartender serverBar : _seedServers) {
      
      ServerHeartbeat server = (ServerHeartbeat) serverBar;
      if (id.equals(server.getId())
          || id.equals(server.getExternalId())
          || id.equals(server.getDisplayName())) {
        return server;
      }
    }
    
    return null;
  }

  public ServerBartender findServerByMachine(String machine)
  {
    if (machine == null || machine.isEmpty()) {
      return null;
    }
    
    for (ServerHeartbeat server : _dynServers) {
      if (machine.equals(server.getMachineHash())) {
        return server;
      }
    }
    
    for (ServerHeartbeat server : _seedServers) {
      if (machine.equals(server.getMachineHash())) {
        return server;
      }
    }
    
    return null;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + id() + "]";
  }
}
