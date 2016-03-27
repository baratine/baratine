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

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.service.ServiceRefLocal;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.amp.stub.StubAmpNull;

import io.baratine.service.ResultFuture;

/**
 * Inbox that spawns threads to deliver messages.
 */
public class InboxExecutorBase extends InboxBase
{
  private final String _path;
  private final Executor _executor;
  private final StubAmpNull _stub;
  private final ServiceRefAmp  _serviceRef;
  
  public InboxExecutorBase(String path,
                           Executor executor)
  {
    super(null);
    
    _path = path;
    
    Objects.requireNonNull(executor);
    
    _executor = executor;
    _stub = new StubAmpNull(path);
    _serviceRef = new ServiceRefLocal(_stub, this);
  }
  
  @Override
  public String getAddress()
  {
    return _path;
  }
  
  @Override
  public ServiceRefAmp serviceRef()
  {
    return _serviceRef;
  }

  @Override
  public StubAmp stubDirect()
  {
    return _stub;
  }

  @Override
  public boolean offer(final MessageAmp message, long timeout)
  {
    _executor.execute(()->runMessage(message));
    
    return true;
  }
  
  public boolean isEmpty()
  {
    return true;
  }
  
  @Override
  public boolean isClosed()
  {
    return false;
  }

  @Override
  public MessageAmp getMessage()
  {
    // TODO Auto-generated method stub
    return null;
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
    
    _stub.onInit(future);
  }
  
  @Override
  public void shutdownStubs(ShutdownModeAmp mode)
  {
    _stub.onShutdown(mode);
  }
  
  /*
  @Override
  public MessageAmp runAs(OutboxDeliver<MessageAmp> outbox, MessageAmp tailMsg)
                               
  {
    tailMsg.offerQueue(0);
    outbox.flushAndExecuteLast();
    wake();
    
    return null;
  }
  */
  
  private void runMessage(MessageAmp msg)
  {
    try (OutboxAmp outbox = OutboxAmpFactory.newFactory().get()) {
      outbox.inbox(msg.inboxTarget());
      outbox.message(msg);
      
      //RampActor systemActor = null;
      StubAmp systemActor = _stub;
      
      msg.invoke(InboxExecutorBase.this, systemActor);
      
      while (! outbox.flushAndExecuteLast()) {
      }
    }
  }

  public String toString()
  {
    return getClass().getSimpleName() + "[" + serviceRef() + "]";
  }
  
  /*
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
        ActorAmp systemActor = _actor;
        
        _msg.invoke(InboxExecutorBase.this, systemActor);
        
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
  */

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
