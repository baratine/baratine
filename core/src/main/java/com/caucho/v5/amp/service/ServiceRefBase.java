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
import io.baratine.service.Result;
import io.baratine.service.ServiceRef;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.message.OnSaveRequestMessage;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.spi.QueryRefAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.stub.StubAmp;

/**
 * Abstract implementation for a service ref.
 */
abstract public class ServiceRefBase implements ServiceRefAmp, Serializable
{
  private static final Logger log
    = Logger.getLogger(ServiceRefBase.class.getName());
  
  @Override
  abstract public String address();
  
  @Override
  public boolean isUp()
  {
    return ! isClosed();
  }
  
  @Override
  public boolean isClosed()
  {
    return false;
  }
  
  @Override
  public boolean isPublic()
  {
    return false;
  }
  
  /*
  @Override
  public String []getRemoteRoles()
  {
    return null;
  }
  
  @Override
  public boolean isRemoteSecure()
  {
    return false;
  }
  */
  
  @Override
  public InboxAmp inbox()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public ServiceManagerAmp manager()
  {
    return inbox().manager();
  }
  
  @Override
  public StubAmp getActor()
  {
    return null;
  }
  
  @Override
  public Class<?> apiClass()
  {
    StubAmp actor = getActor();
    
    if (actor != null) {
      return actor.getApiClass();
    }
    else {
      return Object.class;
    }
  }
  
  /*
  @Override
  public Annotation[] getApiAnnotations()
  {
    ActorAmp actor = getActor();
    
    if (actor != null) {
      return actor.getApiAnnotations();
    }
    else {
      return new Annotation[0];
    }
  }
  */

  @Override
  public MethodRefAmp getMethod(String methodName)
  {
    return new MethodRefNull(this, methodName);
  }
  
  @Override
  public MethodRefAmp getMethod(String methodName, Type returnType)
  {
    return getMethod(methodName);
  }
  
  @Override
  public Iterable<? extends MethodRefAmp> getMethods()
  {
    return new ArrayList<MethodRefAmp>();
  }

  @Override
  public void offer(MessageAmp msg)
  {
    throw new UnsupportedOperationException(this + " " + getClass().getName());
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
  public ServiceRefAmp lookup()
  {
    return null;
  }
  
  @Override
  public ServiceRefAmp onLookup(String path)
  {
    return null;
  }
  
  @Override
  public ServiceRefAmp lookup(String path)
  {
    return manager().service(address() + path);
  }
  
  public ServiceRefAmp partition(int hash)
  {
    return this;
  }
  
  public int partitionSize()
  {
    return 1;
  }

  @Override
  public ServiceRefAmp start()
  {
    return this;
  }

  @Override
  public ServiceRef save(Result<Void> result)
  {
    result.ok(null);
    
    return this;
  }

  /*
  @Override
  public ServiceRef service(Supplier<?> supplier, ServiceConfig config)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */

  /*
  @Override
  public ServiceRef unbind(String address)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */

  @Override
  public <T> T as(Class<T> api, Class<?>... apis)
  {
    return manager().newProxy(this, api, apis);
  }

  @Override
  public ServiceRefAmp pin(Object serviceImpl)
  {
    Objects.requireNonNull(serviceImpl);
    
    // start();
    
    return manager().pin(this, serviceImpl);
  }

  @Override
  public ServiceRefAmp pin(Object serviceImpl, String path)
  {
    Objects.requireNonNull(serviceImpl);
    
    // start();
    
    return manager().pin(this, serviceImpl, path);
  }

  @Override
  public ServiceRefAmp bind(String address)
  {
    manager().bind(this, address);
    
    return this;
  }

  @Override
  public Cancel consume(Object consumer)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public Cancel subscribe(Object subscriber)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public void close()
  {
    shutdown(ShutdownModeAmp.GRACEFUL);
  }

  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
  }
  
  protected void shutdownCheckpoint(ShutdownModeAmp mode)
  {
    try {
      long timeout = 0;
    
      if (mode == ShutdownModeAmp.GRACEFUL) {
        OnSaveRequestMessage checkpointMsg
          = new OnSaveRequestMessage(inbox(), Result.ignore());
    
        inbox().offerAndWake(checkpointMsg, timeout);
      }
    
      //getInbox().offerAndWake(new MessageOnShutdown(getInbox(), mode), timeout);
      inbox().shutdown(mode);
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);
    }
  }
  
  private Object writeReplace()
  {
    return new ServiceRefHandle(address(), manager());
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + address() + "]";
  }
}
