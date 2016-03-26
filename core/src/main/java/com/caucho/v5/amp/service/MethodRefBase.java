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

package com.caucho.v5.amp.service;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.message.HeadersNull;
import com.caucho.v5.amp.message.QueryWithResultMessage_N;
import com.caucho.v5.amp.message.ResultStreamAmp;
import com.caucho.v5.amp.message.SendMessage_N;
import com.caucho.v5.amp.message.StreamCallMessage;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.ParameterAmp;
import com.caucho.v5.amp.stub.StubAmp;

import io.baratine.service.Result;
import io.baratine.spi.Headers;
import io.baratine.stream.ResultStream;

/**
 * Sender for an actor ref.
 */
abstract public class MethodRefBase implements MethodRefAmp, Serializable
{
  @Override
  public boolean isUp()
  {
    return true;
  }
  
  @Override
  public boolean isClosed()
  {
    return false;
  }
  
  @Override
  public void offer(MessageAmp msg)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public Type getReturnType()
  {
    return method().getReturnType();
  }

  @Override
  public MethodAmp method()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public ServiceRefAmp serviceRef()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public InboxAmp inbox()
  {
    return serviceRef().inbox();
  }
  
  @Override
  public StubAmp stubActive(StubAmp actorDeliver)
  {
    return actorDeliver;
  }
  
  public Annotation []getAnnotations()
  {
    return method().getAnnotations();
  }
  
  @Override
  public ParameterAmp []parameters()
  {
    return method().parameters();
  }
  
  @Override
  public boolean isVarArgs()
  {
    return method().isVarArgs();
  }

  @Override
  public void send(Headers headers, Object... args)
  {
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(serviceRef().manager())) {
      HeadersAmp headersAmp = (HeadersAmp) headers;
      MessageAmp msg = new SendMessage_N(outbox, headersAmp, serviceRef(), method(), args);
    
      long timeout = Integer.MAX_VALUE; // use queue default timeout
    
      msg.offer(timeout);
    }
  }

  @Override
  public void send(Object... args)
  {
    send(HeadersNull.NULL, args);
  }

  @Override
  public <T> void query(Headers headers,
                        Result<T> result,
                        Object... args)
  {
    long timeout = 15000;
    
    query(headers, result, timeout, TimeUnit.MILLISECONDS, args);
  }

  @Override
  public <T> void query(Result<T> result, Object... args)
  {
    query(HeadersNull.NULL, result, args);
  }

  @Override
  public <T> void query(Headers headers,
                        Result<T> result, 
                        long timeout, TimeUnit timeUnit,
                        Object... args)
  {
    QueryWithResultMessage_N<T> msg;
    
    if (timeUnit != null) {
      timeout = timeUnit.toMillis(timeout);
    }
    
    ServiceManagerAmp manager = serviceRef().manager();
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(manager)) {
      // OutboxAmp outbox = manager.getCurrentOutbox();
      // InboxAmp inbox = outbox.getInbox();
      
      msg = new QueryWithResultMessage_N<T>(outbox, result, timeout,
                                            serviceRef(), method(),
                                            args);
    
      msg.offer(timeout);
    } catch (Throwable e) {
      result.fail(e);
    }
  }

  @Override
  public <T> void stream(Headers headers,
                         ResultStream<T> result,
                         Object... args)
  {
    StreamCallMessage<T> msg;
    
    long timeout = -1;
    
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(serviceRef().manager())) {
      HeadersAmp headersAmp = HeadersNull.NULL;
      
      InboxAmp inboxCaller;
      
      if (result instanceof ResultStreamAmp) {
        inboxCaller = ((ResultStreamAmp<?>) result).getInbox();
      }
      else {
        inboxCaller = outbox.inbox();
      }
    
      msg = new StreamCallMessage<T>(outbox, inboxCaller, headersAmp,
                                     serviceRef(), method(), result,
                                     timeout, args);
    
      msg.offer(timeout);
    }
  }
  
  private Object writeReplace()
  {
    return new MethodRefHandle(serviceRef().address(), getName());
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
