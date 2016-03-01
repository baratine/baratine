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

import java.util.Map;

import com.caucho.v5.bartender.heartbeat.HeartbeatImpl;
import com.caucho.v5.bartender.heartbeat.HeartbeatLocalImpl;
import com.caucho.v5.bartender.heartbeat.HeartbeatService;
import com.caucho.v5.bartender.heartbeat.HeartbeatServiceLocal;
import com.caucho.v5.bartender.link.SchemeBartenderBase;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.bartender.pod.PodBartenderProxy;
import com.caucho.v5.bartender.pod.PodHeartbeatService;
import com.caucho.v5.bartender.pod.PodLocalService;
import com.caucho.v5.bartender.pod.PodManagerService;
import com.caucho.v5.bartender.pod.PodsManagerServiceLocal;
import com.caucho.v5.subsystem.SystemManager;

public class BartenderSystemBare extends BartenderSystem
{
  private ServerBartender _serverSelf;
  private RootBartender _root;
  private ClusterBartender _cluster;
  private Map<String, PodBartender> _podMap;

  protected BartenderSystemBare(BartenderBuilder builder)
  {
    _serverSelf = builder.getServerSelf();
    
    builder.build(this);
    
    _root = builder.getRoot();
    _cluster = _root.findCluster("cluster");
    
    builder.buildPods();
    
    _podMap = builder.podMap();
    /*
    _heartbeat = builder.getHeartbeat();
    _heartbeatLocal = builder.getHeartbeatLocal();
    
    _podHeartbeat = builder.getPodHeartbeat();
    _podService = builder.getPodService();
    // _podManager = builder.getPodManager();
    
    builder.buildPods();
    
    // _joinClientImpl = builder.buildJoinClient();
    
    _isFileSystemEnabled = builder.isFileSystemEnabled();
    
    _linkSystem = LinkBartenderSystem.createAndAddSystem(this);
    
    ServiceManagerAmp ampManager = AmpSystem.getCurrentManager();

    new SchemePodSystem(this, ampManager).bind("pod:");
    */
  }

  /**
   * Creates a new network cluster service.
   */
  public static BartenderSystem
  createAndAddSystem(BartenderBuilder builder)
  {
    BartenderSystemBare systemBartender
      = new BartenderSystemBare(builder);
    
    SystemManager system = preCreate(BartenderSystem.class);

    system.addSystem(BartenderSystem.class, systemBartender);
    
    return systemBartender;
  }

  @Override
  public boolean isFileSystemEnabled()
  {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public HeartbeatServiceLocal getService()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public RootBartender getRoot()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public PodHeartbeatService getPodHeartbeat()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public PodLocalService getPodService()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public PodsManagerServiceLocal getPodsClusterService()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public PodBartender findPod(String podName)
  {
    String id;
    
    if (podName.indexOf('.') >= 0) {
      id = podName;
    }
    else if (podName.equals("local")) {
      id = "local.local";
    }
    else {
      id = podName + "." + _cluster.id();
    }
    
    PodBartender pod = _podMap.get(id);
    
    if (pod != null) {
      return pod;
    }
    else {
      return null;
    }
  }

  @Override
  public PodBartender findActivePod(String podName)
  {
    return _podMap.get(podName);
  }

  @Override
  public SchemeBartenderBase getSchemeBartenderPod()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  HeartbeatService getHeartbeatService()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public HeartbeatServiceLocal getHeartbeatLocal()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  HeartbeatImpl getHeartbeatServiceImpl()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ServerBartender findServerByName(String name)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ServerBartender getServerHandle(String address, int port)
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ClusterBartender findCluster(String clusterId)
  {
    if (clusterId.equals(_cluster.id())) {
      return _cluster;
    }
    else {
      return null;
    }
  }

  @Override
  protected HeartbeatLocalImpl getBartenderServiceImpl()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public PodManagerService getPodManagerHub()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ServerBartender serverSelf()
  {
    return _serverSelf;
  }
}
