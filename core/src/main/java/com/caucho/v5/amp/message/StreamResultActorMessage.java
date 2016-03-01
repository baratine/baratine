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

import io.baratine.service.ResultStream;

import java.util.ArrayList;
import java.util.Objects;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.OutboxAmp;

/**
 * Handles the context for an actor, primarily including its
 * query map.
 */
public class StreamResultActorMessage
  extends MessageOutboxBase
{
  private final ServiceRefAmp _serviceRef;
  private final long _qid;

  private ArrayList<Object> _values = new ArrayList<>();
  private Throwable _exn;
  private int _sequence;
  private boolean _isComplete;

  private boolean _isSent;

  public StreamResultActorMessage(OutboxAmp outbox,
                                  ServiceRefAmp serviceRef,
                                  long qid)
  {
    super(outbox, serviceRef.inbox());
    
    _serviceRef = serviceRef;
    _qid = qid;
  }
  
  ServiceRefAmp getServiceRef()
  {
    return _serviceRef;
  }
  
  boolean isSent()
  {
    return _isSent;
  }
  
  public boolean add(Object value)
  {
    if (_isSent) {
      return false;
    }

    _values.add(value);
    
    return true;
  }
  
  public boolean complete(int sequence)
  {
    if (_isSent) {
      return false;
    }
    
    _sequence = sequence;
    _isComplete = true;
    
    return true;
  }
  
  @Override
  public void fail(Throwable exn)
  {
    failQueue(exn);
  }
  
  public boolean failQueue(Throwable exn)
  {
    if (_isSent) {
      return false;
    }
    
    _exn = exn;
    
    _isComplete = true;
    
    return true;
  }
  
  @Override
  public void offerQueue(long timeout)
  {
    _isSent = true;
    
    super.offerQueue(timeout);
  }

  @Override
  public void invoke(InboxAmp inbox, ActorAmp actorDeliver)
  {
    _isSent = true;
    
    ActorAmp actorMessage = getServiceRef().getActor();
    
    actorDeliver.load(actorMessage, this)
                .streamResult(actorDeliver, actorMessage, 
                              getHeaders(),
                              _qid, _sequence, _values, _exn, _isComplete);
  }
}
