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

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.manager.ServiceManagerAmpImpl;
import com.caucho.v5.amp.message.ConsumeMessage;
import com.caucho.v5.amp.message.OnSaveRequestMessage;
import com.caucho.v5.amp.message.SubscribeMessage;
import com.caucho.v5.amp.message.UnsubscribeMessage;
import com.caucho.v5.amp.proxy.ProxyHandleAmp;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.MethodAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.QueryRefAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

import io.baratine.service.Cancel;
import io.baratine.service.Result;
import io.baratine.service.ServiceExceptionClosed;
import io.baratine.service.ServiceRef;

/**
 * Handles the context for an actor, primarily including its
 * query map.
 */
abstract class ServiceRefActorBase extends ServiceRefBase
{
  private final static Logger log
    = Logger.getLogger(ServiceRefActorBase.class.getName());
  
  private final InboxAmp _inbox;
  private final ActorAmp _actor;

  public ServiceRefActorBase(ActorAmp actor,
                             InboxAmp inbox)
  {
    _actor = actor;
    _inbox = inbox;
  }

  @Override
  public String address()
  {
    return _inbox.getAddress();
  }

  @Override
  public boolean isUp()
  {
    return _actor.isUp() && ! _inbox.isClosed();
  }

  @Override
  public boolean isClosed()
  {
    return _inbox.isClosed();
  }

  @Override
  public boolean isPublic()
  {
    return _actor.isExported();
  }
  
  @Override
  public ActorAmp getActor()
  {
    return _actor;
  }
  
  @Override
  public MethodRefAmp getMethod(String methodName)
  {
    // start();
    
    MethodAmp method = _actor.getMethod(methodName);

    return createMethod(method);
  }
  
  @Override
  public Iterable<? extends MethodRefAmp> getMethods()
  {
    // start();
    
    ArrayList<MethodRefAmp> methods = new ArrayList<>();
    
    for (MethodAmp method : _actor.getMethods()) {
      MethodRefAmp methodRef = createMethod(method);
      
      methods.add(methodRef);
    }
    
    return methods;
  }
  
  private MethodRefAmp createMethod(MethodAmp method)
  {
    return new MethodRefImpl(this, method);
    //return new MethodRefImpl(this, method, _inbox);
    /*
    if (method.isDirect()) {
      return new MethodRefDirect(this, method, _inbox);
    }
    else {
      return new MethodRefImpl(this, method, _inbox);
    }
    */
  }
  
  @Override
  public QueryRefAmp removeQueryRef(long id)
  {
    return inbox().removeQueryRef(id);
  }
  
  @Override
  public QueryRefAmp getQueryRef(long id)
  {
    return inbox().getQueryRef(id);
  }
  
  @Override
  public void offer(MessageAmp message)
  {
    long timeout = InboxAmp.TIMEOUT_INFINITY;
    
    inbox().offerAndWake(message, timeout);
  }
  
  @Override
  public InboxAmp inbox()
  {
    return _inbox;
  }
  
  @Override
  public ServiceRefAmp onLookup(String path)
  {
    Object child;
    
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(manager())) {
      init();
    
      Object oldContext = outbox.getAndSetContext(inbox());
      try {
        child = onLookupImpl(path);
      } finally {
        outbox.getAndSetContext(oldContext);
      }
    }

