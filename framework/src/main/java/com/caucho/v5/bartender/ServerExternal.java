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

package com.caucho.v5.bartender;

import java.util.Objects;

public class ServerExternal
{
  private final String _address;
  private final int _port;
  
  private final ClusterBartender _cluster;
  
  private final String _id;
  private String _displayName;
  
  private ServerBartender _server;
  
  public ServerExternal(String address,
                        int port,
                        ClusterBartender cluster)
  {
    Objects.requireNonNull(address);
    Objects.requireNonNull(cluster);
    
    _address = address;
    _port = port;
    
    _id = getAddress() + ":" + getPort();
    _displayName = _id;
    
    _cluster = cluster;
  }
  
  public String getId()
  {
    return _id;
  }

  public String getDisplayName()
  {
    return _displayName;
  }
  
  public ClusterBartender getCluster()
  {
    return _cluster;
  }
  
  public String getAddress()
  {
    if (_address != null && ! "".equals(_address)) {
      return _address;
    }
    else {
      return "127.0.0.1";
    }
  }
  
  public String getBindAddress()
  {
    return _address;
  }
  
  public int getPort()
  {
    return _port;
  }
  
  public ServerBartender getServer()
  {
    return _server;
  }
  
  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    
    sb.append(getClass().getSimpleName()); 
    sb.append("[");

    if (! getDisplayName().equals(getId())) {
      sb.append(getDisplayName());
      sb.append(",");
    }
    
    sb.append(getId());
    // sb.append(",").append(_state);
    sb.append("]");
    
    return sb.toString();
  }
}
