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

import com.caucho.v5.bartender.heartbeat.BartenderBuilderHeartbeat;
import com.caucho.v5.bartender.heartbeat.HeartbeatImpl;
import com.caucho.v5.bartender.heartbeat.HeartbeatLocalImpl;
import com.caucho.v5.bartender.heartbeat.HeartbeatService;
import com.caucho.v5.bartender.heartbeat.HeartbeatServiceLocal;
import com.caucho.v5.bartender.heartbeat.ServerHeartbeatBuilder;
import com.caucho.v5.bartender.link.SchemeBartenderBase;
import com.caucho.v5.bartender.pod.NodePodAmp;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.bartender.pod.PodHeartbeatService;
import com.caucho.v5.bartender.pod.PodLocalService;
import com.caucho.v5.bartender.pod.PodManagerService;
import com.caucho.v5.bartender.pod.PodsManagerServiceLocal;
import com.caucho.v5.loader.EnvironmentLocal;
import com.caucho.v5.subsystem.SubSystemBase;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.util.L10N;

import io.baratine.config.Config;

abstract public class BartenderSystem extends SubSystemBase
{
  private static final L10N L = new L10N(BartenderSystem.class);
  
  public static final int START_PRIORITY = START_PRIORITY_HEARTBEAT;
  
  private static final EnvironmentLocal<PodBartender> _localPod
    = new EnvironmentLocal<>();
    
  private static final EnvironmentLocal<NodePodAmp> _localShard
    = new EnvironmentLocal<>();
  
  protected BartenderSystem()
  {
  }
  
  public static BartenderSystem current()
  {
    return SystemManager.getCurrentSystem(BartenderSystem.class);
  }
  
  public static BartenderBuilder newSystem(Config config,
                                               String address,
                                               int port,
                                               boolean isSSL,
                                               int portBartender,
                                               String clusterId,
                                               String displayName,
                                               int machinePort,
                                               ServerHeartbeatBuilder selfBuilder)
  {
    BartenderSystem system = current();
    
    if (system != null) {
      throw new IllegalStateException(L.l("Can't create {0} when a {1} exists",
                                          BartenderBuilder.class.getName(),
                                          BartenderSystem.class.getName()));
    }
    
    return new BartenderBuilderHeartbeat(config, address, port, isSSL, portBartender,
                                         clusterId, displayName,
                                         machinePort,
                                         selfBuilder);
  }
  
  public static BartenderBuilder newSystem(Config config)
  {
    BartenderSystem system = current();
    
    if (system != null) {
      throw new IllegalStateException(L.l("Can't create {0} when a {1} exists",
                                          BartenderBuilder.class.getName(),
                                          BartenderSystem.class.getName()));
    }
    
    return new BartenderBuilderHeartbeat(config);
  }
  
  public static ServerBartender getCurrentSelfServer()
  {
    BartenderSystem bartender = current();
    
    if (bartender != null) {
      return bartender.serverSelf();
    }
    else {
      return null;
    }
  }
  
  public static PodBartender getCurrentPod()
  {
    BartenderSystem bartender = current();
    
    if (bartender != null) {
      return bartender.getLocalPod();
    }
    else {
      return null;
    }
  }
  
  public static PodBartender getCurrentPod(ClassLoader loader)
  {
    BartenderSystem bartender = current();
    
    if (bartender != null) {
      return bartender.getLocalPod(loader);
    }
    else {
      return null;
    }
  }
  
  public static NodePodAmp getCurrentShard()
  {
    BartenderSystem bartender = current();
    
    if (bartender != null) {
      return bartender.getLocalShard();
    }
    else {
      return null;
    }
  }

  abstract public boolean isFileSystemEnabled();
  
  public PodBartender getLocalPod()
  {
    PodBartender pod = _localPod.get();
    
    if (pod != null) {
      return pod;
    }
    else {
      return findPod("cluster");
    }
  }
  
  public PodBartender getLocalPod(ClassLoader loader)
  {
    PodBartender pod = _localPod.get(loader);
    
    if (pod != null) {
      return pod;
    }
    else {
      return findPod("cluster");
    }
  }
  
  public void setLocalPod(PodBartender pod)
  {
    _localPod.set(pod);
  }
  
  public NodePodAmp getLocalShard()
  {
    NodePodAmp shard = _localShard.get();
    
    if (shard != null) {
      return shard;
    }
    else {
      return null;
    }
  }
  
  public void setLocalShard(NodePodAmp shard, ClassLoader loader)
  {
    _localShard.set(shard, loader);
  }
  
  public boolean isClosed()
  {
    return false;
  }
  
  abstract public HeartbeatServiceLocal getService();

  
  /*
  public FailoverService getFailoverService()
  {
    return _failoverService;
  }
  */
  
  abstract public RootBartender getRoot();

  abstract public PodHeartbeatService getPodHeartbeat();
  
  abstract public PodLocalService getPodService();
  
  abstract public PodsManagerServiceLocal getPodsClusterService();
  
  abstract public PodBartender findPod(String podName);

  abstract public PodBartender findActivePod(String podName);
  
  abstract public SchemeBartenderBase schemeBartenderPod();

  /*
  protected ServiceRef getServiceRef()
  {
    return _serviceRef;
  }
  */
  
  /**
   * Return the champ scheme for pod messages.
   */
  /*
  public SchemeBartenderBase getSchemeBartenderPod()
  {
    return _linkSystem.getSchemeBartenderPod();
  }
  */
  
  @Override
  public int getStartPriority()
  {
    return START_PRIORITY;
  }
  
  abstract HeartbeatService getHeartbeatService();

  abstract public HeartbeatServiceLocal getHeartbeatLocal();
  
  abstract HeartbeatImpl getHeartbeatServiceImpl();

  abstract public ServerBartender findServerByName(String name);

  abstract public ServerBartender getServerHandle(String address, 
                                                  int port);

  abstract public ClusterBartender findCluster(String clusterId);
  
  abstract protected HeartbeatLocalImpl getBartenderServiceImpl();

  abstract public PodManagerService getPodManagerHub();
  
  abstract public ServerBartender serverSelf();
}
