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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.caucho.v5.bartender.ServerBartenderState;
import com.caucho.v5.util.CurrentTime;

class ServerHeartbeatData
{
  private final ServerHeartbeat _server;
  
  private ServerBartenderState _state = ServerBartenderState.unknown;
  
  private UpdateServerHeartbeat _update;
  
  private RackHeartbeat _rack;
  // sequence of the server within the rack
  private int _index;
  
  private long _lastHeartbeatTime;
  private long _lastSequence;
  private long _lastStateChange;
  
  // private String _externalId;
  private int _seedIndex;
  private long _lastJoinSeedTime;
  
  private String _machineHash = "";
  private long _lastSeedTime;
  private int _portBartender;

  private String _displayName;

  private ServerHeartbeat _serverExternal;
  private ServerHeartbeat _serverDelegate;

  private int _portBartenderConfig;

  private boolean _isPodAny;
  private Set<String> _podSet = new HashSet<>();
  
  //private AtomicLong _lastConnectionFailTime = new AtomicLong();
  
  ServerHeartbeatData(ServerHeartbeat server)
  {
    Objects.requireNonNull(server);
    
    _server = server;
    
    update();
  }

  public ServerHeartbeat getServer()
  {
    return _server;
  }

  public String getDisplayName()
  {
    return _displayName;
  }

  void setDisplayName(String displayName)
  {
    if (displayName != null) {
      _displayName = displayName;
    }
  }

  public ServerBartenderState getState()
  {
    return _state;
  }

  public void setPortBartender(int portBartender)
  {
    _portBartender = portBartender;
  }

  public void setPortBartenderConfig(int portBartender)
  {
    _portBartenderConfig = portBartender;
  }
  
  public int getPortBartender()
  {
    if (_portBartenderConfig > 0) {
      return _portBartenderConfig;
    }
    else {
      return _portBartender;
    }
  }
  
  //
  // rack information
  //

  /**
   * Returns the owning rack, if the server represents an active server.
   */
  public RackHeartbeat getRack()
  {
    return _rack;
  }
  
  public int getServerIndex()
  {
    return _index;
  }
  
  /**
   * Sets the rack and server index within the rack.
   * 
   * The index can change dynamically as servers are added to the rack.
   */
  void setRack(RackHeartbeat rack, int index)
  {
    _rack = rack;
    _index = index;
  }

  public long getSequence()
  {
    return _lastSequence;
  }

  public long getStateSequence()
  {
    return _lastStateChange;
  }

  public void setExternalServer(ServerHeartbeat server)
  {
    ServerHeartbeat oldServer = _serverExternal;
    
    if (oldServer != server) {
      if (server != getServer()) {
        _serverExternal = server;
      }
      
      if (oldServer != null) {
        oldServer.setDelegate(null);
      }
    }
  }
  
  public void setDelegate(ServerHeartbeat server)
  {
    _serverDelegate = server;
  }

  public ServerHeartbeat getDelegate()
  {
    return _serverDelegate;
  }

  /*
  public void setExternalId(String extId)
  {
    _externalId = extId;
    
    update();
  }
  */
  
  public String getExternalId()
  {
    if (_serverExternal != null) {
      return _serverExternal.getId();
    }
    else {
      return null;
    }
  }
  
  //
  // heartbeats
  //
  
  public long getLastHeartbeatTime()
  {
    return _lastHeartbeatTime;
  }
  
  public String getMachineHash()
  {
    return _machineHash;
  }
  
  public void setMachineHash(String machineHash)
  {
    Objects.requireNonNull(machineHash);
    
    _machineHash = machineHash;
  }

  public void setSeedIndex(int index)
  {
    _seedIndex = index;
  }

  public int getSeedIndex()
  {
    return _seedIndex;
  }

  public void setLastSeedTime(long now)
  {
    _lastSeedTime = now;
  }

  public boolean isPodAny()
  {
    return _isPodAny;
  }
  
  public void setPodAny(boolean isPodAny)
  {
    _isPodAny = isPodAny;
  }
  
  public Set<String> getPodSet()
  {
    return new HashSet<>(_podSet);
  }
  
  public void setPodSet(Set<String> podSet)
  {
    Objects.requireNonNull(podSet);
    
    _podSet = new HashSet<>(podSet);
  }
  
  void toKnown()
  {
    ServerBartenderState oldState = _state;
    _state = oldState.toKnown();
    
    update();
    updateStateChange(oldState);
  }
  
  boolean onHeartbeatStart()
  {
    ServerBartenderState oldState = _state;
    
    _state = oldState.onHeartbeatStart();
    
    long now = CurrentTime.currentTime();
    
    _lastHeartbeatTime = now;
    
    _server.clearConnectionFailTime();
    //_lastConnectionFailTime.set(0);
    
    if (oldState != _state) {
      updateStateChange(oldState);
      updateSequence();
            
      return true;
    }
    else {
      return false;
    }
  }
  
  boolean onHeartbeatStop()
  {
    ServerBartenderState oldState = _state;
    
    _state = oldState.onHeartbeatStop();
    
    if (oldState != _state) {
      updateStateChange(oldState);
      updateSequence();
      
      return true;
    }
    else {
      return false;
    }
  }
  
  boolean onHeartbeatUpdate(UpdateServerHeartbeat update)
  {
    Objects.requireNonNull(update);
    
    if (update.isUp()) {
      _lastHeartbeatTime = CurrentTime.currentTime();
    }
    
    if (update.getSequence() <= _lastSequence) {
      return false;
    }
    
    if (_server.getId().equals(getDisplayName())) {
      setDisplayName(update.getDisplayName());
    }
    
    setPortBartender(update.getPortBartender());
    
    setMachineHash(update.getMachineHash());
    
    setPodAny(update.isPodAny());
    setPodSet(update.getPodSet());
    
    _lastSequence = update.getSequence();
    
    ServerBartenderState oldState = _state;
    
    _state = update.getState();
    
    update();
    
    return updateStateChange(oldState);
  }
  
  protected void updateSequence()
  {
    long now = CurrentTime.currentTime();
    
    _lastSequence = Math.max(_lastSequence + 1, now);
    update();
  }
  
  protected boolean updateStateChange(ServerBartenderState oldState)
  {
    if (_state != oldState) {
      long now = CurrentTime.currentTime();
    
      _lastStateChange = Math.max(_lastStateChange + 1, now);
      
      return true;
    }
    else {
      return false;
    }
  }
  
  UpdateServerHeartbeat getUpdate()
  {
    return _update;
  }
  
  void update()
  {
    UpdateServerHeartbeat oldUpdate = _update;
    
    _update = new UpdateServerHeartbeat(this);
    
    RackHeartbeat rack = getRack();
    
    if (rack != null
        && (oldUpdate == null || oldUpdate.getCrc() != _update.getCrc())) {
      rack.update();
    }
  }
  
  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    
    sb.append(getClass().getSimpleName()); 
    sb.append("[");

    sb.append(_server.getId());
    
    sb.append(",").append(getState());
    sb.append("]");
    
    return sb.toString();
  }
}
