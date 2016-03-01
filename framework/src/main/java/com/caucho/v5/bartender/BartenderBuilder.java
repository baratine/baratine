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

import com.caucho.v5.bartender.heartbeat.HeartbeatService;
import com.caucho.v5.bartender.heartbeat.HeartbeatServiceLocal;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.bartender.pod.PodHeartbeatService;
import com.caucho.v5.bartender.pod.PodLocalService;

abstract public class BartenderBuilder
{
  private boolean _isFileSystemEnabled;

  abstract public ClusterBartender cluster(String name);
  
  abstract public ClusterBartender getCluster(String clusterId);
  
  abstract public ServerBartender server(String address,
                                         int port,
                                         boolean isSSL,
                                         String clusterName,
                                         String displayName,
                                         boolean isDynamic);
  
  abstract public ServerBartender serverDyn(String address,
                                         int port,
                                         boolean isSSL,
                                         String clusterName,
                                         String displayName);
  
  abstract public BartenderBuilderPod pod(String id,
                                          String clusterName);
  
  public BartenderBuilder fileSystemEnabled(boolean isEnabled)
  {
    _isFileSystemEnabled = isEnabled;
    
    return this;
  }
  
  public boolean isFileSystemEnabled()
  {
    return _isFileSystemEnabled;
  }
  
  //
  // build method
  //
  
  abstract public BartenderSystem build();
  /*
  {
    return BartenderSystem.createAndAddSystem(this);
  }
  */
  
  //
  // impl methods
  
  abstract protected void build(BartenderSystem bartenderSystem);
  abstract protected void buildPods();
  
  abstract protected Map<String,PodBartender> podMap();
  
  abstract protected RootBartender getRoot();
  
  abstract protected ServerBartender getServerSelf();
  
  abstract protected HeartbeatService getHeartbeat();
  abstract protected HeartbeatServiceLocal getHeartbeatLocal();
  
  abstract protected PodHeartbeatService getPodHeartbeat();
  // abstract protected PodManagerService getPodManager();
  abstract protected PodLocalService getPodService();

  
  // abstract protected JoinClient buildJoinClient();

}
