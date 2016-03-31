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

package com.caucho.v5.amp.remote;

import java.lang.reflect.AnnotatedType;
import java.util.Objects;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.QueryRefAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.MethodAmpBase;
import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.amp.stub.StubAmpBase;
import com.caucho.v5.bartender.pod.PodRef;
import com.caucho.v5.inject.type.AnnotatedTypeClass;

import io.baratine.service.Result;
import io.baratine.stream.ResultStream;

/**
 * Actor for a link to a remote Bartender server.
 */
public class StubLink extends StubAmpBase
{
  // private final String _addressRemote;
  //private final ActorAmpOut _actorOut;
  private final PodRef _podCaller;
  private ServiceRefAmp _parentRef;
  private ServicesAmp _ampManager;
  private String _addressRemote;
  
  private ServiceRefAmp _selfRef;
  
  public StubLink(ServicesAmp ampManager,
                   String addressRemote,
                   ServiceRefAmp parentRef,
                   PodRef podCaller,
                   StubAmpOut actorOut)
  {
    _ampManager = ampManager;
    
    _addressRemote = addressRemote;
    
    Objects.requireNonNull(ampManager);
    Objects.requireNonNull(parentRef);
    
    _parentRef = parentRef;
    _podCaller = podCaller;
  }
  
  void initSelfRef(ServiceRefAmp selfRef)
  {
    _selfRef = selfRef;
  }
  
  protected String getRemoteAddress()
  {
    return _addressRemote;
  }
  
  protected ServicesAmp getServiceManager()
  {
    return _ampManager;
  }
  
  public PodRef getPodCaller()
  {
    return _podCaller;
  }
  
  public ServiceRefAmp getParentRef()
  {
    return _parentRef;
  }
  
  public ServiceRefAmp getQueryMapRef()
  {
    return _parentRef;
  }
  
  /*
  protected ActorAmpOut getActorOut()
  {
    return _actorOut;
  }
  */
  
  @Override
  public boolean isUp()
  {
    return getParentRef().isUp();
  }

  @Override
  public AnnotatedType api()
  {
    return new AnnotatedTypeClass(getClass());
  }

  @Override
  public MethodAmp methodByName(String methodName)
  {
    return new LinkMethod(methodName);
  }
  
  @Override
  public Object onLookup(String path, ServiceRefAmp parentRef)
  {
    StubLink actorLink = new StubLink(getServiceManager(),
                                        getRemoteAddress() + path, parentRef, // getServiceRefCaller(),
                                        _podCaller, null); // _actorOut);
    
    ServiceRefAmp actorRef = parentRef.pin(actorLink);
    
    actorLink.initSelfRef(actorRef);;
    
    return actorRef;
  }

  @Override
  public void queryReply(HeadersAmp headers, 
                         StubAmp rampActor,
                         long id,
                         Object result)
  {
    /*
    ActorOutAmp actorOut = (ActorOutAmp) rampActor;
    
    getOut().reply(headers, actorLink.getRemoteAddress(), id, result);
    */
  }

  @Override
  public void queryError(HeadersAmp headers,
                         StubAmp rampActor,
                         long id,
                         Throwable exn)
  {
    /*
    try {
      ActorLink connActor = (ActorLink) rampActor;
    
      getOut().queryError(headers, connActor.getRemoteAddress(), id, exn);
    } catch (Exception e) {
      e.printStackTrace();
      exn.printStackTrace();
    }
    */
  }
  
  @Override
  public void onShutdown(ShutdownModeAmp mode)
  {
    super.onShutdown(mode);
   
    // getActorOut().onShutdown(mode);
  }

  protected QueryRefAmp createQueryChain(Result<?> result)
  {
    // long timeout = 0;
    
    ServiceRefAmp queryMapRef = getQueryMapRef();
    String address = queryMapRef.address();
    
    InboxAmp inbox = queryMapRef.inbox();
    ClassLoader loaderCaller = getLoaderCaller();
    
    QueryRefAmp queryRef = inbox.addQuery(address, result, loaderCaller);
    
    return queryRef;
  }

  protected QueryRefAmp createQueryChain(ResultStream<?> result,
                                         ServiceRefAmp targetRef)
  {
    // long timeout = 0;
    
    ServiceRefAmp queryMapRef = getQueryMapRef(); 
    String address = queryMapRef.address();
    
    ClassLoader loader = getLoaderCaller();
    
    QueryRefAmp queryRef
      = queryMapRef.inbox().addQuery(address, result, targetRef, loader);
    
    return queryRef;
  }
  
