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

package com.caucho.v5.amp.stub;

import java.lang.annotation.Annotation;
import java.util.Arrays;

import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.util.L10N;

import io.baratine.pipe.ResultPipeIn;
import io.baratine.pipe.ResultPipeOut;
import io.baratine.service.ResultChain;
import io.baratine.service.ServiceExceptionMethodNotFound;
import io.baratine.stream.ResultStream;

/**
 * Abstract stream for an actor.
 */
public class MethodAmpBase implements MethodAmp
{
  private static final L10N L = new L10N(MethodAmpBase.class);
  
  @Override
  public String name()
  {
    return "unknown[" + getClass().getSimpleName() + "]";
  }
  
  @Override
  public boolean isClosed()
  {
    return false;
  }
  
  @Override
  public boolean isDirect()
  {
    return false;
  }
  
  @Override
  public Annotation [] getAnnotations()
  {
    return null;
  }
  
  @Override
  public Class<?> getReturnType()
  {
    return Object.class;
  }
  
  @Override
  public ParameterAmp[] parameters()
  {
    return null;
  }
  
  @Override
  public boolean isVarArgs()
  {
    return false;
  }

  @Override
  public void send(HeadersAmp headers,
                   StubAmp actor)
  {
    send(headers, actor, new Object[0]);
  }

  @Override
  public void send(HeadersAmp headers,
                   StubAmp actor,
                   Object arg1)
  {
    send(headers, actor, new Object[] { arg1 });
  }

  @Override
  public void send(HeadersAmp headers,
                   StubAmp actor,
                   Object arg1,
                   Object arg2)
  {
    send(headers, actor, new Object[] { arg1, arg2 });
  }

  @Override
  public void send(HeadersAmp headers,
                   StubAmp actor,
                   Object arg1,
                   Object arg2,
                   Object arg3)
  {
    send(headers, actor, new Object[] { arg1, arg2, arg3 });
  }

  @Override
  public void send(HeadersAmp headers,
                   StubAmp actor,
                   Object []args)
  {
  }

  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor)
  {
    query(headers, result, actor, new Object[] {});
  }

  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor,
                    Object arg1)
  {
    query(headers, result, actor, new Object[] { arg1 });
  }

  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor,
                    Object arg1,
                    Object arg2)
  {
    query(headers, result, actor, new Object[] { arg1, arg2 });
  }

  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor,
                    Object arg1,
                    Object arg2,
                    Object arg3)
  {
    query(headers, result, actor, new Object[] { arg1, arg2, arg3 });
  }

  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor,
                    Object []args)
  {
    result.fail(new ServiceExceptionMethodNotFound(
                                L.l("'{0}' is an undefined method for {1}",
                                    this, actor)));
  }

  @Override
  public <T> void stream(HeadersAmp headers,
                         ResultStream<T> result,
                         StubAmp actor,
                         Object []args)
  {
    result.fail(new ServiceExceptionMethodNotFound(
                              L.l("'{0}' is an undefined method for {1}",
                                  this, actor)));
  }

  @Override
  public <T> void outPipe(HeadersAmp headers,
                          ResultPipeOut<T> result,
                          StubAmp actor,
                          Object []args)
  {
    result.fail(new ServiceExceptionMethodNotFound(
                              L.l("'{0}' is an undefined method for {1}",
                                  this, actor)));
  }

  @Override
  public <T> void inPipe(HeadersAmp headers,
                          ResultPipeIn<T> result,
                          StubAmp actor,
                          Object []args)
  {
    result.fail(new ServiceExceptionMethodNotFound(
                              L.l("'{0}' is an undefined method for {1}",
                                  this, actor)));
  }
  
  /*
  @Override
  public ActorAmp getActorInvoke(ActorAmp actorDeliver)
  {
    return actorDeliver;
  }
  */

  /*
  @Override
  public MethodAmp toTail()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */
  
  @Override
  public int hashCode()
  {
    return name().hashCode();
  }
  
  @Override
  public boolean equals(Object obj)
  {
    if (! (obj instanceof MethodAmp)) {
      return false;
    }
    else if (obj == this) {
      return true;
    }
    
    MethodAmp method = (MethodAmp) obj;
    
    if (! name().equals(method.name())) {
      return false;
    }
    
    System.out.println("ZOP: " + this + " " + method);
    
    return Arrays.equals(parameters(), method.parameters());
  }

  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + name()
            + "]");
  }
}
