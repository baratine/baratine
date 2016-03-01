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

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.actor.ServiceRefImpl;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

/**
 * Mailbox for an actor
 */
public class InboxDirect extends InboxBase
{
  private final ServiceRefAmp _actorRef;
  private final ActorAmp _actor;
  
  public InboxDirect(ServiceManagerAmp manager,
                       ActorAmp actor)
  {
    super(manager);
    
    _actor = actor;
    _actorRef = new ServiceRefImpl(String.valueOf(actor), actor, this);
  }
  
  @Override
  public ServiceRefAmp serviceRef()
  {
    return _actorRef;
  }

  @Override
  public boolean offerResult(final MessageAmp message)
  {
    return offer(message, InboxAmp.TIMEOUT_INFINITY);
  }

  @Override
  public ActorAmp getDirectActor()
  {
    return _actor;
  }
  
  @Override
  public boolean offer(final MessageAmp message, long timeout)
  {
    message.invoke(this, _actor);
      
    return true;
  }
  
  @Override
  public void onInit()
  {
    ResultFuture<Boolean> future = new ResultFuture<>();
    
    _actor.onInit(future);
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
  public void shutdownActors(ShutdownModeAmp mode)
  {
    _actor.onShutdown(mode);
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _actorRef + "]";
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
    // TODO Auto-generated method stub
    return null;
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
