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

import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.queue.WorkerDeliver;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.OutboxAmp;

/**
 * Message for a reply.
 */
public final class QueryReplyMessage implements MessageAmp
{
  private final static Logger log
    = Logger.getLogger(QueryReplyMessage.class.getName());
  
  private final ServiceRefAmp _serviceRef;
  private final HeadersAmp _headers;
  private final long _qid;
  private final Object _value;
  private final ActorAmp _from;

  private OutboxAmp _outboxCaller;
  
  public QueryReplyMessage(OutboxAmp outbox,
                           ServiceRefAmp serviceRef,
                            HeadersAmp headers,
                            ActorAmp from,
                            long qid,
                            Object value)
  {
    //InboxAmp inboxTarget = serviceRef.getInbox();

    _outboxCaller = outbox;
    
    _serviceRef = serviceRef;
    _headers = headers;
    _from = from;
    _qid = qid;
    _value = value;
  }
  
  @Override
  public void offer(long timeout)
  {
    getOutboxCaller().offer(this);
  }

  @Override
  public void offerQueue(long timeout)
  {
    _serviceRef.inbox().offerResult(this);
  }

  @Override
  public WorkerDeliver getWorker()
  {
    return _serviceRef.inbox().getWorker();
  }

  @Override
  public Type getType()
  {
    return Type.REPLY;
  }

  @Override
  public InboxAmp getInboxTarget()
  {
    return _serviceRef.inbox();
  }

  /*
  @Override
  public InboxAmp getInboxContext()
  {
    // XXX: technically incorrect
    return _serviceRef.getInbox();
  }
  */

  @Override
  public OutboxAmp getOutboxCaller()
  {
    // XXX: technically incorrect
    //return _serviceRef.getInbox();
    
    return _outboxCaller;
  }

  @Override
  public HeadersAmp getHeaders()
  {
    return _headers;
  }
  
  /*
  @Override
  public ActorAmp getActorInvoke(ActorAmp actor)
  {
    // return actorDeliver;
    return _from;
  }
  */

  @Override
  public void invoke(InboxAmp inbox, ActorAmp actorDeliver)
  {
    ActorAmp actor = _serviceRef.getActor();
    
    // kraken/210a vs baratine/2245
    ActorAmp actorInvoke = actorDeliver.getActor(actor);

    actorInvoke.queryReply(_headers, actor, _qid, _value);
    
    //System.out.println("ADQR: " + actorDeliver + " " + actor);
  }

  @Override
  public void fail(Throwable exn)
  {
    log.log(Level.FINE, exn.toString(), exn);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _serviceRef + "," + _qid + "," + _value + "]"; 
  }
}
