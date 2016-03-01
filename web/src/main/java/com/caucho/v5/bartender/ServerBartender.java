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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import com.caucho.v5.util.L10N;

abstract public class ServerBartender
{
  private static final L10N L = new L10N(ServerBartender.class);
  
  private final String _address;
  private final int _port;
  
  private final String _id;
  
  private final AtomicLong _lastConnectionFailTime = new AtomicLong();
  
  public ServerBartender(String address,
                         int port)
  {
    //Objects.requireNonNull(rack);
    Objects.requireNonNull(address);
    
    if (address.isEmpty()) {
      throw new IllegalArgumentException(L.l("Invalid address port={0}", port));
    }
    
    _address = address;
    _port = port;
    
    _id = getAddress() + ":" + port();
    /*
    if (_id.equals("127.0.0.1:6810")) {
      Thread.dumpStack();
    }
    */
  }
  
  public final String getId()
  {
    return _id;
  }
  
  /**
   * The public TCP/IP address of the server. 
   */
  public final String getAddress()
  {
    if (_address != null && ! "".equals(_address)) {
      return _address;
    }
    else {
      return "127.0.0.1";
    }
  }
  
  public final String getBindAddress()
  {
    return _address;
  }

  /**
   * Public TCP/IP port for the server.
   */
  public final int port()
  {
    return _port;
  }

  abstract public String getDisplayName();
  
  abstract public ClusterBartender getCluster();

  public String getClusterId()
  {
    return getCluster().id();
  }
  
  /**
   * Root of the bartender multi-cluster system.
   */
  public RootBartender getRoot()
  {
    return getCluster().getRoot();
  }

  /**
   * True if the server is up; it has a valid heartbeat.
   */
  public final boolean isUp()
  {
    return getState().isActive();
  }

  /**
   * True if the server is a known server, as opposed to a hollow proxy.
   */
  public final boolean isKnown()
  {
    return getState().isKnown();
  }

  abstract public ServerBartenderState getState();

  /**
   * True for the current server itself.
   */
  public boolean isSelf()
  {
    return false;
  }

  public boolean isHub()
  {
    return true;
  }

  /**
   * 
   */
  public void disable()
  {
    // TODO Auto-generated method stub
    
  }

  /**
   * 
   */
  public void disableSoft()
  {
    // TODO Auto-generated method stub
    
  }

  /**
   * 
   */
  public void enable()
  {
    // TODO Auto-generated method stub
    
  }
  
  //
  // connection status
  //
  
  public void clearConnectionFailTime()
  {
    _lastConnectionFailTime.set(0);
  }
  
  public long getConnectionFailTime()
  {
    return _lastConnectionFailTime.get();
  }
  
  public boolean compareAndSetConnectionFailTime(long lastTime, long time)
  {
    return _lastConnectionFailTime.compareAndSet(lastTime, time);
  }

  public long getLastHeartbeatTime()
  {
    return 0;
  }

  public int getServerIndex()
  {
    return 0;
  }
  
  public boolean isSameServer(ServerBartender server)
  {
    return this == server;
  }
  
  @Override
  public int hashCode()
  {
    return getId().hashCode();
  }
  
  @Override
  public boolean equals(Object o)
  {
    if (! (o instanceof ServerBartender)) {
      return false;
    }
    
    ServerBartender server = (ServerBartender) o;
    
    return getId().equals(server.getId());
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
    
    sb.append(",").append(getState());
    sb.append("]");
    
    return sb.toString();
  }
}
