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

public class PodBartenderProxy implements PodBartender
{
  private final String _id;
  private final String _name;
  private final String _clusterId;
  
  private PodBartender _delegate;
  
  PodBartenderProxy(String name)
  {
    Objects.requireNonNull(name);
    
    int p = name.indexOf('.');
    
    if (p <= 0) {
      throw new IllegalArgumentException(name);
    }
    
    if (name.indexOf('/') >= 0) {
      throw new IllegalArgumentException(name);
    }
    
    _name = name.substring(0, p);
    _clusterId = name.substring(p + 1);
    _id = _name + '.' + _clusterId;
  }
  
  @Override
  public boolean isValid()
  {
    return getDelegate() != null;
  }
  
  void setDelegate(PodBartender delegate)
  {
    Objects.requireNonNull(delegate);

    _delegate = delegate;
  }
  
  PodBartender getDelegate()
  {
    PodBartender delegate = _delegate;
    
    if (delegate != null && delegate.isValid()) {
      return delegate;
    }
    else {
      return null;
    }
  }

  NodePodAmp getNodeDelegate(int index)
  {
    PodBartender delegate = getDelegate();
    
    if (delegate != null) {
      return delegate.getNode(index);
    }
    else {
      return null;
    }
  }
  
  @Override
  public String getId()
  {
    return _id;
  }
  
  @Override
  public String name()
  {
    return _name;
  }
  
  @Override
  public String getClusterId()
  {
    return _clusterId;
  }
  
  @Override
  public long getSequence()
  {
    PodBartender delegate = getDelegate();
    
    if (delegate != null) {
      return delegate.getSequence();
    }
    else {
      return -1;
    }
  }
  
  @Override
  public String getTag()
  {
    PodBartender delegate = getDelegate();
    
    if (delegate != null) {
      return delegate.getTag();
    }
    else {
      return null;
    }
  }
  
  @Override
  public long getCrc()
  {
    PodBartender delegate = getDelegate();
    
    if (delegate != null) {
      return delegate.getCrc();
    }
    else {
      return 0;
    }
  }
  
  @Override
  public PodBartender.PodType getType()
  {
    PodBartender delegate = getDelegate();

    if (delegate != null) {
      return delegate.getType();
    }
    else {
      return PodBartender.PodType.off;
    }
  }

  @Override
  public ServerBartender[] getServers()
  {
    PodBartender delegate = getDelegate();
    
    if (delegate != null) {
      return delegate.getServers();
    }
    else {
      return new ServerBartender[] {};
    }
  }

  @Override
  public ServerBartender server(int i)
  {
    PodBartender delegate = getDelegate();
    
    if (delegate != null) {
      return delegate.server(i);
    }
    else {
      return null;
    }
  }
  
  @Override
  public int serverCount()
  {
    PodBartender delegate = getDelegate();
    
    if (delegate != null) {
      return delegate.serverCount();
    }
    else {
      return 1;
    }
  }

  @Override
  public int findServerIndex(ServerBartender server)
  {
    PodBartender delegate = getDelegate();
    
    if (delegate != null) {
      return delegate.findServerIndex(server);
    }
    else {
      return -1;
    }
  }

  @Override
  public int findServerIndex(String serverId)
  {
    PodBartender delegate = getDelegate();
    
    if (delegate != null) {
      return delegate.findServerIndex(serverId);
    }
    else {
      return -1;
    }
  }
  
  @Override
  public int nodeCount()
  {
    PodBartender delegate = getDelegate();
    
    if (delegate != null) {
      return delegate.nodeCount();
    }
    else {
      return 1;
    }
  }

  @Override
  public NodePodAmp getNode(int hash)
  {
    return new NodePodProxy(this, hash);
  }
  
  @Override
  public int getVnodeCount()
  {
    PodBartender delegate = getDelegate();
    
    if (delegate != null) {
      return delegate.getVnodeCount();
    }
    else {
      return 1;
    }
  }

  @Override
  public int getDepth()
  {
    PodBartender delegate = getDelegate();
    
    if (delegate != null) {
      return delegate.getDepth();
    }
    else {
      return 1;
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + name() + '.' + getClusterId() + "]";
  }
}
