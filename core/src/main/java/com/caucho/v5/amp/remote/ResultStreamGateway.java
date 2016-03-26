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

import io.baratine.service.Cancel;
import io.baratine.stream.ResultStream;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.OutboxAmp;

/**
 * Interface for a reply to a gateway query.
 */
public class ResultStreamGateway implements ResultStream<Object>, Cancel
{
  private final GatewayReply _gatewayReply;
  private final ServiceRefAmp _serviceRef;
  private HeadersAmp _headers;
  private long _qid;
  private int _sequence;
  
  private boolean _isCancelled;
  private StreamGatewayResultMessage _msg;
  
  public ResultStreamGateway(GatewayReply gatewayReply,
                             ServiceRefAmp serviceRef,
                             HeadersAmp headers,
                             long qid)
  {
    _gatewayReply = gatewayReply;
    _serviceRef = serviceRef;
    _headers = headers;
    _qid = qid;
  }
  
  private ServiceManagerAmp getManager()
  {
    return _serviceRef.manager();
  }

  @Override
  public void accept(Object value)
  {
    StreamGatewayResultMessage msg = _msg;
    
    if (msg == null || ! msg.accept(value)) {
      try (OutboxAmp outbox = OutboxAmp.currentOrCreate(getManager())) {
        _msg = msg = create(outbox);
        msg.accept(value);
        _sequence++;
        long timeout = 0;
        msg.offer(timeout);
      }
    }
  }

  @Override
  public void ok()
  {
    StreamGatewayResultMessage msg = _msg;
    
    if (msg == null || ! msg.complete(_sequence)) {
      try (OutboxAmp outbox = OutboxAmp.currentOrCreate(getManager())) {
        _msg = msg = create(outbox);
        msg.complete(_sequence);
        long timeout = 0;
        msg.offer(timeout);
      }
    }
    
    cancel();
  }
  
  @Override
  public void fail(Throwable exn)
  {
    StreamGatewayResultMessage msg = _msg;
    
    if (msg == null || ! msg.queueFail(exn)) {
      try (OutboxAmp outbox = OutboxAmp.currentOrCreate(getManager())) {
        _msg = msg = create(outbox);
        msg.queueFail(exn);
        long timeout = 0;
        msg.offer(timeout);
      }
    }
    
    cancel();
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
    return _isCancelled;
  }
  
  @Override
  public void cancel()
  {
    if (! _isCancelled) {
      _isCancelled = true;
      
      _gatewayReply.streamCancel(_qid);
    }
  }
  
  private StreamGatewayResultMessage create(OutboxAmp outbox)
  {
    long timeout = InboxAmp.TIMEOUT_INFINITY;
    
    //OutboxAmp outbox = _serviceRef.getManager().getCurrentOutbox();
    
    StreamGatewayResultMessage msg;
    
    msg = new StreamGatewayResultMessage(outbox,
                                         _serviceRef.inbox(),
                                         _serviceRef.stub(), 
                                         _qid);
    
    //msg.offer(timeout);
    //msg.getWorker().wake();
    
    return msg;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _serviceRef + "," + _qid + "]";
  }
}
