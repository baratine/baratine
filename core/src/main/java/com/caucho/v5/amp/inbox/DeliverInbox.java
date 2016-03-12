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

import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.deliver.Deliver;
import com.caucho.v5.amp.deliver.Outbox;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

import io.baratine.service.ServiceExceptionConnect;

/**
 * Worker for an inbox
 */
class DeliverInbox implements Deliver<MessageAmp>
{
  private static final Logger log
    = Logger.getLogger(DeliverInbox.class.getName());
  
  private final InboxAmp _inbox;
  private final ActorAmp _actor;

  // private OutboxAmp _outbox;

  //private MessageInboxDeliver _messageContext;

  DeliverInbox(InboxAmp inbox, 
               ActorAmp actor)
  {
    _inbox = inbox;
    _actor = actor;
  }
  
  @Override
  public String getName()
  {
    //return _inbox.getDebugName();
    return _inbox.getAddress();
  }
  
  /*
  @Override
  public void initOutbox(Outbox<MessageAmp> outbox)
  {
    Objects.requireNonNull(outbox);
    
    super.initOutbox(outbox);
    
    _outbox = (OutboxAmp) outbox;
    
    _outbox.setInbox(_inbox);
    
    _messageContext = new MessageInboxDeliver(_inbox, _outbox);
    _outbox.setMessage(_messageContext);
  }
  */
  
  /*
  protected OutboxAmp getOutbox()
  {
    return _outbox;
  }
  */

  @Override
  public void deliver(final MessageAmp msg, Outbox outbox)
      throws Exception
  {
    //outbox.setMessage(msg);
    
    try {
      msg.invoke(_inbox, _actor);
    } catch (ServiceExceptionConnect e) {
      if (log.isLoggable(Level.FINEST)) {
        log.log(Level.FINEST, e.toString(), e);
      }
      else {
        log.fine(e.toString());
      }
    } catch (Throwable e) {
      if (log.isLoggable(Level.FINER)) {
        log.log(Level.FINER, e.toString(), e);
      }
      else {
        log.fine(e.toString());
      }
    }
  }

  @Override
  public void beforeBatch()
  {
    _actor.beforeBatch();
  }

  @Override
  public void afterBatch()
  {
    _actor.afterBatch();
  }

  @Override
  public void onInit()
  {
    //ServiceFuture<Boolean> future = new ServiceFuture<>();
    
    //_actor.onInit(future);
  }

  @Override
  public void onActive()
  {
    //_actor.onActive();
  }

  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    _actor.loadState().shutdown(_actor, mode);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _actor + "]";
  }
}
