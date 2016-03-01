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

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Server for the heartbeat system.
 * 
 * ServerHeart contains the current known state of the corresponding
 * Baratine server.
 */
public class ServerHeartbeatBuilder
{
  private ClusterHeartbeat _cluster;
  
  private boolean _isSSL;
  private boolean _isDynamic;
  
  private Boolean _isPodAny;
  private HashSet<String> _podSet = new HashSet<>();
  
  public ClusterHeartbeat getCluster()
  {
    return _cluster;
  }
  
  public ServerHeartbeatBuilder cluster(ClusterHeartbeat cluster)
  {
    Objects.requireNonNull(cluster);
    
    _cluster = cluster;
    
    return this;
  }
  
  public boolean isSSL()
  {
    return _isSSL;
  }
  
  public ServerHeartbeatBuilder ssl(boolean isSSL)
  {
    _isSSL = isSSL;
    
    return this;
  }
  
  public boolean isDynamic()
  {
    return _isDynamic;
  }
  
  public ServerHeartbeatBuilder dynamic(boolean isDynamic)
  {
    _isDynamic = isDynamic;
    
    return this;
  }
  
  public boolean isPodAny()
  {
    if (_isPodAny != null) {
      return _isPodAny;
    }
    else {
      return _podSet.size() == 0;
    }
  }
  
  public void podAny(boolean isPodAny)
  {
    _isPodAny = isPodAny;
  }
  
  public Set<String> getPodSet()
  {
    return Collections.unmodifiableSet(_podSet);
  }
  
  public ServerHeartbeatBuilder pod(String pod)
  {
    _podSet.add(pod);
    
    return this;
  }
}
