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

import java.lang.reflect.Type;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.message.CloseMessageCallback;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.StubAmp;

import io.baratine.service.Cancel;
import io.baratine.service.ServiceExceptionClosed;

/**
 * Service ref for an object pinned to a parent inbox.
 */
public class ServiceRefPin extends ServiceRefStubBase
{
  private static final Logger log = Logger.getLogger(ServiceRefPin.class.getName());
  
  private String _bindAddress;
  
  public ServiceRefPin(StubAmp actor,
                            InboxAmp inbox)
  {
    super(actor, inbox);
  }
  
  protected ServiceRefPin(String path,
                               StubAmp actor,
                               InboxAmp inbox)
  {
    super(actor, inbox);
    
    _bindAddress = path;
  }

  @Override
  public String address()
  {
    if (_bindAddress != null) {
      return _bindAddress;
    }
    else {
      return "callback:" + stub().name() + "@" + inbox().getAddress();
    }
  }
  
  @Override
  public MethodRefAmp getMethod(String methodName)
  {
    MethodAmp methodBean = stub().getMethod(methodName);
    
    //MethodAmp methodCallback = new MethodAmpChild(methodBean, getActor());
    //return new MethodRefImpl(this, methodCallback, getInbox());
    
    //return new MethodRefImpl(this, methodBean, getInbox());
    return new MethodRefImpl(this, methodBean);
  }
  
  @Override
  public MethodRefAmp getMethod(String methodName, Type type)
  {
    MethodAmp methodBean = stub().getMethod(methodName);
    //MethodAmp methodCallback = new MethodAmpChild(methodBean, getActor());

    //return new MethodRefImpl(this, methodCallback, getInbox());
    
    //return new MethodRefImpl(this, methodBean, getInbox());
    return new MethodRefImpl(this, methodBean);
  }
  
  @Override
  public Object onLookupImpl(String path)
  {
    return stub().onLookup(path, this);
  }

  @Override
  public ServiceRefAmp bind(String address)
  {
    if (_bindAddress == null) {
      _bindAddress = address;
    }

    manager().bind(this, address);
      
    return this;
  }

  @Override
  public <T> T as(Class<T> api, Class<?>... apis)
  {
    //return getManager().createPinProxy(this, api, apis);
    return manager().newProxy(this, api, apis);
  }
  
  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    // XXX: checkpoint if mode = graceful?
    
    CloseMessageCallback msg = new CloseMessageCallback(inbox(), 
                                                        stub());
    
    long offerTimeout = 0;
    
    inbox().offerAndWake(msg, offerTimeout);

    /*
    try {
      msg.get(10, TimeUnit.SECONDS);
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);
    }
    */
  }
  
  @Override
  public int hashCode()
  {
    return stub().hashCode();
  }

  @Override
  public boolean equals(Object o)
  {
    if (! (o instanceof ServiceRefPin)) {
      return false;
    }
    
    ServiceRefPin cb = (ServiceRefPin) o;

    return (stub().equals(cb.stub())
            && (inbox() == cb.inbox()));
  }
}
