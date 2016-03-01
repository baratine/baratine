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

import com.caucho.v5.bartender.ServerBartender;

public class RegionHeartbeat
{
  private String _clusterId;
  private int _index;
  
  private String _id;
  
  private ClusterHeartbeat _cluster;
  
  private RackHeartbeat []_pods = new RackHeartbeat[0];
  private int _podLength;
  
  RegionHeartbeat(String clusterName, int index, ClusterHeartbeat cluster)
  {
    _clusterId = clusterName;
    _index = index;
    
    _id = "r" + _index + "." + _clusterId;
    
    _cluster = cluster;
  }
  
  public String getId()
  {
    return _id;
  }
  
  public String getClusterId()
  {
    return _clusterId;
  }
  
  public int getIndex()
  {
    return _index;
  }
  
  public ClusterHeartbeat getCluster()
  {
    return _cluster;
  }
  
  public RootHeartbeat getRoot()
  {
    return getCluster().getRoot();
  }

  public RackHeartbeat[]getPods()
  {
    return _pods;
  }

  public int getPodLength()
  {
    return _podLength;
  }

  public RackHeartbeat findRack(int index)
  {
    RackHeartbeat[] pods = _pods;
    
    if (index < pods.length) {
      return pods[index];
    }
    else {
      return null;
    }
  }

  RackHeartbeat createRack(int index)
  {
    RackHeartbeat pod = null;
    
    if (index < _pods.length) {
      pod = _pods[index];
    }
    
    if (pod == null) {
      pod = _cluster.createRack("r" + index);
      
      _podLength = Math.max(index + 1, _podLength);
      
      if (_pods.length < _podLength) {
        RackHeartbeat []pods = new RackHeartbeat[_podLength];
        
        System.arraycopy(_pods, 0, pods, 0, _pods.length);
        _pods = pods;
      }
      
      _pods[index] = pod;
      
      // getCluster().addRack(pod);
    }

    return pod;
  }

  ServerBartender findServer(String address, int port)
  {
    for (RackHeartbeat pod : getPods()) {
      if (pod == null) {
        continue;
      }
      
      ServerBartender server = pod.findServer(address, port);
      
      if (server != null) {
        return server;
      }
    }
    
    return null;
  }

  ServerBartender getServer(int podIndex, int serverIndex)
  {
    RackHeartbeat pod = findRack(podIndex);
    
    if (pod != null) {
      return pod.getServer(serverIndex);
    }
    
    return null;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _id + "]";
  }
}
