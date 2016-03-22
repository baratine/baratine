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

import java.util.Objects;
import java.util.concurrent.Executor;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.deliver.WorkerDeliver;
import com.caucho.v5.amp.service.ServiceRefImpl;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.amp.stub.StubAmpNull;

import io.baratine.service.ResultFuture;

/**
 * Inbox that spawns threads to deliver messages.
 */
public class InboxExecutor extends InboxBase
  implements WorkerDeliver<MessageAmp>
{
  private final ServiceRefAmp _actorRef;
  private final StubAmp _actor;
  
  private final Executor _executor;
  
  public InboxExecutor(ServiceManagerAmp manager,
                       String path,
                       Executor executor)
  {
    super(manager);
    
    Objects.requireNonNull(executor);
    
    _actor = new StubAmpNull(path);
    _actorRef = new ServiceRefImpl(path, _actor, this);
    
    _executor = executor;
  }
  
  @Override
  public ServiceRefAmp serviceRef()
  {
    return _actorRef;
  }

  @Override
  public StubAmp getDirectActor()
  {
    return _actor;
  }

  @Override
  public boolean offer(final MessageAmp message, long timeout)
  {
    _executor.execute(new SpawnMessage(message));
    
    return true;
  }
  
  public boolean isEmpty()
  {
    return true;
  }
  
  @Override
  public WorkerDeliver<MessageAmp> worker()
  {
    return this;
  }

  /*
  @Override
  public boolean wake()
  {
    return true;
  }
  */
  
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
  public boolean wake()
  {
    return false;
  }
  
  @Override
  public void shutdownActors(ShutdownModeAmp mode)
  {
    _actor.onShutdown(mode);
  }
  
  /*
  @Override
  public boolean runAs(Outbox outbox, MessageAmp tailMsg)
  {
    tailMsg.offerQueue(0);
    outbox.flushAndExecuteLast();
    wake();
  }
  */

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
      try (OutboxAmp outbox = OutboxAmpFactory.newFactory().get()) {
        outbox.inbox(_msg.inboxTarget());
        outbox.message(_msg);
        
        //RampActor systemActor = null;
        StubAmp systemActor = _actor;
        
        _msg.invoke(InboxExecutor.this, systemActor);
        
        while (! outbox.flushAndExecuteLast()) {
        }
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
}
