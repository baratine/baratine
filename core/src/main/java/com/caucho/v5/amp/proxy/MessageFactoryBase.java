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

package com.caucho.v5.amp.proxy;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.message.HeadersNull;
import com.caucho.v5.amp.message.PipeInMessage;
import com.caucho.v5.amp.message.PipeOutMessage;
import com.caucho.v5.amp.message.QueryWithResultMessage_0;
import com.caucho.v5.amp.message.QueryWithResultMessage_1;
import com.caucho.v5.amp.message.QueryWithResultMessage_N;
import com.caucho.v5.amp.message.SendMessage_0;
import com.caucho.v5.amp.message.SendMessage_1;
import com.caucho.v5.amp.message.SendMessage_N;
import com.caucho.v5.amp.message.StreamCallMessage;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.MethodAmp;
import com.caucho.v5.amp.spi.OutboxAmp;

import io.baratine.io.ResultPipeIn;
import io.baratine.io.ResultPipeOut;
import io.baratine.service.Result;
import io.baratine.service.ResultFuture;
import io.baratine.service.ResultStream;

/**
 * Factory for proxy message
 */
public final class MessageFactoryBase implements MessageFactoryAmp
{
  private static final Logger log
    = Logger.getLogger(MessageFactoryBase.class.getName());
  
  private static final long TIMEOUT = 60 * 1000L;
  
  private ServiceManagerAmp _manager;
  
  private long _queryTimeoutMin = TIMEOUT;
  
  MessageFactoryBase(ServiceManagerAmp manager)
  {
    Objects.requireNonNull(manager);
    
    _manager = manager;
  }
  
