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

import io.baratine.service.OnActive;
import io.baratine.service.Result;

import java.util.Objects;

import com.caucho.v5.amp.Direct;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.ClusterBartender;
import com.caucho.v5.bartender.ServerBartender;

public class HeartbeatLocalImpl
{
  private final ServerBartender _serverSelf;
  
  private final HeartbeatImpl _heartbeatImpl;
  
  HeartbeatLocalImpl(BartenderSystem bartender,
                     HeartbeatImpl heartbeatImpl)
  {
    Objects.requireNonNull(bartender);
    Objects.requireNonNull(heartbeatImpl);

    _serverSelf = bartender.serverSelf();
    
    Objects.requireNonNull(_serverSelf);
    
    _heartbeatImpl = heartbeatImpl;
  }
  
  public RackHeartbeat getRack()
  {
    return getHeartbeatService().getRack();
  }

  public Iterable<? extends ClusterBartender> getClusters()
  {
    return getHeartbeatService().getClusters();
  }

  @Direct
  public ClusterBartender findCluster(String clusterId)
  {
    return getHeartbeatService().findCluster(clusterId);
  }

  @Direct
  public ServerHeartbeat getServerHandle(String address, int port, boolean isSSL)
  {
    return getHeartbeatService().createServer(address, port, isSSL);
  }

  @Direct
  public boolean isJoinComplete()
  {
    return getHeartbeatService().isJoinComplete();
  }
  
  @Direct
  public ServerBartender getServer(String id)
  {
    return getHeartbeatService().getServer(id);
  }

  public void updateRack(UpdateRackHeartbeat updateRack)
  {
    getHeartbeatService().updateRack(updateRack);
  }

  public void updateHeartbeats()
  {
    getHeartbeatService().updateHeartbeats();
  }
  
  @OnActive
  public void onStart()
  {
    // onHeartbeatStart(_serverSelf);
  }
  
  public void start(Result<Boolean> result)
  {
    getHeartbeatService().start(result);
  }
  
  public void stop()
  {
    getHeartbeatService().stop();
  }

  public void updatePodsFromHeartbeat()
  {
    //_podServiceImpl.updatePodsFromHeartbeat();
  }

  public HeartbeatImpl getHeartbeatService()
  {
    return _heartbeatImpl;
  }
}
