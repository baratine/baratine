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

import java.util.Objects;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.LoadStateAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.StubAmp;

import io.baratine.stream.ResultStream;

/**
 * Handles the context for an actor, primarily including its
 * query map.
 */
public class StreamForkMessage<T>
  extends MethodMessageBase
  implements ResultStreamAmp<T>
{
  private final ResultStream<T> _result;

  private final Object[] _args;

  private InboxAmp _callerInbox;
  
  public StreamForkMessage(OutboxAmp outbox,
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
  }
  
  @Override
  public InboxAmp getInbox()
  {
    return getCallerInbox();
  }

  @Override
  public ResultStream<T> fork()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  private InboxAmp getCallerInbox()
  {
    return _callerInbox;
  }
  
  private ServicesAmp getManager()
  {
    return getCallerInbox().manager();
  }
  
  private ResultStream<T> getNext()
  {
    return _result;
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

      LoadStateAmp load = actorDeliver.load(actorMessage, this);
    
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
    getNext().start();
  }

  @Override
  public void accept(T value)
  {
    getNext().accept(value);
  }

  @Override
  public void ok()
  {
    getNext().ok();
  }

  @Override
  public void fail(Throwable exn)
  {
    getNext().fail(exn);
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
    return getNext().isCancelled();
  }
  
  @Override
  public ResultStream<?> createJoin()
  {
    return _result.createJoin();
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
}