    if (child == null) {
      return null;
    }
    else if (child instanceof ServiceRefAmp) {
      return (ServiceRefAmp) child;
    }
    else if (child instanceof ProxyHandleAmp) {
      ProxyHandleAmp handle = (ProxyHandleAmp) child;
      
      return handle.__caucho_getServiceRef();
    }
    else {
      // object children use the same inbox
      String subpath = address() + path;
      
      ServiceConfig config = null;
      
      ActorAmp actorChild = manager().createActor(child, config);

      return createChild(subpath, actorChild);
    }
  }
  
  protected ServiceRefAmp createChild(String address, 
                                      ActorAmp child)
  {
    return new ServiceRefChild(address, child, _inbox);
  }                                      
  
  protected Object onLookupImpl(String path)
  {
    return getLookupActor().onLookup(path, this);
  }
  
  protected ActorAmp getLookupActor()
  {
    // baratine/1618
    // ActorAmp actor = getInbox().getDirectActor();
    ActorAmp actor = _actor;
    
    return actor;
  }
  
  @Override
  public ServiceRefAmp lookup(String path)
  {
    return manager().service(address() + path);
  }

  @Override
  public ServiceRefAmp bind(String address)
  {
    address = ServiceManagerAmpImpl.toCanonical(address);
    
    ServiceRefAmp bindRef = new ServiceRefBound(address, _actor, _inbox);
    
    manager().bind(bindRef, address);
    
    return bindRef;
  }

  @Override
  public Cancel consume(Object service)
  {
    start();
    
    ServiceRef subscriber = toSubscriber(service);
    
    offer(new ConsumeMessage(inbox(), subscriber));
    
    return new SubscriberCancel(subscriber);
  }

  @Override
  public Cancel subscribe(Object service)
  {
    start();
    
    ServiceRef subscriber = toSubscriber(service);
    
    offer(new SubscribeMessage(inbox(), subscriber));
    
    return new SubscriberCancel(subscriber);
  }

  /*
  @Override
  public CancelHandle unsubscribe(Object service)
  {
    start();
    
    ServiceRef subscriber = toSubscriber(service);
    
    offer(new UnsubscribeMessage(getInbox(), subscriber)); 
    
    return new SubscriberCancel(subscriber);
  }
  */
  
  protected ServiceRefAmp toSubscriber(Object listener)
  {
    if (listener instanceof ServiceRefAmp) {
      return (ServiceRefAmp) listener;
    }
    else {
      ServiceRef selfServiceRef = ServiceRef.current();

      if (selfServiceRef != null) {
        return (ServiceRefAmp) selfServiceRef.pin(listener);
      }
      else {
        return manager().toService(listener);
      }
    }
  }
  
  @Override
  public ServiceRefAmp start()
  {
    inbox().start();
    
    return this;
  }
  
  private ServiceRefAmp init()
  {
    inbox().init();
    
    return this;
  }

  @Override
  public ServiceRefAmp save(Result<Void> result)
  {
    start();
    
    if (! isUp()) {
      result.ok(null);
      
      return this;
    }
    
    // JournalAmp journal = getActor().getJournal();
    
    offer(new OnSaveRequestMessage(inbox(), result));

    // offer(new CheckpointMessage(getMailbox()));
    
    return this;
  }

  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    InboxAmp inbox = inbox();
    
    if (inbox.isClosed()) {
      return;
    }
    
    /*
    if (inbox.isLifecycleAware() && mode == ShutdownModeAmp.GRACEFUL) {
      ResultFuture<Boolean> future = new ResultFuture<>();
          
      try {
        OnSaveRequestMessage checkpoint
          = new OnSaveRequestMessage(inbox, future);
            
        offer(checkpoint);
            
        future.get(10, TimeUnit.SECONDS);
      } catch (Exception e) {
        log.log(Level.FINER, e.toString(), e);
      }
    }
    */

    inbox().shutdown(mode);
    //offer(new MessageOnShutdown(getInbox(), mode));
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + address() + "]";
  }
  
  private class SubscriberCancel implements Cancel {
    private ServiceRefAmp _subscriber;
    private boolean _isCancelled;
    
    SubscriberCancel(ServiceRef subscriber)
    {
      _subscriber = (ServiceRefAmp) subscriber;
      
    }
    @Override
    public void cancel()
    {
      if (! _isCancelled) {
        _isCancelled = true;

        try {
          offer(new UnsubscribeMessage(ServiceRefActorBase.this, _subscriber));
        } catch (ServiceExceptionClosed e) {
          log.log(Level.FINEST, e.toString(), e);
        }
      }
    }
    
    @Override
    public String toString()
    {
      return (getClass().getSimpleName()
              + "[" + ServiceRefActorBase.this.address()
              + ",sub=" + _subscriber.address()
              + "]");
    }
    
  }
}
