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

import java.io.Serializable;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Type;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.spi.QueryRefAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.stub.StubAmp;

import io.baratine.service.Cancel;
import io.baratine.service.Result;
import io.baratine.service.ServiceRef;

/**
 * Wrapper for service filtering.
 */
abstract public class ServiceRefWrapper implements ServiceRefAmp, Serializable
{
  private static final Logger log
    = Logger.getLogger(ServiceRefWrapper.class.getName());
  
  abstract protected ServiceRefAmp delegate();

  @Override
  public String address()
  {
    return delegate().address();
  }
  
  @Override
  public InboxAmp inbox()
  {
    try {
      return delegate().inbox();
    } catch (Exception e) {
      System.out.println("EXN: " + this + " " + e);
      e.printStackTrace();
      throw e;
    }
  }
  
  @Override
  public ServiceManagerAmp manager()
  {
    return delegate().manager();
  }
  
  @Override
  public StubAmp stub()
  {
    return delegate().stub();
  }
  
  @Override
  public boolean isUp()
  {
    ServiceRefAmp delegate = delegate();
    
    return delegate != null && delegate.isUp();
  }
  
  @Override
  public boolean isClosed()
  {
    ServiceRefAmp delegate = delegate();
    
    return delegate == null || delegate.isClosed();
  }
  
  @Override
  public boolean isPublic()
  {
    return delegate().isPublic();
  }
  
  @Override
  public ClassLoader getDelegateClassLoader()
  {
    return delegate().getDelegateClassLoader();
  }
  
  /*
  @Override
  public String []getRemoteRoles()
  {
    return getDelegate().getRemoteRoles();
  }
  
  @Override
  public boolean isRemoteSecure()
  {
    return getDelegate().isRemoteSecure();
  }
  */
  
  @Override
  public AnnotatedType api()
  {
    ServiceRefAmp delegate = delegate();
    
    if (delegate == null) {
      System.out.println("FAILED_DELEGATE: " + this);
    }
    
    return delegate().api();
  }
  
  /*
  @Override
  public Annotation []getApiAnnotations()
  {
    return getDelegate().getApiAnnotations();
  }
  */

  @Override
  public MethodRefAmp getMethod(String methodName)
  {
    try {
      return delegate().getMethod(methodName);
    } catch (Exception e) {
      return new MethodRefException(this, methodName, e);
    }
  }

  @Override
  public MethodRefAmp getMethod(String methodName, Type returnType)
  {
    return delegate().getMethod(methodName, returnType);
  }
  
  public Iterable<? extends MethodRefAmp> getMethods()
  {
    return delegate().getMethods();
  }

  @Override
  public void offer(MessageAmp message)
  {
    delegate().offer(message);
  }

  @Override
  public QueryRefAmp removeQueryRef(long id)
  {
    return delegate().removeQueryRef(id);
  }

  @Override
  public QueryRefAmp getQueryRef(long id)
  {
    return delegate().getQueryRef(id);
  }

  @Override
  public Cancel consume(Object consumer)
  {
    return delegate().consume(consumer);
  }

  @Override
  public Cancel subscribe(Object subscriber)
  {
    return delegate().subscribe(subscriber);
  }

  /*
  @Override
  public ServiceRefAmp unsubscribe(Object service)
  {
    getDelegate().unsubscribe(service);
    
    return this;
  }
  */
  
  @Override
  public ServiceRefAmp lookup()
  {
    // return getManager().lookup(getAddress());
    return this;
  }

  @Override
  public ServiceRefAmp onLookup(String path)
  {
    return delegate().onLookup(path);
  }
  
  @Override
  public ServiceRefAmp lookup(String path)
  {
    return manager().service(address() + path);
  }
  
  /*
  @Override
  public ServiceRef lookup(String path)
  {
    // return getDelegate().lookup(path);
  }
  */
  
  /*
  @Override
  public ServiceRef partition(int hash)
  {
    return getDelegate().partition(hash);
  }
  
  @Override
  public int partitionSize()
  {
    return getDelegate().partitionSize();
  }
  */

  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
  }

  @Override
  public void close()
  {
    shutdown(ShutdownModeAmp.GRACEFUL);
  }

  /*
  @Override
  public ServiceRef unbind(String address)
  {
    return getDelegate().unbind(address);
  }
  */

  @Override
  public <T> T as(Class<T> api, Class<?>... apis)
  {
    return manager().newProxy(this, api, apis);
  }

  @Override
  public ServiceRefAmp pin(Object listener)
  {
    return manager().pin(this, listener);
  }

  @Override
  public ServiceRefAmp pin(Object listener, String path)
  {
    return manager().pin(this, listener, path);
  }

  @Override
  public ServiceRefAmp bind(String address)
  {
    manager().bind(this, address);
    
    return this;
  }
  
  @Override
  public ServiceRefAmp start()
  {
    return this;
  }
  
  @Override
  public ServiceRef save(Result<Void> result)
  {
    delegate().save(result);
    
    return this;
  }
  
  private Object writeReplace()
  {
    try {
      return new ServiceRefHandle(address(), manager());
    } catch (IllegalStateException e) {
      if (log.isLoggable(Level.FINEST)) {
        log.log(Level.FINEST, e.toString(), e);
      }
      
      return new ServiceRefHandle(address(), null);
    }
  }

  @Override
  public String toString()
  {
    //return getClass().getSimpleName() + "[" + getDelegate() + "]";
    return getClass().getSimpleName() + "[" + address() + "]";
  }
}
