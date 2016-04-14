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

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.ParameterAmp;
import com.caucho.v5.amp.stub.StubAmp;

import io.baratine.service.Result;
import io.baratine.service.ResultChain;
import io.baratine.spi.Headers;
import io.baratine.stream.ResultStream;

/**
 * Sender for an actor ref.
 */
abstract public class MethodRefWrapper implements MethodRefAmp
{
  abstract protected MethodRefAmp delegate();

  @Override
  public ServiceRefAmp serviceRef()
  {
    return delegate().serviceRef();
  }

  @Override
  public String getName()
  {
    return delegate().getName();
  }

  @Override
  public boolean isUp()
  {
    MethodRefAmp delegate = delegate();
    
    return delegate != null && delegate.isUp();
  }

  @Override
  public boolean isClosed()
  {
    MethodRefAmp delegate = delegate();
    
    return delegate == null || delegate.isClosed();
  }

  @Override
  public InboxAmp inbox()
  {
    return delegate().inbox();
  }

  @Override
  public void offer(MessageAmp msg)
  {
    delegate().offer(msg);
  }
  
  @Override
  public ParameterAmp []parameters()
  {
    MethodRefAmp delegate = delegate();
    
    if (delegate != null) {
      return delegate.parameters();
    }
    else {
      return new ParameterAmp[0];
    }
  }
  
  @Override
  public Annotation []getAnnotations()
  {
    return delegate().getAnnotations();
  }
  
  @Override
  public Type getReturnType()
  {
    MethodRefAmp delegate = delegate();
    
    if (delegate != null) {
      return delegate.getReturnType();
    }
    else {
      return Object.class;
    }
  }
  
  @Override
  public boolean isVarArgs()
  {
    MethodRefAmp delegate = delegate();
    
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
    return delegate().method();
  }

  @Override
  public StubAmp stubActive(StubAmp actorDeliver)
  {
    return delegate().stubActive(actorDeliver);
  }
  
  @Override
  public void send(Headers headers, Object... args)
  {
    delegate().send(headers, args);
  }
  
  @Override
  public void send(Object... args)
  {
    delegate().send(args);
  }

  @Override
  public <T> void query(Result<T> cb,
                        Object... args)
  {
    delegate().query(cb, args);
  }

  @Override
  public <T> void query(Headers headers,
                        ResultChain<T> cb,
                        Object... args)
  {
    delegate().query(headers, cb, args);
  }

  @Override
  public <T> void query(Headers headers,
                        ResultChain<T> cb, 
                        long timeout, TimeUnit timeUnit,
                        Object... args)
  {
    delegate().query(headers, cb, timeout, timeUnit, args);
  }

  @Override
  public <T> void stream(Headers headers,
                         ResultStream<T> result,
                         Object... args)
  {
    delegate().stream(headers, result, args);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + delegate() + "]";
  }
}