  protected ClassLoader getLoaderCaller()
  {
    if (_podCaller != null) {
      return _podCaller.getClassLoader();
    }
    
    ServiceRefAmp queryMapRef = getQueryMapRef();
    InboxAmp inbox = queryMapRef.inbox();
    
    ClassLoader loaderCaller = inbox.manager().classLoader();
    
    return loaderCaller;
  }

  @Override
  public String toString()
  {
    if (_podCaller != null) {
      return getClass().getSimpleName() + "[" + getRemoteAddress() + ",from=" + _podCaller + "]";
    }
    else {
      return getClass().getSimpleName() + "[" + getRemoteAddress() + "]";
    }
  }
  
  private class LinkMethod extends MethodAmpBase
  {
    private String _methodName;
    
    LinkMethod(String methodName)
    {
      _methodName = methodName;
    }
    
    @Override
    public String name()
    {
      return _methodName;
    }

    @Override
    public void send(HeadersAmp headers,
                     StubAmp actor,
                     Object []args)
    {
      // RampConnectionProxy connActor = (RampConnectionProxy) actor;
      StubAmpOut outActor = (StubAmpOut) actor;
      
      OutAmp conn = outActor.getOut();
      
      if (conn != null) {
        conn.send(headers, getRemoteAddress(), _methodName, getPodCaller(), args);
      }
    }

    @Override
    public void query(HeadersAmp headers,
                      Result<?> result,
                      StubAmp actor,
                      Object []args)
    {
      //OutboxDeliver<MessageAmp> outboxDeliver = ContextOutbox.getCurrent();
      // OutboxAmp outbox = (OutboxAmp) outboxDeliver;

      QueryRefAmp queryRef = createQueryChain(result);
      
      try {
        // XXX: wrong name here
        String from = queryRef.getFrom();
        long qid = queryRef.getId();
        
        //RampConnectionProxy connActor = (RampConnectionProxy) actor;
        // ActorLink connActor = (ActorLink) actor;
        StubAmpOut outActor = (StubAmpOut) actor;

        OutAmp conn = outActor.getOut();

        PodRef podCaller = getPodCaller();

        if (conn != null) {
          conn.query(headers, from, qid, 
                     getRemoteAddress(), _methodName, podCaller,
                     args);
        }
        else {
          queryRef.fail(headers, new IllegalStateException("null conn"));
        }
      } catch (Throwable e) {
        queryRef.fail(headers, e);
      }
    }

    @Override
    public <T> void stream(HeadersAmp headers,
                           ResultStream<T> result,
                           StubAmp actor,
                           Object []args)
    {
      //System.out.println("  S-REM: " + getRemoteAddress() + " " + this + " " + ActorAmpOutClientProxy.this);
      //OutboxDeliver<MessageAmp> outboxDeliver = ContextOutbox.getCurrent();
      //OutboxAmp outbox = (OutboxAmp) outboxDeliver;
      
      ResultStream resultProxy = ResultStreamRemoteProxy.STREAM;
      
      ResultStream<?> resultLocal = result.createJoin();
      ResultStream<T> resultServer = result.createFork(resultProxy);

      QueryRefAmp queryRef = createQueryChain(resultLocal, _selfRef);

      String from = queryRef.getFrom();
      long qid = queryRef.getId();
      
      //RampConnectionProxy connActor = (RampConnectionProxy) actor;
      StubAmpOut outActor = (StubAmpOut) actor;

      OutAmp conn = outActor.getOut();
      
      PodRef podCaller = getPodCaller();
      
      if (conn != null) {
        conn.stream(headers, from, qid, 
                     getRemoteAddress(), _methodName, podCaller,
                     resultServer, args);
      }
      else {
        result.fail(new IllegalStateException("null conn"));
      }
    }
  }
  
  private static class ResultStreamRemoteProxy implements ResultStream {
    private static final ResultStreamRemoteProxy STREAM = new ResultStreamRemoteProxy();

    @Override
    public void handle(Object o, Throwable exn, boolean isEnd)
    {
      System.out.println("handle: " + o + " " + exn + " " + isEnd);
    };
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[]";
    }
  }
}
