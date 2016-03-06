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

package com.caucho.v5.ramp.jamp;

import io.baratine.service.ServiceException;
import io.baratine.service.ServiceExceptionTimeout;
import io.baratine.spi.Message;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import com.caucho.v5.amp.message.HeadersNull;
import com.caucho.v5.amp.queue.Outbox;
import com.caucho.v5.amp.queue.WorkerDeliver;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;

/**
 * Message to unpark a thread.
 */
public class ResultJampRpc<T> implements MessageAmp
{
  private static final L10N L = new L10N(ResultJampRpc.class);
  
  private final QueueJampUnpark _queue;
  
  private volatile Thread _thread;
  private volatile boolean _isDone;
  private volatile T _value;
  private volatile Throwable _exception;
  
  ResultJampRpc(QueueJampUnpark queue)
  {
    Objects.requireNonNull(queue);
    
    _queue = queue;
  }
  
  public T get(long time, TimeUnit unit)
  {
    if (! _isDone) {
      long timeout = unit.toMillis(time);
      long expires = timeout + CurrentTime.getCurrentTimeActual();
      
      Thread thread = Thread.currentThread();
      
      _thread = thread;
      
      while (! _isDone && CurrentTime.getCurrentTimeActual() < expires) {
        try {
          Thread.interrupted();
          LockSupport.parkUntil(expires);
        } catch (Exception e) {
        }
      }
      
      _thread = null;
      
      if (! _isDone) {
        throw new ServiceExceptionTimeout(L.l("jamp-rpc timeout"));
      }
    }
    
    if (_exception != null) {
      throw ServiceException.createAndRethrow(_exception);
    }
    else {
      return _value;
    }
  }
  
  public void complete(T value)
  {
    _value = value;
    _isDone = true;

    unpark();
  }
  
  @Override
  public void fail(Throwable exn)
  {
    _exception = exn;
    _isDone = true;
    
    unpark();
  }
  
  private void unpark()
  {
    if (true) {
      unparkImpl();
      return;
    }
    
    //OutboxAmp outbox = getOutbox();
    OutboxAmp outbox = OutboxAmp.current(); // getOutbox();
    
    if (outbox != null) {
      outbox.offer(this);
      
      return;
    }
    
    System.err.println("No outbox available");

    unparkImpl();
  }
  
  void unparkImpl()
  {
    Thread thread = _thread;
    
    if (thread != null) {
      LockSupport.unpark(thread);
      
      _thread = null;
    }
  }
  
  /*
  private static OutboxAmp getOutbox()
  {
    OutboxAmp outbox<MessageAmp> outboxDeliver = OutboxThreadLocal.getCurrent();
    
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
  public void invoke(InboxAmp inbox, ActorAmp actor)
  {
    unparkImpl();
  }
  
  @Override
  public void offerQueue(long timeout)
  {
    offer(timeout);
  }

  @Override
  public void offer(long timeout)
  {
    _queue.offer(this);
  }

  @Override
  public WorkerDeliver getWorker()
  {
    return _queue;
  }

  @Override
  public Type getType()
  {
    return Message.Type.UNKNOWN;
  }

  /*
  @Override
  public ActorAmp getActorInvoke(ActorAmp actorDeliver)
  {
    return actorDeliver;
  }
  */

  @Override
  public InboxAmp inboxTarget()
  {
    System.out.println("IBT: " + this);
    
    return null;
  }

  /*
  @Override
  public InboxAmp getInboxContext()
  {
    return getInboxTarget();
  }
  */

  @Override
  public OutboxAmp getOutboxCaller()
  {
    System.out.println("OBC: " + this);

    return null;
  }

  @Override
  public HeadersAmp getHeaders()
  {
    return HeadersNull.NULL;
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
