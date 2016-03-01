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

import java.util.Objects;
import java.util.Set;

import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.ServerBartenderState;

/**
 * Server for the heartbeat system.
 * 
 * ServerHeart contains the current known state of the corresponding
 * Baratine server.
 */
public class ServerHeartbeat extends ServerBartender
{
  private final ClusterHeartbeat _cluster;
  
  private final boolean _isDynamic;
  private final boolean _isSSL;
  
  private final ServerHeartbeatData _dataSelf;
  
  private ServerHeartbeatData _data;
  
  ServerHeartbeat(String address,
                  int port,
                  boolean isSSL,
                  int portBartender,
                  ClusterHeartbeat cluster,
                  String displayName,
                  ServerHeartbeatBuilder builder)
  {
    super(address, port);
    
    Objects.requireNonNull(builder);
    
    _isSSL = isSSL;
    
    //Objects.requireNonNull(rack);
    Objects.requireNonNull(cluster);
    
    _cluster = cluster;
    
    _dataSelf = new ServerHeartbeatData(this);
    _data = _dataSelf;
    
    _dataSelf.setPortBartenderConfig(portBartender);
    
    if (displayName == null) {
      displayName = getId();
    }
    
    getDataSelf().setDisplayName(displayName);
    getDataSelf().setPodSet(builder.getPodSet());
    getDataSelf().setPodAny(builder.isPodAny());
    
    _isDynamic = builder.isDynamic();
    
    update();
  }

  /**
   * The user-readable display name is either a user-defined identifier or
   * the TCP address. 
   */
  @Override
  public String getDisplayName()
  {
    return getDataSelf().getDisplayName();
  }

  void setDisplayName(String displayName)
  {
    getDataSelf().setDisplayName(displayName);
  }
  
  /**
   * The owning cluster of the server.
   */
  @Override
  public ClusterHeartbeat getCluster()
  {
    return _cluster;
  }

  @Override
  public ServerBartenderState getState()
  {
    return getData().getState();
  }

  public boolean isDynamic()
  {
    return _isDynamic;
  }

  public boolean isSSL()
  {
    return _isSSL;
  }
  
  /**
   * The internal TCP port for Bartender sockets. 
   */
  public int getPortBartender()
  {
    return getData().getPortBartender();
  }

  public void setPortBartender(int portBartender)
  {
    getDataSelf().setPortBartender(portBartender);
  }
  
  //
  // rack information
  //

  /**
   * Returns the owning rack, if the server represents an active server.
   */
  public RackHeartbeat getRack()
  {
    return getData().getRack();
  }
  
  /**
   * The server index within the current rack.
   */
  @Override
  public int getServerIndex()
  {
    return getData().getServerIndex();
  }
  
  /**
   * Sets the rack and server index within the rack.
   * 
   * The index can change dynamically as servers are added to the rack.
   */
  void setRack(RackHeartbeat rack, int index)
  {
    getDataSelf().setRack(rack, index);
  }

  public long getSequence()
  {
    return getData().getSequence();
  }

  public long getStateSequence()
  {
    return getData().getStateSequence();
  }

  /**
   * Dynamic data for the server.
   */
  ServerHeartbeatData getData()
  {
    return _data;
  }

  /**
   * Statically configured data for the server.
   */
  ServerHeartbeatData getDataSelf()
  {
    return _dataSelf;
  }
  
  public ServerHeartbeat getDelegate()
  {
    return getDataSelf().getDelegate();
  }

  public void setDelegate(ServerHeartbeat delegate)
  {
    getDataSelf().setDelegate(delegate);
    
    if (delegate != null) {
      _data = delegate.getData();
    }
    else {
      _data = getDataSelf();
    }
  }
  
  @Override
  public boolean isSelf()
  {
    ServerHeartbeat delegate = getDelegate();
    
    if (delegate != null) {
      return delegate.isSelf();
    }
    else {
      return super.isSelf();
    }
  }
  
  @Override
  public boolean isSameServer(ServerBartender server)
  {
    if (this == server) {
      return true;
    }
    else if (getDelegate() == server) {
      return true;
    }
    else if (((ServerHeartbeat) server).getDelegate() == this) {
      return true;
    }
    else {
      return false;
    }
  }

  /*
  public void setExternalId(String extId)
  {
    _externalId = extId;
    
    update();
  }
  */
  
  public void setExternalServer(ServerHeartbeat serverExt)
  {
    getData().setExternalServer(serverExt);
    
    // update();
  }
  
  public String getExternalId()
  {
    return getData().getExternalId();
  }
  
  //
  // heartbeats
  //
  
  @Override
  public long getLastHeartbeatTime()
  {
    return getData().getLastHeartbeatTime();
  }
  
  public String getMachineHash()
  {
    return getData().getMachineHash();
  }
  
  public void setMachineHash(String machineHash)
  {
    getData().setMachineHash(machineHash);
  }

  public void setSeedIndex(int index)
  {
    getData().setSeedIndex(index);
  }

  public int getSeedIndex()
  {
    return getData().getSeedIndex();
  }
  
  public boolean isPodAny()
  {
    return getData().isPodAny();
  }
  
  public Set<String> getPodSet()
  {
    return getData().getPodSet();
  }

  public void setLastSeedTime(long now)
  {
    getData().setLastSeedTime(now);
  }
  
  void toKnown()
  {
    getData().toKnown();
  }
  
  boolean onHeartbeatStart()
  {
    clearConnectionFailTime();
    
    return getData().onHeartbeatStart();
  }
  
  boolean onHeartbeatStop()
  {
    return getData().onHeartbeatStop();
  }
  
  boolean onHeartbeatUpdate(UpdateServerHeartbeat update)
  {
    clearConnectionFailTime();
    
    return getData().onHeartbeatUpdate(update);
  }
  
  protected void updateSequence()
  {
    getData().updateSequence();
  }
  
  UpdateServerHeartbeat getUpdate()
  {
    return getData().getUpdate();
    
    // return _update;
  }
  
  void update()
  {
    getData().update();
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
    /*
    sb.append(",").append(_clusterId)
                  .append(".").append(_regionIndex)
                  .append(".").append(_rackIndex)
                  .append(".").append(_index);
  */
    
    int seedIndex = getSeedIndex();
    if (seedIndex > 0) {
      sb.append(",seed=" + seedIndex);
    }
    
    if (getExternalId() != null) {
      sb.append(",ext=").append(getExternalId());
    }
    
    for (String pod : getPodSet()) {
      sb.append("," + pod);
    }
    
    sb.append(",").append(getState());
    sb.append("]");
    
    return sb.toString();
  }
}
