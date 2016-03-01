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

import java.util.logging.Logger;

import com.caucho.v5.bartender.pod.UpdatePodSystem;
import com.caucho.v5.util.CurrentTime;

/**
 * Outgoing server for heartbeat messages
 */
public class ServerTarget
{
  private static final Logger log = Logger.getLogger(ServerTarget.class.getName());
  
  private final ServerHeartbeat _server;
  private final HeartbeatService _heartbeat;
  
  private long _lastServerTime;
  
  private long _lastHubTime;
  private long _lastRackCrc;
  private long _lastPodCrc;

  ServerTarget(ServerHeartbeat server,
               HeartbeatService heartbeat)
  {
    _server = server;
    _heartbeat = heartbeat;
  }

  ServerHeartbeat getServer()
  {
    return _server;
  }

  private HeartbeatService getHeartbeat()
  {
    return _heartbeat;
  }

  void sendServerHeartbeat(UpdateServerHeartbeat updateServer)
  {
    long now = CurrentTime.getCurrentTime();
    
    if (_lastServerTime + getTimeout() < now) {
      _lastServerTime = now;
      
      getHeartbeat().serverHeartbeat(updateServer);
    }
  }

  /**
   * Sends a cluster heartbeat message if the update has changed, or it's time
   * for a periodic heartbeat.
   * 
   * The cluster heartbeat message include all information for a foreign cluster.
   */
  void clusterHeartbeat(String clusterId,
                        UpdateServerHeartbeat updateSelf,
                        UpdateRackHeartbeat updateRack,
                        UpdatePodSystem updatePod,
                        long sequence)
  {
    if (startUpdate(updateRack, updatePod)) {
      getHeartbeat().clusterHeartbeat(clusterId, updateSelf, updateRack, updatePod, sequence);
    }
  }

  /**
   * Sends a hub heartbeat message if the update has changed, or it's time
   * for a periodic heartbeat.
   * 
   * The hub heartbeat message include all information for the current cluster
   * from a hub server.
   */
  void hubHeartbeat(UpdateServerHeartbeat updateServer,
                    UpdateRackHeartbeat updateRack,
                    UpdatePodSystem updatePod,
                    long sequence)
  {
    if (startUpdate(updateRack, updatePod)) {
      getHeartbeat().hubHeartbeat(updateServer, updateRack, updatePod, sequence);
    }
  }
  
  /**
   * Only send updates on changes or timeouts.
   */
  private boolean startUpdate(UpdateRackHeartbeat updateRack,
                              UpdatePodSystem updatePod)
  {
    long now = CurrentTime.getCurrentTime();
    
    if (_lastHubTime + getTimeout() < now) {
    }
    else if (_lastRackCrc != updateRack.getCrc()) {
    }
    else if (updatePod != null && _lastPodCrc != updatePod.getCrc()) {
    }
    else {
      return false;
    }
    
    _lastHubTime = now;
    _lastRackCrc = updateRack.getCrc();
    
    if (updatePod != null) {
      _lastPodCrc = updatePod.getCrc();
    }
    
    return true;
  }
  
  /**
   * The send timeout avoids duplicate messages.
   */
  private long getTimeout()
  {
    return 15000;
  }
  
  /**
   * Clears timeout to ensure the message will be sent.
   */
  void clear()
  {
    _lastServerTime = 0;
    _lastHubTime = 0;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _server + "]";
  }
}
