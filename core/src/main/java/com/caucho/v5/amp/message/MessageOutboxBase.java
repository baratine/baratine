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

package com.caucho.v5.amp.message;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.outbox.WorkerOutbox;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.OutboxAmp;

/**
 * Handle to an amp instance.
 */
abstract public class MessageOutboxBase implements MessageAmp
{
  private static final Logger log
    = Logger.getLogger(MessageOutboxBase.class.getName());
  
  private final OutboxAmp _outboxCaller;
  private final InboxAmp _inboxTarget;
  
  protected MessageOutboxBase(OutboxAmp outboxCaller,
                              InboxAmp inboxTarget)
  {
    Objects.requireNonNull(inboxTarget);
    
    _outboxCaller = outboxCaller;
    _inboxTarget = inboxTarget;
  }
  
  /*
  @Override
  public Type getType()
  {
    return Type.UNKNOWN;
  }
  */
  
  @Override
  public HeadersAmp getHeaders()
  {
    return HeadersNull.NULL;
  }
  
  @Override
  public OutboxAmp getOutboxCaller()
  {
    return _outboxCaller;
  }

  @Override
  public InboxAmp inboxTarget()
  {
    return _inboxTarget;
  }
  
  /*
  @Override
  public ActorAmp getActorInvoke(ActorAmp actorDeliver)
  {
    return actorDeliver;
  }
  */
  
  @Override
  public void offer(long timeout)
  {
    getOutboxCaller().offer(this);
  }

  @Override
  public void offerQueue(long timeout)
  {
    inboxTarget().offer(this, timeout);
  }

  @Override
  public WorkerOutbox<MessageAmp> worker()
  {
    return inboxTarget().worker();
  }

  @Override
  public void fail(Throwable exn)
  {
    log.log(Level.FINE, exn.toString(), exn);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + inboxTarget() + "]";
  }
}
