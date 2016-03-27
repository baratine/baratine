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

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.OutboxAmp;

import io.baratine.stream.ResultStream;

/**
 * Handles the context for an actor, primarily including its
 * query map.
 */
public class ResultStreamFork implements ResultStreamAmp<Object>
{
  private InboxAmp _inbox;
  private ResultStreamJoin<?> _join;
  
  //private long _sequenceAccept;
  private StreamResultMessage<Object> _resultMessage;
  
  private boolean _isComplete;
  
  ResultStreamFork(ResultStreamJoin<?> join, InboxAmp inbox)
  {
    _join = join;

    _join.start();
    //ServiceRefAmp serviceRef = (ServiceRefAmp) ServiceRef.current();
    //ServiceRefAmp serviceRef = serviceRefResult;
    
    _inbox = inbox;
  }
    
  /*
    @Override
    public void start()
    {
      _resultJoinLocal.start();
    }
  */
  
  @Override
  public InboxAmp getInbox()
  {
    return _inbox;
  }
  
  private ServicesAmp getManager()
  {
    return _inbox.manager();
  }
    
  @Override
  public void accept(Object value)
  {
    if (_isComplete) {
      return;
    }
    
    _join.accept(value);
    /*
    //_sequenceAccept++;
      
    StreamResultMessage<Object> resultMessage = _resultMessage;
    
    if (resultMessage == null || ! resultMessage.add(value)) {
      try (OutboxAmp outbox = OutboxAmp.currentOrCreate(getManager())) {
        _resultMessage = createResultMessage(outbox);
        _resultMessage.add(value);
        _resultMessage.offer(0);
      }
    }
    */
  }

  @Override
  public void ok()
  {
    if (_isComplete) {
      return;
    }
    
    _isComplete = true;
    
    _join.ok();
    /*
    StreamResultMessage<Object> resultMessage = _resultMessage;
    _resultMessage = null;
      
    if (resultMessage == null || ! resultMessage.complete()) {
      try (OutboxAmp outbox = OutboxAmp.currentOrCreate(getManager())) {
        resultMessage = createResultMessage(outbox);
        resultMessage.complete();
        resultMessage.offer(0);
      }
    }
      
    _resultMessage = null;
    */
  }
    
  @Override
  public void fail(Throwable exn)
  {
    _join.fail(exn);
    /*
    StreamResultMessage<Object> resultMessage = _resultMessage;
    _resultMessage = null;
      
    if (resultMessage == null || ! resultMessage.failQueue(exn)) {
      try (OutboxAmp outbox = OutboxAmp.currentOrCreate(getManager())) {
        resultMessage = createResultMessage(outbox);
        resultMessage.failQueue(exn);
        resultMessage.offer(0);
      }
    }
      
    _resultMessage = null;
    */
  }
  
  @Override
  public void handle(Object value, Throwable exn, boolean ok)
  {
    if (ok) {
      ok();
    }
    else if (exn != null) {
      fail(exn);
    }
    else {
      accept(value);
    }
  }
  
  @Override
  public boolean isCancelled()
  {
    return _join.isCancelled();
  }
  
  private StreamResultMessage<Object> createResultMessage(OutboxAmp outbox)
  {
    // OutboxAmp outbox = getServiceRef().getManager().getCurrentOutbox();
    
    return new StreamResultMessage<>(outbox, _inbox, _join);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _inbox + "," + _join + "]";
  }

  @Override
  public ResultStream<Object> fork()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
}
