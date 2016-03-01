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

import java.io.Serializable;
import java.util.Objects;

import com.caucho.v5.util.Crc64;

@SuppressWarnings("serial")
public class UpdateRackHeartbeat implements Serializable
{
  private final String _id;
  private final String _clusterId;
  private final UpdateServerHeartbeat []_servers;
  
  private final long _sequence;
  private final long _crc;
  
  public UpdateRackHeartbeat(RackHeartbeat rack)
  {
    _id = rack.getId();
    _clusterId = rack.getCluster().id();
    
    long crc = 0;
    
    crc = Crc64.generate(crc, _id);
    crc = Crc64.generate(crc, _clusterId);
    
    ServerHeartbeat []servers = rack.getServers();
    
    _servers = new UpdateServerHeartbeat[servers.length];
    
    for (int i = 0; i < _servers.length; i++) {
      ServerHeartbeat server = servers[i];
      
      if (server != null) {
        _servers[i] = server.getUpdate();
      }
      else {
        _servers[i] = new UpdateServerHeartbeat();
      }
      
      crc = Crc64.generate(crc, _servers[i].getCrc());
    }
    
    _sequence = 0;
    _crc = crc;
  }
  
  /*
  public UpdateRackHeartbeat(String id,
                             String clusterId,
                              UpdateServerHeartbeat []servers,
                              long sequence)
  {
    //Objects.requireNonNull(rack);
    Objects.requireNonNull(id);
    Objects.requireNonNull(clusterId);
    
    _id = id;
    _clusterId = clusterId;
    _servers = servers;
    _sequence = sequence;
    
    long crc = 0;
    
    crc = Crc64.generate(crc, id);
    crc = Crc64.generate(crc, clusterId);
    
    for (UpdateServerHeartbeat server : servers) {
      crc = Crc64.generate(crc, server.getCrc());
    }
    
    _crc = crc;
  }
  */
  
  public String getId()
  {
    return _id;
  }
  
  public String getClusterId()
  {
    return _clusterId;
  }
  
  public UpdateServerHeartbeat []getServers()
  {
    return _servers;
  }

  public long getSequence()
  {
    return _sequence;
  }

  public long getCrc()
  {
    return _crc;
  }
  
  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    
    sb.append(getClass().getSimpleName()); 
    sb.append("[");

    sb.append(getId());
    
    sb.append("]");
    
    return sb.toString();
  }
}
