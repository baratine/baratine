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
import com.caucho.v5.amp.deliver.WorkerDeliver;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.stub.StubAmp;

/**
 * Message for a reply.
 */
public final class QueryErrorMessage implements MessageAmp
{
  private static final Logger log
    = Logger.getLogger(QueryErrorMessage.class.getName());
  
  private final ServiceRefAmp _serviceRef;
  private final HeadersAmp _headers;
  private final long _qid;
  private final Throwable _exn;
  private final StubAmp _from;

  private OutboxAmp _outbox;
  
  public QueryErrorMessage(OutboxAmp outbox,
                           ServiceRefAmp serviceRef,
                            HeadersAmp headers,
                            StubAmp from,
                            long qid,
                            Throwable exn)
  {
    _outbox = outbox;
    _serviceRef = serviceRef;
    _headers = headers;
    _from = from;
    _qid = qid;
    _exn = exn;
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
  public WorkerDeliver worker()
  {
    return _serviceRef.inbox().worker();
  }

  /*
  @Override
  public Type getType()
  {
    return Type.REPLY;
  }
  */

  @Override
  public InboxAmp inboxTarget()
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
    return _outbox;
  }

  @Override
  public HeadersAmp getHeaders()
  {
    return _headers;
  }
  
  /*
  @Override
  public ActorAmp getActorInvoke(ActorAmp actorDeliver)
  {
    return actorDeliver;
  }
  */
  
  @Override
  public void invoke(InboxAmp mailbox, StubAmp actorDeliver)
  {
    // actorDeliver.queryError(_headers, _from, _qid, _exn);
    
    StubAmp actorMessage = _serviceRef.stub();
    
    // kraken/210a vs baratine/2245
    StubAmp actorInvoke = actorDeliver.worker(actorMessage);
    
    // actorInvoke.queryError(_headers, actorMessage, _qid, _exn);
    actorInvoke.queryError(_headers, _from, _qid, _exn);
  }
 
  @Override
  public void fail(Throwable exn)
  {
    log.log(Level.FINE, exn.toString(), exn);
  }
}
