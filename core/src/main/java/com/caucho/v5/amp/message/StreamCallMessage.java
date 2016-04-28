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

package com.caucho.v5.amp.message;

import io.baratine.service.Services;
import io.baratine.service.ServiceRef;
import io.baratine.stream.ResultStream;

import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.StubStateAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.StubAmp;

/**
 * Handles the context for an actor, primarily including its
 * query map.
 */
public class StreamCallMessage<T>
  extends MethodMessageBase
  implements ResultStreamAmp<T>
{
  private static final Logger log 
    = Logger.getLogger(StreamCallMessage.class.getName());
  
  private final ResultStream<T> _result;

  private Object[] _args;

  //private ResultStreamProxyClient _resultJoinLocal;
  private StreamCallJoinStub _join;

  private ResultStream<T> _fork;

  //private StreamCallMessage<T>.ResultStreamProxyService _resultJoinProxy;
  //private StreamCallTargetProxy _forkProxy;
  
  private StreamResultMessage<Object> _resultMessage;

  private InboxAmp _callerInbox;
  
  public StreamCallMessage(OutboxAmp outbox,
                           InboxAmp callerInbox,
                           HeadersAmp headers,
                           ServiceRefAmp serviceRef,
                           MethodAmp method,
                           ResultStream<T> result,
                           long expires,
                           Object []args)
  {
    super(outbox, headers, serviceRef, method);
    
    Objects.requireNonNull(result);
    
    _result = result;
    _args = args;
    
    Objects.requireNonNull(callerInbox);
    
    _callerInbox = callerInbox;
    
    ///System.err.println("STRCALL:" + serviceRef + " " + serviceRef.getInbox());
    
    ResultStream<Object> resultJoin = (ResultStream<Object>) result.createJoin();

    _join = new StreamCallJoinStub(resultJoin);
    
    resultJoin.start();
    
    //_resultJoinProxy = new ResultStreamProxyService();
    /*
    _join.fork();
    _forkProxy  = new StreamCallTargetProxy(_join, callerInbox);
    _fork = result.createFork(_forkProxy);
    */
    _fork = fork();
  }
  
  @Override
  public InboxAmp getInbox()
  {
    return getCallerInbox();
  }
  
  @Override
  public ResultStream<T> fork()
  {
    _join.fork();
    
    StreamCallTargetProxy forkProxy = new StreamCallTargetProxy(_join, _callerInbox);
    
    return _result.createFork(forkProxy);
  }
  
  public void forkComplete()
  {
    _join.ok();
  }
  
  private InboxAmp getCallerInbox()
  {
    return _callerInbox;
  }
  
  /*
  private ServiceManagerAmp getManager()
  {
    return getCallerInbox().getManager();
  }
  */
  
  /*
  private StreamResultMessage<Object> createResultMessage(OutboxAmp outbox)
  {
    // OutboxAmp outbox = getServiceRef().getManager().getCurrentOutbox();

    return new StreamResultMessage<>(outbox, getCallerInbox(), _resultJoinLocal);
  }
  */
  
  private ResultStream<T> next()
  {
    return _fork;
  }
  
  @Override
  public boolean isFuture()
  {
    ResultStream<T> result = _result;
    
    return result != null && result.isFuture();
  }

  @Override
  public final void invoke(InboxAmp inbox, StubAmp actorDeliver)
  {
    try {
      MethodAmp method = method();
    
      StubAmp actorMessage = serviceRef().stub();

      StubStateAmp load = actorDeliver.load(actorMessage, this);
      
      load.stream(actorDeliver, actorMessage,
                  method,
                  getHeaders(),
                  this,
                  _args);
      
    } catch (Throwable e) {
      fail(e);
    }
  }

  /*
  @Override
  public void complete(T value)
  {
    accept(value);
    complete();
  }
  */
  
  @Override
  public void start()
  {
    next().start();
  }

  @Override
  public void accept(T value)
  {
    next().accept(value);
  }

  @Override
  public void ok()
  {
    next().ok();
  }

  @Override
  public void fail(Throwable exn)
  {
    next().fail(exn);
  }
  
  @Override
  public void handle(T value, Throwable exn, boolean ok)
  {
    if (ok) {
      ok();
    }
    else if (exn != null) {
      fail(exn);
    }
    else {
      accept(value);
    }
  }
  
  @Override
  public boolean isCancelled()
  {
    return next().isCancelled();
  }
  
  @Override
  public ResultStream<?> createJoin()
  {
    return _fork.createJoin();
  }
  
  @Override
  public ResultStream<T> createFork(ResultStream<Object> resultJoin)
  {
    return _result.createFork(resultJoin);
  }
  
  @Override
  public void offerQueue(long timeout)
  {
    try {
      super.offerQueue(timeout);
    } catch (Throwable e) {
      fail(e);
    }
  }

  @Override
  public String toString()
  {
    String toAddress = null;
    
    if (inboxTarget() != null && inboxTarget().serviceRef() != null) {
      toAddress = inboxTarget().serviceRef().address();
    }
    
    String callbackName = null;
    
    if (_result != null) {
      callbackName = _result.getClass().getName();
    }
    
    return (getClass().getSimpleName()
        + "[" + method().name()
        + ",to=" + toAddress
        + ",result=" + callbackName
        + "]");
    
  }
  
  /*
  @SuppressWarnings("serial")
  private class ResultStreamProxyService implements ResultStream<Object> {
    private long _sequenceAccept;
    
    @Override
    public void start()
    {
      _resultJoinLocal.start();
    }
    
    @Override
    public void accept(Object value)
    {
      StreamResultMessage<Object> resultMessage = _resultMessage;
      
      if (resultMessage == null || ! resultMessage.add(value)) {
        _sequenceAccept++;
        
        try (OutboxAmp outbox = OutboxAmp.currentOrCreate(getManager())) {
          _resultMessage = createResultMessage(outbox);
          _resultMessage.add(value);
          _resultMessage.offer(0);
        }
      }
    }
    
    @Override
    public void complete()
    {
      StreamResultMessage<Object> resultMessage = _resultMessage;
      _resultMessage = null;
      
      if (resultMessage == null || ! resultMessage.complete()) {
        try (OutboxAmp outbox = OutboxAmp.currentOrCreate(getManager())) {
          resultMessage = createResultMessage(outbox);
          resultMessage.complete();
          resultMessage.offer(0);
        }
      }
      
      _resultMessage = null;
    }
    
    @Override
    public void fail(Throwable exn)
    {
      StreamResultMessage<Object> resultMessage = _resultMessage;
      _resultMessage = null;
      
      if (resultMessage == null || ! resultMessage.failQueue(exn)) {
        try (OutboxAmp outbox = OutboxAmp.currentOrCreate(getManager())) {
          resultMessage = createResultMessage(outbox);
          resultMessage.failQueue(exn);
          resultMessage.offer(0);
        }
      }
      
      _resultMessage = null;
    }
    
    @Override
    public void flush()
    {
      _resultMessage = null;
    }
    
    @Override
    public boolean isCancelled()
    {
      System.out.println("ISCAN: " + _resultJoinLocal.isCancelled() + " " + _resultJoinLocal);
      return _resultJoinLocal.isCancelled();
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + getCallerInbox() + "," + _resultJoinLocal + "]";
    }
  }
*/
}
