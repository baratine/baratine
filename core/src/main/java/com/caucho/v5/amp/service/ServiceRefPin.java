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

import io.baratine.service.Cancel;
import io.baratine.service.ServiceExceptionClosed;

import java.lang.reflect.Type;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.message.CloseMessageCallback;
import com.caucho.v5.amp.message.ConsumeMessageCallback;
import com.caucho.v5.amp.message.SubscribeMessageCallback;
import com.caucho.v5.amp.message.UnsubscribeMessage;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.StubAmp;

/**
 * Service ref for an object pinned to a parent inbox.
 */
public class ServiceRefPin extends ServiceRefActorBase
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
      return "callback:" + getActor().name() + "@" + inbox().getAddress();
    }
  }
  
  @Override
  public MethodRefAmp getMethod(String methodName)
  {
    MethodAmp methodBean = getActor().getMethod(methodName);
    
    //MethodAmp methodCallback = new MethodAmpChild(methodBean, getActor());
    //return new MethodRefImpl(this, methodCallback, getInbox());
    
    //return new MethodRefImpl(this, methodBean, getInbox());
    return new MethodRefImpl(this, methodBean);
  }
  
  @Override
  public MethodRefAmp getMethod(String methodName, Type type)
  {
    MethodAmp methodBean = getActor().getMethod(methodName);
    //MethodAmp methodCallback = new MethodAmpChild(methodBean, getActor());

    //return new MethodRefImpl(this, methodCallback, getInbox());
    
    //return new MethodRefImpl(this, methodBean, getInbox());
    return new MethodRefImpl(this, methodBean);
  }
  
  @Override
  public Object onLookupImpl(String path)
  {
    return getActor().onLookup(path, this);
  }

  @Override
  public Cancel consume(Object service)
  {
    ServiceRefAmp subscriber = toSubscriber(service);
    
    offer(new ConsumeMessageCallback(inbox(), subscriber, getActor()));

    return new SubscriberCancelPin(subscriber);
  }

  @Override
  public Cancel subscribe(Object service)
  {
    ServiceRefAmp subscriber = toSubscriber(service);
    
    offer(new SubscribeMessageCallback(inbox(), subscriber, getActor()));
    
    return new SubscriberCancelPin(subscriber);
  }

  /*
  @Override
  public ServiceRefAmp unsubscribe(Object service)
  {
    offer(new UnsubscribeMessageCallback(getInbox(), 
                                         toSubscriber(service),
                                         getActor()));
    
    return this;
  }
  */

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
                                                        getActor());
    
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
    return getActor().hashCode();
  }

  @Override
  public boolean equals(Object o)
  {
    if (! (o instanceof ServiceRefPin)) {
      return false;
    }
    
    ServiceRefPin cb = (ServiceRefPin) o;

    return (getActor().equals(cb.getActor())
            && (inbox() == cb.inbox()));
  }
  
  private class SubscriberCancelPin implements Cancel {
    private ServiceRefAmp _subscriber;
    private boolean _isCancelled;
    
    SubscriberCancelPin(ServiceRefAmp subscriber)
    {
      _subscriber = subscriber;
      
    }
    @Override
    public void cancel()
    {
      if (! _isCancelled) {
        _isCancelled = true;

        /*
        offer(new UnsubscribeMessageCallback(getInbox(),
                                             _subscriber));
                                             */
        try {
          offer(new UnsubscribeMessage(ServiceRefPin.this, _subscriber));
        } catch (ServiceExceptionClosed e) {
          log.log(Level.FINEST, e.toString(), e);
        }
      }
    }
    
    @Override
    public String toString()
    {
      return (getClass().getSimpleName()
              + "[" + ServiceRefPin.this.address()
              + ",sub=" + _subscriber.address()
              + "]");
    }
    
  }
}
