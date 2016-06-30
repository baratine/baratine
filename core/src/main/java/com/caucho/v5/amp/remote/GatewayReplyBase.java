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

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.message.QueryErrorMessage;
import com.caucho.v5.amp.message.QueryReplyMessage;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.QueryRefAmp;

import io.baratine.stream.ResultStream;

/**
 * Interface for a reply to a gateway query.
 */
public class GatewayReplyBase implements GatewayReply
{
  private final ServiceRefAmp _serviceRef;
  
  public GatewayReplyBase(ServiceRefAmp serviceRef)
  {
    _serviceRef = serviceRef;
  }
  
  @Override
  public boolean isAsync()
  {
    // return true;
    return false;
  }

  @Override
  public void queryOk(HeadersAmp headers, long qid, Object value)
  {
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_serviceRef.services())) {
      MessageAmp msg = new QueryReplyMessage(outbox,
                                             _serviceRef, 
                                             headers,
                                             _serviceRef.stub(),
                                             qid, 
                                             value);
    
      long timeout = 0;
      msg.offer(timeout);
    }
  }

  /* (non-Javadoc)
   * @see com.caucho.ramp.remote.GatewayReply#queryError(com.caucho.ramp.spi.RampHeaders, long, java.lang.Throwable)
   */
  @Override
  public void queryFail(HeadersAmp headers, long qid, Throwable exn)
  {
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_serviceRef.services())) {
      _serviceRef.offer(new QueryErrorMessage(outbox,
                                              _serviceRef, 
                                              headers,
                                              _serviceRef.stub(),
                                              qid, 
                                              exn));
    }
  }
  
  @Override
  public ResultStream<Object> stream(HeadersAmp headers, long qid)
  {
    ResultStreamGateway result
      = new ResultStreamGateway(this, _serviceRef, headers, qid);
    
    _serviceRef.inbox().addQuery(qid, result, result);
    
    return result;
  }
  
  @Override
  public void streamCancel(long qid)
  {
    QueryRefAmp queryRef = _serviceRef.inbox().removeQueryRef(qid);
    
    if (queryRef != null) {
      queryRef.cancel();
    }
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _serviceRef + "]";
  }
}
