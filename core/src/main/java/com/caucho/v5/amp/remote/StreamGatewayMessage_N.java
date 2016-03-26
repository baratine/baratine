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

import io.baratine.service.Result;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.message.HeadersNull;
import com.caucho.v5.amp.message.QueryMessageBase;
import com.caucho.v5.amp.remote.GatewayReply;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.StubAmp;

/**
 * Handle to an amp instance.
 */
public class StreamGatewayMessage_N
  extends QueryMessageBase
{
  private final GatewayReply _gatewayReply;
  private final long _qid;
  private final Object []_args;

  public StreamGatewayMessage_N(OutboxAmp outboxCaller,
                                InboxAmp inboxCaller,
                                HeadersAmp headersCaller,
                                GatewayReply reply,
                                long qid,
                                ServiceRefAmp serviceRef,
                                MethodAmp method,
                                long timeout,
                                Object []args)
  {
    super(outboxCaller, inboxCaller, headersCaller, serviceRef, method, timeout);
    
    _gatewayReply = reply;

    _qid = qid;
    
    _args = args;
  }
  
  @Override
  public boolean isFuture()
  {
    return _gatewayReply.isAsync();
  }
  
  /*
  @Override
  public <U> void completeAsync(Result<U> result, U value)
  {
    _gatewayReply.completeAsync(result, value);
  }
  */
  
  @Override
  public void completeFuture(Object value)
  {
    ok(value);
  }

  @Override
  public final void invokeQuery(InboxAmp inbox, StubAmp actorDeliver)
  {
    StubAmp actorMessage = serviceRef().stub();

    actorDeliver.load(actorMessage, this)
                .query(actorDeliver, actorMessage,
                       getMethod(),
                       getHeaders(),
                       this,
                       _args);
  }

  @Override
  protected boolean invokeOk(StubAmp actor)
  {
    HeadersAmp headers = null;
    
    if (headers == null) {
      headers = HeadersNull.NULL;
    }

    _gatewayReply.queryOk(headers, _qid, getReply());
    
    return true;
  }

  @Override
  protected boolean invokeFail(StubAmp actor)
  {
    HeadersAmp headers = null;
    
    if (headers == null) {
      headers = HeadersNull.NULL;
    }
    
    _gatewayReply.queryFail(headers, _qid, getException());
    
    return true;
  }
}
