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
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.manager.TraceAmp;
import com.caucho.v5.amp.spi.HeadersAmp;

import io.baratine.pipe.PipeSub;
import io.baratine.pipe.PipePub;
import io.baratine.service.ResultChain;
import io.baratine.stream.ResultStream;


/**
 * send a copy of a message to a debug tee.
 */
public class MethodAmpTrace implements MethodAmp
{
  private static final Logger log
    = Logger.getLogger(MethodAmpTrace.class.getName());

  private final MethodAmp _delegate;
  
  public MethodAmpTrace(MethodAmp delegate)
  {
    Objects.requireNonNull(delegate);
    
    _delegate = delegate;
  }
  
  private MethodAmp delegate()
  {
    return _delegate;
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
  public Class<?> declaringClass()
  {
    return delegate().declaringClass();
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
    String traceId = getTraceId(headers);
    
    if (traceId != null) {
      traceSend(traceId, headers, actor, new Object[0]);
      
      TraceAmp.deliverBreakpoint(traceId, actor.name(), name());
    }
    
    delegate().send(headers, actor);
  }

  @Override
  public void send(HeadersAmp headers,
                    StubAmp actor,
                    Object arg1)
  {
    String traceId = getTraceId(headers);
    
    if (traceId != null) {
      traceSend(traceId, headers, actor, new Object[] { arg1 });
      
      TraceAmp.deliverBreakpoint(traceId, actor.name(), name());
    }
    
    delegate().send(headers, actor, arg1);
  }

  @Override
  public void send(HeadersAmp headers,
                    StubAmp actor,
                    Object arg1, 
                    Object arg2)
  {
    String traceId = getTraceId(headers);
    
    if (traceId != null) {
      traceSend(traceId, headers, actor, new Object[] { arg1, arg2 });
      
      TraceAmp.deliverBreakpoint(traceId, actor.name(), name());
    }
    
    delegate().send(headers, actor, arg1, arg2);
  }

  @Override
  public void send(HeadersAmp headers,
                   StubAmp actor,
                   Object arg1,
                   Object arg2, 
                   Object arg3)
  {
    String traceId = getTraceId(headers);
    
    if (traceId != null) {
      traceSend(traceId, headers, actor, new Object[] { arg1, arg2, arg3 });
      
      TraceAmp.deliverBreakpoint(traceId, actor.name(), name());
    }
    
    delegate().send(headers, actor, arg1, arg2, arg3);
  }

  @Override
  public void send(HeadersAmp headers,
                   StubAmp actor,
                   Object []args)
  {
    String traceId = getTraceId(headers);
    
    if (traceId != null) {
      traceSend(traceId, headers, actor, args);
      
      TraceAmp.deliverBreakpoint(traceId, actor.name(), name());
    }
    
    delegate().send(headers, actor, args);
  }
  
  private void traceSend(String traceId,
                         HeadersAmp headers, 
                         StubAmp actor,
                         Object []args)
  {
    if (traceId == null) {
      return;
    }
    
    if (log.isLoggable(Level.FINE)) {
      log.fine("send {id=" + traceId + "} " + actor.name() + " " + name() + Arrays.asList(args));
    }
  }
  
  //
  // query methods
  //
  
  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor)
  {
    String traceId = getTraceId(headers);
    
    if (traceId != null) {
      traceQuery(traceId, headers, actor, new Object[] { });
      
      TraceAmp.deliverBreakpoint(traceId, actor.name(), name());
    }
    
    delegate().query(headers, result, actor);
  }
  
  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor,
                    Object arg1)
  {
    String traceId = getTraceId(headers);
    
    if (traceId != null) {
      traceQuery(traceId, headers, actor, new Object[] { });
      
      TraceAmp.deliverBreakpoint(traceId, actor.name(), name());
    }
    
    delegate().query(headers, result, actor, arg1);
  }
  
  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor,
                    Object arg1,
                    Object arg2)
  {
    String traceId = getTraceId(headers);
    
    if (traceId != null) {
      traceQuery(traceId, headers, actor, new Object[] { });
      
      TraceAmp.deliverBreakpoint(traceId, actor.name(), name());
    }
    
    delegate().query(headers, result, actor, arg1, arg2);
  }
  
  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor,
                    Object arg1,
                    Object arg2,
                    Object arg3)
  {
    String traceId = getTraceId(headers);
    
    if (traceId != null) {
      traceQuery(traceId, headers, actor, new Object[] { });
      
      TraceAmp.deliverBreakpoint(traceId, actor.name(), name());
    }
    
    delegate().query(headers, result, actor, arg1, arg2, arg3);
  }
  
  @Override
  public void query(HeadersAmp headers,
                    ResultChain<?> result,
                    StubAmp actor,
                    Object []args)
  {
    String traceId = getTraceId(headers);
    
    if (traceId != null) {
      traceQuery(traceId, headers, actor, new Object[] { });
      
      TraceAmp.deliverBreakpoint(traceId, actor.name(), name());
    }
    
    delegate().query(headers, result, actor, args);
  }
  
  @Override
  public Object shim(Object value)
  {
    return delegate().shim(value);
  }
  
  private void traceQuery(String traceId,
                          HeadersAmp headers, 
                          StubAmp actor,
                          Object []args)
  {
    if (traceId == null) {
      return;
    }
    
    if (log.isLoggable(Level.FINE)) {
      log.fine("query {id=" + traceId + "} " + actor.name() + " " + name() + Arrays.asList(args));
    }
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
    
    // getTee().stream(headers, queryRef, actor, collector, args);
  }
  */

  @Override
  public <T> void stream(HeadersAmp headers,
                         ResultStream<T> result,
                         StubAmp actor,
                         Object []args)
  {
    delegate().stream(headers, result, actor, args);
    
    // getTee().stream(headers, result, actor, args);
  }

  @Override
  public <T> void outPipe(HeadersAmp headers,
                          PipePub<T> result,
                          StubAmp actor,
                          Object []args)
  {
    delegate().outPipe(headers, result, actor, args);
  }

  @Override
  public <T> void inPipe(HeadersAmp headers,
                          PipeSub<T> result,
                          StubAmp actor,
                          Object []args)
  {
    delegate().inPipe(headers, result, actor, args);
  }
  
  private String getTraceId(HeadersAmp headers)
  {
    Object id = headers.get("trace.id");
    
    if (id instanceof String) {
      return (String) id;
    }
    else {
      return null;
    }
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
