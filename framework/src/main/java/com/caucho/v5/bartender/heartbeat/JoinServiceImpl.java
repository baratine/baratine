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

import com.caucho.v5.baratine.InService;
import com.caucho.v5.baratine.Remote;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.ServerBartenderState;
import com.caucho.v5.util.L10N;

import io.baratine.service.Result;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Service for handling the bartender heartbeat messages
 */
@InService(HeartbeatLocalImpl.class)
@Remote
public class JoinServiceImpl
{
  private static final L10N L = new L10N(JoinServiceImpl.class);
  private static final Logger log
    = Logger.getLogger(JoinServiceImpl.class.getName());
  
  private ServerBartender _serverSelf;
  private HeartbeatLocalImpl _bartender;
  private HeartbeatImpl _heartbeatImpl;

  public JoinServiceImpl(ServerBartender serverSelf,
                         HeartbeatLocalImpl bartender,
                         HeartbeatImpl heartbeatImpl)
  {
    _serverSelf = serverSelf;
    _bartender = bartender;
    _heartbeatImpl = heartbeatImpl;
  }
  
  public RackHeartbeat status()
  {
    //return _serverSelf.getServerBartender().getRack();
    return null;
  }
  
  public JoinResult join(String extAddress,
                         int extPort,
                         String clusterId,
                         String address,
                         int port,
                         String displayName,
                         long serverHash)
  {
    /*
    Objects.requireNonNull(extAddress);
    Objects.requireNonNull(address);
    
    UpdateServerHeartbeat server
      = _heartbeatImpl.joinServer(extAddress, extPort,
                                  clusterId,
                                  address, port, 
                                  displayName, serverHash);

    
    // XXX: change to service? to protect against refactoring.
    //_heartbeatImpl.sendHeartbeats();
    
    RackHeartbeat rack = null; // server.getRack();
    JoinResult result = new JoinResult(server);
    
    return result;
    */
    
    return null;
  }
  
  /*
  public void waitForHubHeartbeat(int count, Result<Boolean> result)
  {
    _heartbeatImpl.waitForHubHeartbeat(count, result);
  }
  */

  public ServerBartenderState disableServer(String serverId)
  {
    return ServerBartenderState.unknown;
  }

  public ServerBartenderState disableSoftServer(String serverId)
  {
    return ServerBartenderState.unknown;
  }
  
  public ServerBartenderState enableServer(String serverId)
  {
    return ServerBartenderState.unknown;
  }

  public void updatePod()
  {
    
  }
}
