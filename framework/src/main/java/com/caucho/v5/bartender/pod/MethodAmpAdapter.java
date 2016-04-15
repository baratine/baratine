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

package com.caucho.v5.bartender.pod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.ParameterAmp;
import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.util.L10N;

import io.baratine.pipe.ResultPipeIn;
import io.baratine.pipe.ResultPipeOut;
import io.baratine.service.ResultChain;
import io.baratine.stream.ResultStream;

/**
 * MethodAmp adapted from a MethodRef, where the MethodRef has the sending
 * logic.
 * 
 * Use to match the proxy logic.
 */
public class MethodAmpAdapter implements MethodAmp
{
  private static final L10N L = new L10N(MethodAmpAdapter.class);
  private static final Logger log = Logger.getLogger(MethodAmpAdapter.class.getName());
  
  private final MethodRefAmp _methodRef;
  
  public MethodAmpAdapter(MethodRefAmp methodRef)
  {
    _methodRef = methodRef;
  }

  @Override
  public boolean isClosed()
  {
    return _methodRef.serviceRef().isClosed();
  }

  @Override
  public String name()
  {
    return _methodRef.getName();
  }

  @Override
  public boolean isDirect()
  {
    return false;
  }

  @Override
  public Annotation[] getAnnotations()
  {
    try {
      return _methodRef.getAnnotations();
    } catch (StackOverflowError e) {
      throw new UnsupportedOperationException(getClass().getName() + " " + _methodRef.getClass().getSimpleName());
    }
  }

  @Override
  public ParameterAmp[] parameters()
  {
    if (_methodRef instanceof MethodRefAmp) {
      try {
        return ((MethodRefAmp) _methodRef).parameters();
      } catch (StackOverflowError e) {
        throw new UnsupportedOperationException(getClass().getName() + " " + _methodRef.getClass().getSimpleName());
      }
    }
    else {
      return null;
    }
  }

  @Override
  public Class<?> getReturnType()
  {
    if (_methodRef instanceof MethodRefAmp) {
      try {
        Type type = ((MethodRefAmp) _methodRef).getReturnType();
        
        if (type instanceof Class) {
          return (Class) type;
        }
        else {
          return null;
        }
      } catch (StackOverflowError e) {
        throw new UnsupportedOperationException(getClass().getName() + " " + _methodRef.getClass().getSimpleName());
      }
    }
    else {
      return null;
    }
  }

  @Override
  public boolean isVarArgs()
  {
    return false;
  }

  @Override
  public void send(HeadersAmp headers, StubAmp actor)
  {
    _methodRef.send(headers);
  }

  @Override
  public void send(HeadersAmp headers, StubAmp actor, Object arg1)
  {
    _methodRef.send(headers, arg1);
  }

  @Override
  public void send(HeadersAmp headers, StubAmp actor, Object arg1, Object arg2)
  {
    _methodRef.send(headers, arg1, arg2);
  }

  @Override
  public void send(HeadersAmp headers, StubAmp actor, Object arg1,
                   Object arg2, Object arg3)
  {
    _methodRef.send(headers, arg1, arg2, arg3);
  }

  @Override
  public void send(HeadersAmp headers, StubAmp actor, Object[] args)
  {
    _methodRef.send(headers, args);
  }

  @Override
  public void query(HeadersAmp headers, ResultChain<?> result, StubAmp actor)
  {
    _methodRef.query(headers, result);
  }

  @Override
  public void query(HeadersAmp headers, ResultChain<?> result, StubAmp actor,
                    Object arg1)
  {
    _methodRef.query(headers, result, arg1);
  }

  @Override
  public void query(HeadersAmp headers, ResultChain<?> result, StubAmp actor,
                    Object arg1, Object arg2)
  {
    _methodRef.query(headers, result, arg1, arg2);
  }

  @Override
  public void query(HeadersAmp headers, ResultChain<?> result, StubAmp actor,
                    Object arg1, Object arg2, Object arg3)
  {
    _methodRef.query(headers, result, arg1, arg2, arg3);
  }

  @Override
  public void query(HeadersAmp headers, ResultChain<?> result, StubAmp actor,
                    Object[] args)
  {
    _methodRef.query(headers, result, args);
  }
  
  @Override
  public Object shim(Object value)
  {
    return _methodRef.method().shim(value);
  }

  /*
  @Override
  public <T,R> void stream(HeadersAmp headers, 
                           QueryRefAmp queryRef, 
                           ActorAmp actor,
                           CollectorAmp<T,R> consumer, 
                           Object[] args)
  {
    MethodRefAmp methodRef = (MethodRefAmp) _methodRef;
    
    methodRef.collect(headers, queryRef, (CollectorAmp) consumer, args);
  }
  */

  @Override
  public <T> void stream(HeadersAmp headers,
                         ResultStream<T> result, 
                         StubAmp actor,
                         Object[] args)
  {
    MethodRefAmp methodRef = (MethodRefAmp) _methodRef;
    
    methodRef.stream(headers, result, args);
  }

  @Override
  public <T> void outPipe(HeadersAmp headers,
                          ResultPipeOut<T> result, 
                          StubAmp actor,
                          Object[] args)
  {
    result.fail(new UnsupportedOperationException(getClass().getName()));
  }

  @Override
  public <T> void inPipe(HeadersAmp headers,
                          ResultPipeIn<T> result, 
                          StubAmp actor,
                          Object[] args)
  {
    result.fail(new UnsupportedOperationException(getClass().getName()));
  }

  /*
  @Override
  public ActorAmp getActorInvoke(ActorAmp actorDeliver)
  {
    return actorDeliver;
  }
  */
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _methodRef + "]";
  }
}
