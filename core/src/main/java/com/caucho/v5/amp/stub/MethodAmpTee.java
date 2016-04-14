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
import java.util.Objects;

import com.caucho.v5.amp.spi.HeadersAmp;

import io.baratine.pipe.ResultPipeIn;
import io.baratine.pipe.ResultPipeOut;
import io.baratine.service.ResultChain;
import io.baratine.stream.ResultStream;


/**
 * send a copy of a message to a debug tee.
 */
public class MethodAmpTee implements MethodAmp
{
  private final MethodAmp _delegate;
  private final MethodAmp _teeMethod;
  
  public MethodAmpTee(MethodAmp delegate,
                      MethodAmp teeMethod)
  {
    Objects.requireNonNull(delegate);
    Objects.requireNonNull(teeMethod);
    
    _delegate = delegate;
    _teeMethod = teeMethod;
  }
  
  private MethodAmp delegate()
  {
    return _delegate;
  }
  
  private MethodAmp tee()
  {
    return _teeMethod;
  }
  
  //
  // metadata methods
  //
  
  @Override
  public boolean isClosed()
  {
    return delegate().isClosed();
  }
  
  @Override
  public String name()
  {
    return delegate().name();
  }
  
  @Override
  public boolean isDirect()
  {
    return delegate().isDirect();
  }

  @Override
  public boolean isModify()
  {
    return delegate().isModify();
  }
  
  @Override
  public Annotation[] getAnnotations()
  {
    return delegate().getAnnotations();
  }
  
  @Override
  public Class<?> getReturnType()
  {
    return delegate().getReturnType();
  }
  
  @Override
  public ParameterAmp []parameters()
  {
    return delegate().parameters();
  }
  
  /*
  @Override
  public Class<?> []getParameterTypes()
  {
    return delegate().getParameterTypes();
  }
  
  @Override
  public Type []getGenericParameterTypes()
  {
    return delegate().getGenericParameterTypes();
  }
  
  @Override
  public Annotation [][]getParameterAnnotations()
  {
    return delegate().getParameterAnnotations();
  }
  */
  
  @Override
  public boolean isVarArgs()
  {
    return delegate().isVarArgs();
  }
  
  //
  // send methods
  //
  
  @Override
  public void send(HeadersAmp headers,
                    StubAmp actor)
  {     
    delegate().send(headers, actor);
    
    tee().send(headers, actor);
  }

  @Override
  public void send(HeadersAmp headers,
                    StubAmp actor,
                    Object arg1)
  {
    delegate().send(headers, actor, arg1);
    
    tee().send(headers, actor, arg1);
  }

  @Override
  public void send(HeadersAmp headers,
                    StubAmp actor,
                    Object arg1, 
                    Object arg2)
  {
    delegate().send(headers, actor, arg1, arg2);
    
    tee().send(headers, actor, arg1, arg2);
  }

  @Override
  public void send(HeadersAmp headers,
                   StubAmp actor,
                   Object arg1,
                   Object arg2, 
                   Object arg3)
  {
    delegate().send(headers, actor, arg1, arg2, arg3);
    
    tee().send(headers, actor, arg1, arg2, arg3);
  }

  @Override
  public void send(HeadersAmp headers,
                   StubAmp actor,
                   Object []args)
  {
    delegate().send(headers, actor, args);
    
    tee().send(headers, actor, args);
  }
  
  //
  // query methods
  //
  
  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor)
  {
    delegate().query(headers, result, actor);
    
    tee().query(headers, result, actor);
  }
  
  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor,
                    Object arg1)
  {
    delegate().query(headers, result, actor, arg1);
    
    tee().query(headers, result, actor, arg1);
  }
  
  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor,
                    Object arg1,
                    Object arg2)
  {
    delegate().query(headers, result, actor, arg1, arg2);
    
    tee().query(headers, result, actor, arg1, arg2);
  }
  
  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor,
                    Object arg1,
                    Object arg2,
                    Object arg3)
  {
    delegate().query(headers, result, actor, arg1, arg2, arg3);
    
    tee().query(headers, result, actor, arg1, arg2, arg3);
  }
  
  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor,
                    Object []args)
  {
    delegate().query(headers, result, actor, args);
    
    tee().query(headers, result, actor, args);
  }
  
  //
  // map-reduce methods
  //

  /*
  @Override
  public <T,R> void stream(HeadersAmp headers,
                           QueryRefAmp queryRef,
                           ActorAmp actor,
                           CollectorAmp<T,R> collector,
                           Object []args)
  {
    getDelegate().stream(headers, queryRef, actor, collector, args);
    
    getTee().stream(headers, queryRef, actor, collector, args);
  }
  */

  @Override
  public <T> void stream(HeadersAmp headers,
                         ResultStream<T> result,
                         StubAmp actor,
                         Object []args)
  {
    delegate().stream(headers, result, actor, args);
    
    tee().stream(headers, result, actor, args);
  }

  @Override
  public <T> void outPipe(HeadersAmp headers,
                          ResultPipeOut<T> result,
                          StubAmp actor,
                          Object []args)
  {
    delegate().outPipe(headers, result, actor, args);
    
    tee().outPipe(headers, result, actor, args);
  }

  @Override
  public <T> void inPipe(HeadersAmp headers,
                          ResultPipeIn<T> result,
                          StubAmp actor,
                          Object []args)
  {
    delegate().inPipe(headers, result, actor, args);
    
    tee().inPipe(headers, result, actor, args);
  }
  
  //
  // impl methods
  //

  /**
   * Returns the invocation actor.
   * 
   * For a child, the invocation is stored in the method.
   */
  /*
  @Override
  public ActorAmp getActorInvoke(ActorAmp actorDeliver)
  {
    return getDelegate().getActorInvoke(actorDeliver);
  }
  */
}
