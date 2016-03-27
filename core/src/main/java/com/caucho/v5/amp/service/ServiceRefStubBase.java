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
import com.caucho.v5.amp.manager.ServicesAmpImpl;
import com.caucho.v5.amp.message.OnSaveRequestMessage;
import com.caucho.v5.amp.proxy.ProxyHandleAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.QueryRefAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.StubAmp;

import io.baratine.service.Cancel;
import io.baratine.service.Result;
import io.baratine.service.ServiceExceptionClosed;
import io.baratine.service.ServiceRef;

/**
 * Handles the context for an actor, primarily including its
 * query map.
 */
abstract class ServiceRefStubBase extends ServiceRefBase
{
  private final static Logger log
    = Logger.getLogger(ServiceRefStubBase.class.getName());
  
  private final InboxAmp _inbox;
  private final StubAmp _stub;

  public ServiceRefStubBase(StubAmp stub,
                             InboxAmp inbox)
  {
    _stub = stub;
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
    return _stub.isUp() && ! _inbox.isClosed();
  }

  @Override
  public boolean isClosed()
  {
    return _inbox.isClosed();
  }

  @Override
  public boolean isPublic()
  {
    return _stub.isPublic();
  }
  
  @Override
  public StubAmp stub()
  {
    return _stub;
  }
  
  @Override
  public MethodRefAmp getMethod(String methodName)
  {
    // start();
    
    MethodAmp method = _stub.getMethod(methodName);

    return createMethod(method);
  }
  
  @Override
  public Iterable<? extends MethodRefAmp> getMethods()
  {
    // start();
    
    ArrayList<MethodRefAmp> methods = new ArrayList<>();
    
    for (MethodAmp method : _stub.getMethods()) {
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
      
      StubAmp stubChild = manager().stubFactory().stub(child, config);

      return createChild(subpath, stubChild);
    }
  }
  
  protected ServiceRefAmp createChild(String address, 
                                      StubAmp child)
  {
    return new ServiceRefChild(address, child, _inbox);
  }                                      
  
  protected Object onLookupImpl(String path)
  {
    return getLookupActor().onLookup(path, this);
  }
  
  protected StubAmp getLookupActor()
  {
    // baratine/1618
    // ActorAmp actor = getInbox().getDirectActor();
    StubAmp actor = _stub;
    
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
    address = ServicesAmpImpl.toCanonical(address);
    
    ServiceRefAmp bindRef = new ServiceRefLocal(_stub, _inbox);
    
    manager().bind(bindRef, address);
    
    return bindRef;
  }
  
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
        return manager().toRef(listener);
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
}
