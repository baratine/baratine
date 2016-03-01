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

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.MethodAmp;

/**
 * Abstract stream for an actor.
 */
abstract public class MethodAmpWrapper extends MethodAmpBase 
{
  abstract protected MethodAmp delegate();
  
  @Override
  public String name()
  {
    MethodAmp delegate = delegate();
    
    if (delegate != null) {
      return delegate.name();
    }
    else {
      return "null:" + getClass().getSimpleName();
    }
  }
  
  @Override
  public boolean isClosed()
  {
    return delegate().isClosed();
  }
  
  @Override
  public boolean isDirect()
  {
    MethodAmp delegate = delegate();
    
    if (delegate != null) {
      return delegate.isDirect();
    }
    else {
      return false;
    }
  }
  
  @Override
  public boolean isModify()
  {
    return delegate().isModify();
  }
  
  /*
  @Override
  public RampActor getActor()
  {
    return getDelegate().getActor();
  }
  */
  
  @Override
  public Annotation [] getAnnotations()
  {
    return delegate().getAnnotations();
  }
  
  @Override
  public Class<?>[] getParameterTypes()
  {
    return delegate().getParameterTypes();
  }
  
  @Override
  public Type [] getGenericParameterTypes()
  {
    return delegate().getGenericParameterTypes();
  }
  
  @Override
  public Annotation [][] getParameterAnnotations()
  {
    return delegate().getParameterAnnotations();
  }

  @Override
  public void send(HeadersAmp headers,
                   ActorAmp actor,
                   Object []args)
  {
    delegate().send(headers, actor, args);
  }

  @Override
  public void query(HeadersAmp headers,
                    Result<?> result,
                    ActorAmp actor,
                    Object []args)
  {
    delegate().query(headers, result, actor, args);
  }
  
  //
  // map-reduce methods
  //

  @Override
  public <T> void stream(HeadersAmp headers,
                           ResultStream<T> result,
                           ActorAmp actor,
                           Object []args)
  {
    delegate().stream(headers, result, actor, args);
  }

  /*
  @Override
  public MethodAmp toTail()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */

  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + delegate()
            + "]");
  }
}