  @Override
  public void send(ServiceRefAmp serviceRef,
                   MethodAmp method)
  {
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_manager)) {
      HeadersAmp headers = HeadersNull.NULL;
    
      SendMessage_0 msg
        = new SendMessage_0(outbox, headers, serviceRef, method);

      msg.offer(TIMEOUT);
    }
  }
  
  @Override
  public void send(ServiceRefAmp serviceRef,
                   MethodAmp method,
                   Object arg1)
  {
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_manager)) {
      HeadersAmp headers = HeadersNull.NULL;
    
      SendMessage_1 msg
        = new SendMessage_1(outbox, headers, serviceRef, method, arg1);
    
      msg.offer(TIMEOUT);
    }
  }
  
  @Override
  public void send(ServiceRefAmp serviceRef,
                   MethodAmp method,
                   Object []args)
  {
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_manager)) {
      HeadersAmp headers = HeadersNull.NULL;
    
      SendMessage_N msg
        = new SendMessage_N(outbox, headers, serviceRef, method, args);
    
      msg.offer(TIMEOUT);
    }
  }
  
  @Override
  public <V> void queryResult(Result<V> result, 
                              long timeout,
                              ServiceRefAmp serviceRef,
                              MethodAmp method)
  {
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_manager)) {
      HeadersAmp headers = HeadersNull.NULL;
      QueryWithResultMessage_0<V> msg
        = new QueryWithResultMessage_0<>(outbox,
                                         headers,
                                         result, 
                                         timeout, 
                                         serviceRef, 
                                         method);
    
      msg.offer(timeout);
      
      // outbox.flush();
    }
  }

  @Override
  public <V> void queryResult(Result<V> result, 
                              long timeout,
                              ServiceRefAmp serviceRef, 
                              MethodAmp method,
                              Object arg1)
  {
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_manager)) {
      HeadersAmp headers = HeadersNull.NULL;

      QueryWithResultMessage_1<V> msg
        = new QueryWithResultMessage_1<>(outbox,
                                       headers,
                                       result, 
                                       timeout, 
                                       serviceRef,
                                       method,
                                       arg1);

      msg.offer(timeout);
    }
  }

  @Override
  public <V> void queryResult(Result<V> result,
                              long timeout,
                              ServiceRefAmp serviceRef,
                              MethodAmp method,
                              Object[] args)
  {
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_manager)) {
      HeadersAmp headers = HeadersNull.NULL;
    
      QueryWithResultMessage_N<V> msg
      = new QueryWithResultMessage_N<>(outbox,
                                       headers,
                                       result, 
                                       timeout, 
                                       serviceRef,
                                       method,
                                       args);

      msg.offer(timeout);
    }
  }

  @Override
  public <V> V queryFuture(long timeout,
                           ServiceRefAmp serviceRef,
                           MethodAmp method,
                           Object[] args)
  {
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_manager)) {
      HeadersAmp headers = HeadersNull.NULL;
    
      ResultFuture<V> future = new ResultFuture<>();
    
      timeout = Math.min(timeout, _queryTimeoutMin);

      QueryWithResultMessage_N<V> msg;
      
      msg = new QueryWithResultMessage_N<>(outbox,
          headers,
          future, 
          timeout, 
          serviceRef,
          method,
          args);

      msg.offer(timeout);
      
      outbox.flush();
      
      return future.get(timeout, TimeUnit.MILLISECONDS);
    }
  }

  @Override
  public <V> V queryFuture(ServiceRefAmp serviceRef,
                           MethodAmp method,
                           Object[] args)
  {
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_manager)) {
      HeadersAmp headers = HeadersNull.NULL;
    
      ResultFuture<V> future = new ResultFuture<>();

      long timeout = _queryTimeoutMin;

      QueryWithResultMessage_N<V> msg;
      
      msg = new QueryWithResultMessage_N<>(outbox,
          headers,
          future, 
          timeout, 
          serviceRef,
          method,
          args);

      msg.offer(timeout);
      
      return future.get(_queryTimeoutMin, TimeUnit.MILLISECONDS);
    }
  }
  
  @Override
  public <V> void streamResult(ResultStream<V> result, 
                              long timeout,
                              ServiceRefAmp serviceRef,
                              MethodAmp method,
                              Object []args)
  {
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_manager)) {
      HeadersAmp headers = HeadersNull.NULL;
    
      StreamCallMessage<V> msg
      = new StreamCallMessage<V>(outbox,
                            outbox.inbox(),
                            headers,
                            serviceRef, 
                            method,
                            result, 
                            timeout, 
                            args);
    
      msg.offer(timeout);
    }
  }
  
  @Override
  public <V> void resultOutPipe(ResultPipeOut<V> result, 
                                long timeout,
                                ServiceRefAmp serviceRef,
                                MethodAmp method,
                                Object []args)
  {
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_manager)) {
      HeadersAmp headers = HeadersNull.NULL;
    
      PipeOutMessage<V> msg
        = new PipeOutMessage<V>(outbox,
                            outbox.inbox(),
                            headers,
                            serviceRef, 
                            method,
                            result, 
                            timeout, 
                            args);
    
      msg.offer(timeout);
    }
  }
  
  @Override
  public <V> void resultInPipe(ResultPipeIn<V> result, 
                                long timeout,
                                ServiceRefAmp serviceRef,
                                MethodAmp method,
                                Object []args)
  {
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_manager)) {
      HeadersAmp headers = HeadersNull.NULL;
    
      PipeInMessage<V> msg
        = new PipeInMessage<V>(outbox,
                            outbox.inbox(),
                            headers,
                            serviceRef, 
                            method,
                            result, 
                            timeout, 
                            args);
    
      msg.offer(timeout);
    }
  }
  
  /*
  private OutboxAmp getOutbox()
  {
    OutboxAmp outbox = OutboxAmp.current();
    
    // Outbox<MessageAmp> outboxDeliver = OutboxThreadLocal.getCurrent();
    
    if (outbox != null) { //  && outbox.getInbox() != null) {
      // OutboxAmp outbox = (OutboxAmp) outboxDeliver;
    
      return outbox;
    }
    else {
      return _manager.getSystemOutbox(); 
    }
  }
  */
  
  /*
  private OutboxAmp getCurrentOutbox()
  {
    Outbox<MessageAmp> outboxDeliver = OutboxThreadLocal.getCurrent();
    
    if (outboxDeliver instanceof OutboxAmp) {
      OutboxAmp outbox = (OutboxAmp) outboxDeliver;
    
      return outbox;
    }
    else {
      return null;
    }
  }
  */
}
