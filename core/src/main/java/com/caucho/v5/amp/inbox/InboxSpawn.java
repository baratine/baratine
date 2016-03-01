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

package com.caucho.v5.amp.inbox;

import io.baratine.service.ResultFuture;

import java.util.concurrent.Executor;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.actor.ServiceRefImpl;
import com.caucho.v5.amp.queue.OutboxDeliver;
import com.caucho.v5.amp.queue.WorkerDeliver;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.thread.ThreadPool;

/**
 * Inbox that spawns threads to deliver messages.
 */
public class InboxSpawn extends InboxBase
  implements WorkerDeliver<MessageAmp>
{
  private final ServiceRefAmp _actorRef;
  private final ActorAmp _actor;
  
  private final Executor _executor = ThreadPool.current().getThrottleExecutor();
  
  public InboxSpawn(ServiceManagerAmp manager,
                      ActorAmp actor,
                      String path)
  {
    super(manager);
    
    _actor = actor;
    _actorRef = new ServiceRefImpl(path, actor, this);
  }
  
  @Override
  public ServiceRefAmp serviceRef()
  {
    return _actorRef;
  }

  @Override
  public ActorAmp getDirectActor()
  {
    return _actor;
  }

  @Override
  public boolean offer(final MessageAmp message, long timeout)
  {
    _executor.execute(new SpawnMessage(message));
    
    return true;
  }
  
  @Override
  public boolean isEmpty()
  {
    return true;
  }
  
  @Override
  public WorkerDeliver getWorker()
  {
    return this;
  }

  @Override
  public boolean wake()
  {
    return true;
  }
  
  @Override
  public boolean isClosed()
  {
    return false;
  }

  /**
   * Closes the mailbox
   */
  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    
  }
  
  @Override
  public void onInit()
  {
    ResultFuture<Boolean> future = new ResultFuture<>();
    
    _actor.onInit(future);
  }
  
  @Override
  public void shutdownActors(ShutdownModeAmp mode)
  {
    _actor.onShutdown(mode);
  }
  
  @Override
  public MessageAmp runAs(OutboxDeliver<MessageAmp> outbox,
                          MessageAmp tailMsg)
                               
  {
    tailMsg.offerQueue(0);
    outbox.flushAndExecuteLast();
    wake();
    
    return null;
  }

  public String toString()
  {
    return getClass().getSimpleName() + "[" + serviceRef() + "]";
  }
  
  private class SpawnMessage implements Runnable {
    private final MessageAmp _msg;
    
    SpawnMessage(MessageAmp msg)
    {
      _msg = msg;
    }
    
    @Override
    public void run()
    {
      // MessageAmp prev = ContextMessageAmp.getAndSet(_msg);
      InboxAmp oldInbox = null;
      
      try (OutboxAmp outbox = OutboxAmpFactory.newFactory().get()) {
        // OutboxAmpBase outbox = new OutboxAmpBase();

        Thread.dumpStack();
        outbox.inbox(_msg.getInboxTarget());
        outbox.setMessage(_msg);
        
        //RampActor systemActor = null;
        ActorAmp systemActor = _actor;
        
        _msg.invoke(InboxSpawn.this, systemActor);
        
        while (! outbox.flushAndExecuteLast()) {
        }
      } finally {
        // ContextMessageAmp.set(prev);
        // OutboxThreadLocal.setCurrent(oldOutbox);
      }
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _msg + "]";
    }
  }

  @Override
  public MessageAmp getMessage()
  {
    // TODO Auto-generated method stub
    return null;
  }

  /*
  @Override
  public InboxAmp getInbox()
  {
    return this;
  }

  @Override
  public void setInbox(InboxAmp inbox)
  {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void setMessage(MessageAmp message)
  {
    // TODO Auto-generated method stub
    
  }
  */
}
