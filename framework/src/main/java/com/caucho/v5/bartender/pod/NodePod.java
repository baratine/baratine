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

import java.io.Serializable;
import java.util.Objects;

import com.caucho.v5.bartender.ServerBartender;


@SuppressWarnings("serial")
public class NodePod implements Serializable, NodePodAmp
{
  private final PodBartender _pod;
  private final int []_owners;
  private final int _index;
  
  @SuppressWarnings("unused")
  private NodePod()
  {
    _pod = null;
    _owners = new int[0];
    _index = 0;
  }
  
  NodePod(PodBartender pod,
          int index,
          int []owners)
  {
    Objects.requireNonNull(pod);
    Objects.requireNonNull(owners);

    _pod = pod;
    _index = index;
    _owners = owners;
    
    for (int i = 0; i < owners.length; i++) {
      if (pod.serverCount() <= owners[i]) {
        Thread.dumpStack();
      }
    }
  }
  
  @Override
  public PodBartender pod()
  {
    return _pod;
  }

  @Override
  public int index()
  {
    return _index;
  }

  /**
   * The number of servers for the node.
   */
  @Override
  public int serverCount()
  {
    return _owners.length;
  }

  @Override
  public ServerBartender server(int i)
  {
    return pod().server(owner(i));
  }
  
  @Override
  public int owner(int index)
  {
    int []owners = _owners;
    
    if (index < owners.length) {
      return owners[index];
    }
    else {
      return -1;
    }
  }
  
  /**
   * Test if the server is the primary for the node.
   */
  @Override
  public boolean isServerPrimary(ServerBartender server)
  {
    for (int i = 0; i < Math.min(1, _owners.length); i++) {
      ServerBartender serverBar = server(i);
      
      if (serverBar == null) {
        continue;
      }
      else if (serverBar.isSameServer(server)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Test if the server is the current owner of the node. The owner is the
   * first live server in the backup list.
   * 
   * @param server the server to test
   * 
   * @return true if the server is a node copy and also the first active server.
   */
  @Override
  public boolean isServerOwner(ServerBartender server)
  {
    for (int i = 0; i < _owners.length; i++) {
      ServerBartender serverBar = server(i);

      if (serverBar == null) {
        continue;
      }
      else if (serverBar.isSameServer(server)) {
        return server.isUp();
      }
      else if (serverBar.isUp()) {
        return false;
      }
    }
    
    return false;
  }

  /**
   * Test if the server is the one of the node copies.
   * 
   * @param server the server to test
   * 
   * @return true if the server is a node copy
   */
  @Override
  public boolean isServerCopy(ServerBartender server)
  {
    for (int i = 0; i < _owners.length; i++) {
      ServerBartender serverBar = server(i);

      if (serverBar != null && serverBar.isSameServer(server)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Return the current server owner.
   */
  public ServerBartender owner()
  {
    for (int i = 0; i < _owners.length; i++) {
      ServerBartender serverBar = server(i);
      
      if (serverBar != null && serverBar.isUp()) {
        return serverBar;
      }
    }

    return null;
  }

  /*
  @Override
  public int hash(String path)
  {
    return _manager.hash(path);
  }
  */

  @Override
  public String podName()
  {
    return _pod.name();
  }

  @Override
  public int nodeCount()
  {
    return _pod.nodeCount();
  }

  @Override
  public int nodeIndex()
  {
    return _index;
  }
  
  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    
    sb.append(getClass().getSimpleName());
    sb.append("[");
    
    sb.append(_index);
    
    if (_pod != null) {
      sb.append(",").append(_pod.name());
    }
    
    sb.append(";");
    
    for (int i = 0; i < _owners.length; i++) {
      if (i != 0) {
        sb.append(",");
      }
      
      sb.append(_owners[i]);
    }
    
    sb.append("]");
    
    return sb.toString();
  }
}
