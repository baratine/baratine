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

package com.caucho.v5.bartender.pod;

import com.caucho.v5.bartender.ServerBartender;

/**
 * Handle to a server used for a pod's node.
 * 
 * The handle enables dynamic servers, because it can be reassigned when
 * servers are added or removed.
 */
public class ServerPod
{
  private final int _index;
  
  private ServerBartender _server;
  
  public ServerPod(int index)
  {
    _index = index;
  }
  
  public ServerPod(int index, ServerBartender server)
  {
    this(index);
    
    _server = server;
  }

  public int getIndex()
  {
    return _index;
  }
  
  public ServerBartender getServer()
  {
    return _server;
  }
  
  public void setServer(ServerBartender server)
  {
    _server = server;
  }
  
  /**
   * If the server is up, returns true.
   */
  public boolean isUp()
  {
    ServerBartender server = _server;
    
    return server != null && server.isUp();
  }
  
  /**
   * If the server is up, returns true.
   */
  public boolean isSelf()
  {
    ServerBartender server = _server;
    
    return server != null && server.isSelf();
  }
  
  /**
   * Returns the server's id, if the server is up, and the 
   * empty string otherwise.
   */
  public String getServerId()
  {
    ServerBartender server = _server;
    
    return server != null ? server.getId() : "";
  }

  public boolean isValid()
  {
    return _server != null;
  }

  /**
   * Returns true if a server has been assigned to this node.
   * 
   * This can be false for a triad with only 2 servers.
   */
  public boolean isAssigned()
  {
    // return _server != null || ! _hint.isNull();
    return _server != null;
  }
  
  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    
    sb.append(getClass().getSimpleName());
    sb.append("[");
    
    sb.append(_index);
    
    if (_server != null) {
      sb.append(",").append(_server.getId());
    }
    
    sb.append("]");
    
    return sb.toString();
  }
}
