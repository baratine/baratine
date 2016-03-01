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

import java.util.Objects;

import com.caucho.v5.bartender.ServerBartender;

public class NodePodProxy implements NodePodAmp
{
  private final PodBartenderProxy _pod;
  private final int _index;
  
  @SuppressWarnings("unused")
  private NodePodProxy()
  {
    _pod = null;
    _index = 0;
  }
  
  NodePodProxy(PodBartenderProxy pod,
               int index)
  {
    Objects.requireNonNull(pod);
    
    _pod = pod;
    _index = index;
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

  @Override
  public String podName()
  {
    return pod().name();
  }

  @Override
  public int nodeCount()
  {
    return pod().nodeCount();
  }

  @Override
  public int nodeIndex()
  {
    NodePodAmp delegate = delegate();
    
    if (delegate != null) {
      return delegate.nodeIndex();
    }
    else {
      return 0;
    }
  }

  /*
  @Override
  public int hash(String path)
  {
    return getDelegate().hash(path);
  }
  */

  /**
   * The number of servers for the node.
   */
  @Override
  public int serverCount()
  {
    NodePodAmp delegate = delegate();
    
    if (delegate != null) {
      return delegate.serverCount();
    }
    else {
      return 0;
    }
  }

  /**
   * The owner index for the node.
   */
  @Override
  public int owner(int hash)
  {
    NodePodAmp delegate = delegate();
    
    if (delegate != null) {
      return delegate().owner(hash);
    }
    else {
      return 0;
    }
  }

  @Override
  public ServerBartender server(int i)
  {
    NodePodAmp delegate = delegate();
    
    if (delegate != null) {
      return delegate.server(i);
    }
    else {
      return null;
    }
  }

  @Override
  public boolean isServerPrimary(ServerBartender server)
  {
    NodePodAmp delegate = delegate();
    
    if (delegate != null) {
      return delegate.isServerPrimary(server);
    }
    else {
      return false;
    }
  }

  @Override
  public boolean isServerOwner(ServerBartender server)
  {
    NodePodAmp delegate = delegate();

    if (delegate != null) {
      return delegate.isServerOwner(server);
    }
    else {
      return false;
    }
  }

  @Override
  public boolean isServerCopy(ServerBartender server)
  {
    NodePodAmp delegate = delegate();
    
    if (delegate != null) {
      return delegate.isServerCopy(server);
    }
    else {
      return false;
    }
  }

  @Override
  public ServerBartender owner()
  {
    return delegate().owner();
  }
  
  private NodePodAmp delegate()
  {
    return _pod.getNodeDelegate(_index);
  }
  
  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    
    sb.append(getClass().getSimpleName());
    sb.append("[");

    NodePodAmp delegate = delegate();
    
    if (delegate != null) {
      sb.append(delegate.nodeIndex());
    }
    else {
      sb.append("h=" + _index);
    }
    
    if (_pod != null) {
      sb.append(",").append(_pod.name());
    }
    
    sb.append(";");
    
    for (int i = 0; i < serverCount(); i++) {
      if (i != 0) {
        sb.append(",");
      }
      
      sb.append(owner(i));
    }
    
    sb.append("]");
    
    return sb.toString();
  }
}
