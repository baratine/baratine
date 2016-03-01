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
import java.util.Objects;

import com.caucho.v5.bartender.BartenderBuilderPod;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.bartender.pod.PodBartender.PodType;
import com.caucho.v5.util.L10N;


public class PodBuilderConfig extends BartenderBuilderPod
{
  private static final L10N L = new L10N(PodBuilderConfig.class);
  
  private final BartenderBuilderHeartbeat _builder;
  private final ClusterHeartbeat _cluster;
  private final String _id;
  
  private PodBartender.PodType _type = PodBartender.PodType.lazy;
  private ArrayList<ServerBartender> _serverList = new ArrayList<>();
  
  //private PodConfig _podConfig;
  
  public PodBuilderConfig(String id,
                          ClusterHeartbeat cluster,
                          BartenderBuilderHeartbeat builder)
  {
    Objects.requireNonNull(id);
    Objects.requireNonNull(cluster);
    Objects.requireNonNull(builder);
    
    _id = id;
    _cluster = cluster;
    _builder = builder;
    
    //_podConfig = new PodConfig();
    //_podConfig.setName(_id);
  }

  public String getId()
  {
    return _id;
  }

  public ClusterHeartbeat getCluster()
  {
    return _cluster;
  }

  public PodType getType()
  {
    return _type;
  }

  @Override
  public void server(String address, int port, boolean isSSL,
                     String displayName, boolean isDynamic)
  {
    ServerHeartbeat server;
    
    server = _builder.server(address, port, isSSL,
                             _cluster.id(), displayName, isDynamic);
    
    _serverList.add(server);
    
    /*
    ServerPodConfig serverConfig = new ServerPodConfig();
    serverConfig.setAddress(address);
    serverConfig.setPort(port);
    
    _podConfig.addServer(serverConfig );
    */
  }

  public ArrayList<ServerBartender> getServers()
  {
    return _serverList;
  }

  @Override
  public void type(PodType type)
  {
    Objects.requireNonNull(type);
    
    _type = type;
    
    //_podConfig.setType(type);
  }
  
  @Override
  public void build()
  {
    _builder.addPod(this);
    
    //_builder.addPod(_podConfig);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _id + "]";
  }
}
