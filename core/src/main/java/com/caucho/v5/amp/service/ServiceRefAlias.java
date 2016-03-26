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

package com.caucho.v5.amp.service;

import io.baratine.service.ServiceRef;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.stub.StubAmp;

/**
 * Service ref with an alias.
 */
public class ServiceRefAlias extends ServiceRefWrapper
{
  private final String _address;
  private final ServiceRefAmp _serviceRef;
  
  public ServiceRefAlias(String address, ServiceRefAmp actorRef)
  {
    _address = address;
    _serviceRef = actorRef;
  }
  
  public ServiceRefAmp delegate()
  {
    return _serviceRef;
  }

  @Override
  public String address()
  {
    return _address;
  }
  
  @Override
  public InboxAmp inbox()
  {
    return _serviceRef.inbox();
  }
  
  @Override
  public StubAmp stub()
  {
    return _serviceRef.stub();
  }
  
  @Override
  public boolean isUp()
  {
    return _serviceRef.isUp();
  }
  
  @Override
  public ServiceRefAmp onLookup(String path)
  {
    ServiceRefAmp delegate = _serviceRef.onLookup(path);
    
    if (delegate != null) {
      return new ServiceRefAlias(_address + path, delegate); 
    }
    else {
      return null;
    }
  }
  
  @Override
  public ServiceRefAmp lookup(String path)
  {
    ServiceRefAmp delegate = (ServiceRefAmp) _serviceRef.lookup(path);
    
    if (delegate != null) {
      return new ServiceRefAlias(_address + path, delegate); 
    }
    else {
      return null;
    }
  }

  @Override
  public MethodRefAmp getMethod(String methodName)
  {
    return _serviceRef.getMethod(methodName);
  }

  @Override
  public void offer(MessageAmp message)
  {
    _serviceRef.offer(message);
  }
  
  @Override
  public ServiceRefAmp start()
  {
    return this;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _address + "," + _serviceRef + "]";
  }
}
