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

import com.caucho.v5.util.L10N;

class ServerSelf extends ServerHeartbeat
{
  private static final L10N L = new L10N(ServerSelf.class);
  
  ServerSelf(String address,
             int port,
             boolean isSSL,
             int portBartender,
             ClusterHeartbeat cluster,
             String displayName,
             String serverHash,
             ServerHeartbeatBuilder builder)
  {
    super(address, port, isSSL, portBartender, cluster, displayName, 
          builder);
    
    // Objects.requireNonNull(serverBartender);
    Objects.requireNonNull(address);
    
    if (address.equals("") || address.equals("*")) {
      throw new IllegalArgumentException();
    }

    if (port == 0) {
      throw new IllegalArgumentException(L.l("Port may not be zero for server self"));
    }
    
    if (serverHash == null) {
      serverHash = "";
    }
    
    getDataSelf().setMachineHash(serverHash);
    getDataSelf().setPortBartender(portBartender);
    
    onHeartbeatStart();
  }
  
  @Override
  public final boolean isSelf()
  {
    return true;
  }
  
  @Override
  public void setDelegate(ServerHeartbeat server)
  {
  }
  
  @Override
  public final void setMachineHash(String value)
  {
  }

  @Override
  void setDisplayName(String displayName)
  {
  }
  
  @Override
  boolean onHeartbeatUpdate(UpdateServerHeartbeat update)
  {
    return false;
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    sb.append(getClass().getSimpleName()).append("[");
    
    if (! getDisplayName().equals(getId())) {
      sb.append(getDisplayName()).append(",");
    }
    
    sb.append(getAddress()).append(':').append(port());
    
    sb.append(",").append(getMachineHash());
    
    if (getExternalId() != null) {
      sb.append(",ext=").append(getExternalId());
    }
    sb.append("]");
    
    return sb.toString();
  }
}
