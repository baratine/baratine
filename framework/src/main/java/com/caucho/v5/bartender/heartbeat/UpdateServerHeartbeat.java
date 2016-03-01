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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import com.caucho.v5.bartender.ServerBartenderState;
import com.caucho.v5.util.Crc64;

public class UpdateServerHeartbeat implements Serializable
{
  private final String _address;
  private final int _port;
  
  private final String _displayName;
  
  private final String _clusterId;
  
  private final String _rackId;
  
  private final String _externalId;
  private final int _seedIndex;
  private final String _machineHash;
  
  private final ServerBartenderState _state;
  
  private final long _crc;
  
  private final long _sequence;
  private int _portBartender;
  
  private boolean _isPodAny;
  private List<String> _podSet;
  
  @SuppressWarnings("unused")
  UpdateServerHeartbeat()
  {
    _address = null;
    _port = 0;
    _displayName = null;
    _clusterId = null;
    _rackId = null;
    _state = null;
    _externalId = null;
    _seedIndex = 0;
    _machineHash = "";
    _sequence = 0;
    _crc = 0;
  }
  
  public UpdateServerHeartbeat(ServerHeartbeatData data)
  {
    _address = data.getServer().getAddress();
    _port = data.getServer().port();
    
    _portBartender = data.getPortBartender();
    
    _displayName = data.getDisplayName();
    
    _clusterId = data.getServer().getCluster().id();

    if (data.getRack() != null) {
      _rackId = data.getRack().getId();
    }
    else {
      _rackId = null;
    }
    
    _externalId = data.getExternalId();
    _seedIndex = data.getSeedIndex();
    _machineHash = data.getMachineHash();
    
    _isPodAny = data.isPodAny();
    
    _podSet = new ArrayList<>(data.getPodSet());
    Collections.sort(_podSet);

    _state = data.getState();
    _sequence = data.getSequence();

    long crc = 0;
    crc = Crc64.generate(crc, _address);
    crc = Crc64.generate(crc, _port);
    crc = Crc64.generate(crc, _portBartender);
    crc = Crc64.generate(crc, _displayName);
    crc = Crc64.generate(crc, _clusterId);
    crc = Crc64.generate(crc, _rackId);
    crc = Crc64.generate(crc, _externalId);
    crc = Crc64.generate(crc, _seedIndex);
    crc = Crc64.generate(crc, _machineHash);
    crc = Crc64.generate(crc, _isPodAny ? 1 : 0);
    
    for (String pod : _podSet) {
      crc = Crc64.generate(crc, pod);
    }
    
    crc = Crc64.generate(crc, _state);
    
    _crc = crc;
  }
  
  public String getAddress()
  {
    return _address;
  }
  
  public int getPort()
  {
    return _port;
  }
  
  public int getPortBartender()
  {
    return _portBartender;
  }
  
  public String getDisplayName()
  {
    return _displayName;
  }
  
  public String getRackId()
  {
    return _rackId;
  }

  public long getSequence()
  {
    return _sequence;
  }

  public ServerBartenderState getState()
  {
    return _state;
  }
  
  public String getExternalId()
  {
    return _externalId;
  }

  public int getSeedIndex()
  {
    return _seedIndex;
  }

  public String getMachineHash()
  {
    return _machineHash;
  }
  
  public boolean isPodAny()
  {
    return _isPodAny;
  }
  
  public HashSet<String> getPodSet()
  {
    return new HashSet<>(_podSet);
  }

  public boolean isUp()
  {
    return _state.isActive();
  }

  public long getCrc()
  {
    return _crc;
  }
  
  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    
    sb.append(getClass().getSimpleName()); 
    sb.append("[");

    sb.append(getAddress()).append(':').append(getPort());
    
    sb.append(",").append(_state);
    sb.append("]");
    
    return sb.toString();
  }
}
