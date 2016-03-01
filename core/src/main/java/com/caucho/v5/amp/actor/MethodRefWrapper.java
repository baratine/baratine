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

package com.caucho.v5.amp.actor;

import io.baratine.service.Result;
import io.baratine.service.ResultStream;
import io.baratine.spi.Headers;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.MethodAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;

/**
 * Sender for an actor ref.
 */
abstract public class MethodRefWrapper implements MethodRefAmp
{
  abstract protected MethodRefAmp getDelegate();

  @Override
  public ServiceRefAmp getService()
  {
    return getDelegate().getService();
  }

  @Override
  public String getName()
  {
    return getDelegate().getName();
  }

  @Override
  public boolean isUp()
  {
    MethodRefAmp delegate = getDelegate();
    
    return delegate != null && delegate.isUp();
  }

  @Override
  public boolean isClosed()
  {
    MethodRefAmp delegate = getDelegate();
    
    return delegate == null || delegate.isClosed();
  }

  @Override
  public InboxAmp getInbox()
  {
    return getDelegate().getInbox();
  }

  @Override
  public void offer(MessageAmp msg)
  {
    getDelegate().offer(msg);
  }
  
  @Override
  public Class<?> []getParameterClasses()
  {
    MethodRefAmp delegate = getDelegate();
    
    if (delegate != null) {
      return delegate.getParameterClasses();
    }
    else {
      return new Class[0];
    }
  }
  
  @Override
  public Annotation []getAnnotations()
  {
    return getDelegate().getAnnotations();
  }
  
  @Override
  public Type getReturnType()
  {
    MethodRefAmp delegate = getDelegate();
    
    if (delegate != null) {
      return delegate.getReturnType();
    }
    else {
      return Object.class;
    }
  }
  
  @Override
  public Type []getParameterTypes()
  {
    MethodRefAmp delegate = getDelegate();
    
    if (delegate != null) {
      return delegate.getParameterTypes();
    }
    else {
      return new Type[0];
    }
  }
  
  @Override
  public Annotation [][]getParameterAnnotations()
  {
    return getDelegate().getParameterAnnotations();
  }
  
  @Override
  public boolean isVarArgs()
  {
    MethodRefAmp delegate = getDelegate();
    
    if (delegate != null) {
      return delegate.isVarArgs();
    }
    else {
      return false;
    }
  }

  @Override
  public MethodAmp method()
  {
    return getDelegate().method();
  }

  @Override
  public ActorAmp getActor(ActorAmp actorDeliver)
  {
    return getDelegate().getActor(actorDeliver);
  }
  
  @Override
  public void send(Headers headers, Object... args)
  {
    getDelegate().send(headers, args);
  }
  
  @Override
  public void send(Object... args)
  {
    getDelegate().send(args);
  }

  @Override
  public <T> void query(Result<T> cb,
                        Object... args)
  {
    getDelegate().query(cb, args);
  }

  @Override
  public <T> void query(Headers headers,
                        Result<T> cb,
                        Object... args)
  {
    getDelegate().query(headers, cb, args);
  }

  @Override
  public <T> void query(Headers headers,
                        Result<T> cb, 
                        long timeout, TimeUnit timeUnit,
                        Object... args)
  {
    getDelegate().query(headers, cb, timeout, timeUnit, args);
  }

  @Override
  public <T> void stream(Headers headers,
                         ResultStream<T> result,
                         Object... args)
  {
    getDelegate().stream(headers, result, args);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getDelegate() + "]";
  }
}
