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

package com.caucho.v5.bartender.xa;

import io.baratine.service.MethodRef;
import io.baratine.service.Result;
import io.baratine.spi.Headers;
import io.baratine.stream.ResultStream;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.message.HeadersNull;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.MethodAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.bartender.pod.MethodAmpAdapter;

/**
 * Wrapper for XA method calls
 */
public class MethodRefXA implements MethodRefAmp
{
  private ServiceRefXA _service;
  private MethodRefAmp _delegate;
  private MethodRefAmp _prepare;
  private MethodRefAmp _rollback;
  
  MethodRefXA(ServiceRefXA service, MethodRefAmp delegate)
  {
    _service = service;
    _delegate = delegate;
    
    _prepare = findPrepare();
    _rollback = findRollback();
  }
  
  protected MethodRefAmp getDelegate()
  {
    return _delegate;
  }

  @Override
  public String getName()
  {
    return getDelegate().getName();
  }

  @Override
  public Annotation[] getAnnotations()
  {
    return getDelegate().getAnnotations();
  }

  @Override
  public Type getReturnType()
  {
    return getDelegate().getReturnType();
  }

  @Override
  public Type[] getParameterTypes()
  {
    return getDelegate().getParameterTypes();
  }

  @Override
  public boolean isVarArgs()
  {
    return getDelegate().isVarArgs();
  }

  @Override
  public Annotation[][] getParameterAnnotations()
  {
    return getDelegate().getParameterAnnotations();
  }

  @Override
  public Class<?>[] getParameterClasses()
  {
    return getDelegate().getParameterClasses();
  }
  
  public MethodRefAmp getPrepare()
  {
    return _prepare;
  }
  
  public MethodRefAmp getRollback()
  {
    return _rollback;
  }
  
  private MethodRefAmp findPrepare()
  {
    for (MethodRefAmp method : _service.getMethods()) {
      Prepare prepare = getAnnotation(method, Prepare.class);
      
      if (prepare != null && getName().equals(prepare.value())) {
        return method;
      }
    }
    
    return null;
  }
  
  private MethodRefAmp findRollback()
  {
    for (MethodRefAmp method : _service.getMethods()) {
      Rollback rollback = getAnnotation(method, Rollback.class);
      
      if (rollback != null && getName().equals(rollback.value())) {
        return method;
      }
    }
    
    return null;
  }
  
  private <T> T getAnnotation(MethodRefAmp method, Class<T> annType)
  {
    Annotation []annList = method.getAnnotations();
    
    if (annList == null) {
      return null;
    }
    
    for (Annotation ann : annList) {
      if (ann.annotationType().equals(annType)) {
        return (T) ann;
      }
    }
    
    return null;
  }

  @Override
  public void send(Headers headers, Object... args)
  {
    TransactionImpl xa = XAServiceImpl.getTransaction();
    
    if (xa == null) {
      getDelegate().send(headers, args);
      return;
    }
    
    xa.send(getService(), this, headers, args);
  }

  @Override
  public void send(Object... args)
  {
    send(HeadersNull.NULL, args);
  }

  @Override
  public <T> void query(Headers headers, 
                        Result<T> cb, 
                        Object... args)
  {
    TransactionImpl xa = XAServiceImpl.getTransaction();
    
    if (xa == null) {
      getDelegate().query(headers, cb, args);
      return;
    }
    
    cb.ok(null);
    
    xa.query(getService(), this, headers, args);
  }

  @Override
  public <T> void query(Result<T> result, Object... args)
  {
    query(HeadersNull.NULL, result, args);
  }

  @Override
  public <T> void query(Headers headers, 
                        Result<T> cb, 
                        long timeout,
                        TimeUnit timeUnit, 
                        Object... args)
  {
    TransactionImpl xa = XAServiceImpl.getTransaction();
    
    if (xa == null) {
      getDelegate().query(headers, cb, timeout, timeUnit, args);
      return;
    }
    
    cb.ok(null);
    
    xa.query(getService(), this, headers, args);
  }

  @Override
  public <T> void stream(Headers headers, 
                         ResultStream<T> result,
                         Object... args)
  {
    TransactionImpl xa = XAServiceImpl.getTransaction();
    
    if (xa == null) {
      getDelegate().stream(headers, result, args);
      return;
    }
    
    result.ok();
    
    // XXX: xa.collect(getService(), this, headers, collector, args);
  }

  @Override
  public ServiceRefAmp getService()
  {
    return _service;
  }

  @Override
  public boolean isUp()
  {
    return getDelegate().isUp();
  }

  @Override
  public boolean isClosed()
  {
    return getDelegate().isClosed();
  }

  @Override
  public InboxAmp getInbox()
  {
    return _service.inbox();
  }

  @Override
  public MethodAmp method()
  {
    return new MethodAmpAdapter(this);
  }

  @Override
  public void offer(MessageAmp message)
  {
    message.invoke(_service.inbox(), _service.getActor());
  }

  @Override
  public ActorAmp getActor(ActorAmp actorDeliver)
  {
    return actorDeliver;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getDelegate() + "]";
  }
}
