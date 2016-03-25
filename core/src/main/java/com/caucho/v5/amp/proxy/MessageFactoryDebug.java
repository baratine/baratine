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

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.manager.ServiceManagerAmpImpl;
import com.caucho.v5.amp.message.DebugQueryMap;
import com.caucho.v5.amp.message.HeadersNull;
import com.caucho.v5.amp.message.PipeInMessage;
import com.caucho.v5.amp.message.PipeOutMessage;
import com.caucho.v5.amp.message.QueryMessageDebug_N;
import com.caucho.v5.amp.message.SendMessage_0;
import com.caucho.v5.amp.message.SendMessage_1;
import com.caucho.v5.amp.message.SendMessage_N;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.util.DebugUtil;

import io.baratine.pipe.ResultPipeIn;
import io.baratine.pipe.ResultPipeOut;
import io.baratine.service.Result;
import io.baratine.service.ResultFuture;
import io.baratine.stream.ResultStream;

/**
 * Factory for proxy message
 */
public final class MessageFactoryDebug implements MessageFactoryAmp
{
  private static final Logger log
    = Logger.getLogger(MessageFactoryDebug.class.getName());
  
  private static final long TIMEOUT = 10 * 1000L;
  
  private final ServiceManagerAmpImpl _manager;
  private final DebugQueryMap _debugQueryMap;
  
  private boolean _isFiner;

  MessageFactoryDebug(ServiceManagerAmpImpl ampManager)
  {
    _manager = ampManager;
    
    _debugQueryMap = _manager.getDebugQueryMap();
    
    _isFiner = log.isLoggable(Level.FINER);
  }
  
  @Override
  public void send(ServiceRefAmp serviceRef,
                   MethodAmp method)
  {
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_manager)) {
      HeadersAmp headers = createHeaders(outbox, serviceRef, method);
    
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
      HeadersAmp headers = createHeaders(outbox, serviceRef, method);

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
      HeadersAmp headers = createHeaders(outbox, serviceRef, method);
    
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
      Result<V> debugResult = _debugQueryMap.addQuery(result, serviceRef, method);
    
      HeadersAmp headers = createHeaders(outbox, serviceRef, method);
    
      String loc = DebugUtil.callerEntry(3);
    
      QueryMessageDebug_N<V> msg
        = new QueryMessageDebug_N<>(outbox,
                                    headers,
                                    debugResult, 
                                    timeout, 
                                    serviceRef, 
                                    method,
                                    new Object[0],
                                    loc);

      msg.offer(timeout);
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
      String loc = DebugUtil.callerEntry(3);
    
      QueryMessageDebug_N<V> msg
        = new QueryMessageDebug_N<>(outbox,
            createHeaders(outbox, serviceRef, method),
                                  result, 
                                  timeout, 
                                  serviceRef,
                                  method,
                                  new Object[] { arg1 }, 
                                  loc);

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
      String loc = DebugUtil.callerEntry(3);
    
      QueryMessageDebug_N<V> msg
        = new QueryMessageDebug_N<>(outbox,
                                  createHeaders(outbox, serviceRef, method),
                                  result, 
                                  timeout, 
                                  serviceRef,
                                  method,
                                  args, 
                                  loc);

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
      
      String loc = DebugUtil.callerEntry(3);
    
      ResultFuture<V> future = new ResultFuture<>();
    
      timeout = Math.min(timeout, 60000);

      QueryMessageDebug_N<V> msg;
    
      msg = new QueryMessageDebug_N<>(outbox,
                                           headers,
                                           future, 
                                           timeout, 
                                           serviceRef,
                                           method,
                                           args,
                                           loc);

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
      
      String loc = DebugUtil.callerEntry(3);
    
      ResultFuture<V> future = new ResultFuture<>();
    
      long timeout = 60000;

      QueryMessageDebug_N<V> msg;
    
      msg = new QueryMessageDebug_N<>(outbox,
                                           headers,
                                           future, 
                                           timeout, 
                                           serviceRef,
                                           method,
                                           args,
                                           loc);

      msg.offer(timeout);
      
      return future.get(timeout, TimeUnit.MILLISECONDS);
    }
  }
  
  /*
  private OutboxAmp getOutbox()
  {
    OutboxAmp outbox = OutboxAmp.current();
    
    if (outbox != null) {
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

  @Override
  public <V> void streamResult(ResultStream<V> result, long timeout,
                               ServiceRefAmp serviceRef, MethodAmp method,
                               Object[] args)
  {
    // TODO Auto-generated method stub
    
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

  protected HeadersAmp createHeaders(OutboxAmp outbox,
                                        ServiceRefAmp serviceRef,
                                        MethodAmp method)
  {
    MessageAmp msg = outbox.message();
    
    HeadersAmp headers;
    
    if (msg != null) {
      headers = msg.getHeaders();
    }
    else {
      headers = HeadersNull.NULL;
    }
    
    
    if (_isFiner || headers.getSize() > 0) {
      int size = headers.getSize();

      if (size > 100 && size < 120) {
        log.warning("Possible cycle: " + method+ " " + this + " " + headers);
      }

      int count = 2;

      int index = (size / count) + 1;
      headers = headers.add("service." + index, serviceRef.address());
      headers = headers.add("method." + index, method.name());
    }

    return headers;
  }
}
