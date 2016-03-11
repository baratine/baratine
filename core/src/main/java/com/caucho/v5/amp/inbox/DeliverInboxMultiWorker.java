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

import com.caucho.v5.amp.actor.ActorAmpMultiWorker;
import com.caucho.v5.amp.outbox.DeliverOutbox;
import com.caucho.v5.amp.outbox.Outbox;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

import io.baratine.service.Result;
import io.baratine.service.ResultFuture;
import io.baratine.service.ServiceException;

/**
 * Worker for an inbox
 */
class DeliverInboxMultiWorker implements DeliverOutbox<MessageAmp>
{
  private static final Logger log
    = Logger.getLogger(DeliverInboxMultiWorker.class.getName());
  
  private final InboxQueue _inbox;
  private final ActorAmp _actorDelegate;
  private final ActorAmp _actor;

  // private OutboxAmp _outbox;

  private final DeliverInboxState _stateShared;
  private DeliverInboxState _stateSelf;
  //private MessageInboxDeliver _messageContext;

  DeliverInboxMultiWorker(InboxQueue inbox, 
                          ActorAmp actor,
                          DeliverInboxState state)
  {
    _inbox = inbox;
    _actorDelegate = actor;
    _actor = new ActorAmpMultiWorker(_actorDelegate);
    _stateShared = state;
    
    _stateSelf = new DeliverInboxState();
  }
  
  @Override
  public String getName()
  {
    return _inbox.getDebugName();
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

  @Override
  public final void deliver(final MessageAmp msg, 
                            Outbox outbox)
      throws Exception
  {
    final HeadersAmp headers = msg.getHeaders();
    
    if (headers != null && headers.getSize() > 0) {
      OutboxAmp outboxAmp = (OutboxAmp) outbox;
      
      outboxAmp.message(msg);

      try {
        msg.invoke(_inbox, _actor);
      } catch (ServiceException e) {
        log.fine(e.toString());
      } catch (Throwable e) {
        log.log(Level.WARNING, this + " " + e.toString(), e);
      } finally {
        outboxAmp.message(null);
      }
    }
    else {
      try {
        msg.invoke(_inbox, _actor);
      } catch (ServiceException e) {
        log.fine(e.toString());
      } catch (Throwable e) {
        log.log(Level.WARNING, this + " " + e.toString(), e);
      }
    }
  }

  @Override
  public void beforeBatch()
  {
    if (_stateSelf.beforeBatch(_stateShared, this)) {
      _actor.beforeBatch();
    }
  }

  @Override
  public void afterBatch()
  {
    if (_stateSelf.afterBatch(_stateShared, this)) {
      _actor.afterBatch();
    }
  }

  @Override
  public void onInit()
  {
    _stateShared.toInit();
  }
  
  void onInitImpl()
  {
    ResultFuture<Boolean> future = new ResultFuture<>();
    
    _actor.onInit(future);
  }

  @Override
  public void onActive()
  {
    _stateShared.toActive();
    
    // _stateSelf.beforeBatch(_stateShared, this);
  }
  
  void onActiveImpl()
  {
    _actor.onActive(Result.ignore());
  }

  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    if (_stateShared.toShutdown(mode)) {
      _inbox.wakeAllAndWait();
    }

    _actor.loadState().shutdown(_actor, mode);
  }

  public void onShutdownImpl(ShutdownModeAmp mode)
  {
    _actor.loadState().shutdown(_actor, mode);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _actor + "]";
  }
}
