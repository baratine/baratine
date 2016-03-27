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
 * service proxy for a stream call. The proxy will queue result messages to 
 * the client stub.
 */
class StreamCallTargetProxy implements ResultStream<Object>
{
  private InboxAmp _callerInbox;

  private StreamResultMessage<Object> _resultMessage;
  private ResultStream<Object> _next;

  StreamCallTargetProxy(ResultStream<Object> next,
                        InboxAmp callerInbox)
  {
    _next = next;
    _callerInbox = callerInbox;
  }

  @Override
  public void start()
  {
    //  _resultJoinLocal.start();
  }

  private ServicesAmp getManager()
  {
    return _callerInbox.manager();
  }

  private InboxAmp getCallerInbox()
  {
    return _callerInbox;
  }

  @Override
  public void accept(Object value)
  {
    StreamResultMessage<Object> resultMessage = _resultMessage;

    if (resultMessage == null || ! resultMessage.add(value)) {

      try (OutboxAmp outbox = OutboxAmp.currentOrCreate(getManager())) {
        _resultMessage = resultMessage = createResultMessage(outbox);
        resultMessage.add(value);
        resultMessage.offer(0);
      }
    }
  }

  @Override
  public void ok()
  {
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
  }

  @Override
  public void fail(Throwable exn)
  {
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
  }
  
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

  private StreamResultMessage<Object> createResultMessage(OutboxAmp outbox)
  {
    // OutboxAmp outbox = getServiceRef().getManager().getCurrentOutbox();
    
    return new StreamResultMessage<>(outbox, getCallerInbox(), _next);
  }

  @Override
  public StreamCallTargetProxy flush()
  {
    _resultMessage = null;
    
    return this;
  }

  @Override
  public boolean isCancelled()
  {
    return _next.isCancelled();
  }

  /*
    @Override
    public int getCredit()
    {
      int credit = getNext().getCredit();

      long delta = _sequenceAccept - _resultLocal.getSequenceAccept();

      return Math.max((int) (credit - delta), 0);
    }
   */

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getCallerInbox() + "," + _next + "]";
  }
}
