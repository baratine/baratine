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

package com.caucho.v5.amp.remote;

import java.util.ArrayList;

import com.caucho.v5.amp.message.HeadersNull;
import com.caucho.v5.amp.message.MessageOutboxBase;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.OutboxAmp;

/**
 * Handles the context for an actor, primarily including its
 * query map.
 */
public class StreamGatewayResultMessage
  extends MessageOutboxBase
  {
  private ActorAmp _actor;

  private long _qid;
  private HeadersAmp  _headers;
  
  private ArrayList<Object> _values = new ArrayList<>();
  private boolean _isComplete;

  private boolean _isSent;

  private Throwable _exn;

  private int _sequence;

  public StreamGatewayResultMessage(OutboxAmp outbox,
                                    InboxAmp inbox,
                                    ActorAmp actor,
                                    long qid)
  {
    super(outbox, inbox);
    
    _headers = HeadersNull.NULL;

    _actor = actor;
    _qid = qid;
  }
  
  public boolean accept(Object value)
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
    
    _isComplete = true;
    _sequence = sequence;
    
    return true;
  }
  
  public boolean queueFail(Throwable exn)
  {
    if (_isSent) {
      return false;
    }
    
    _isComplete = true;
    _exn = exn;
    
    return true;
  }
  
  @Override
  public void offerQueue(long timeout)
  {
    _isSent = true;
    
    super.offerQueue(timeout);
  }

  @Override
  public void invoke(InboxAmp inbox, ActorAmp actor)
  {
    _isSent = true;

    if (_exn != null) {
      actor.streamReply(_headers, _actor, _qid, _sequence, _values, _exn, true);
    }
    else {
      actor.streamReply(_headers, _actor, _qid, _sequence, _values, _exn, _isComplete);
    }
    /*
    try {
      for (T value : _values) {
        _result.accept(value);
      }
      
      if (_isEnd) {
        _result.complete();
      }
    } catch (Throwable e) {
      _result.fail(e);
    }
    */
  }
}
