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
import com.caucho.v5.amp.message.QueryMessageBase;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.StubAmp;

import io.baratine.service.Result;

/**
 * Handle to an amp instance.
 */
public class QueryGatewayBaseMessage_N
  extends QueryMessageBase<Object>
  implements Result<Object>
{
  private final Object []_args;

  public QueryGatewayBaseMessage_N(OutboxAmp outboxCaller,
                                   InboxAmp inboxCaller,
                                   HeadersAmp headersCaller,
                                   ServiceRefAmp serviceRef,
                                   MethodAmp method,
                                   long timeout,
                                   Object []args)
  {
    super(outboxCaller, inboxCaller, headersCaller, serviceRef, method, timeout);

    _args = args;
  }

  @Override
  public final void invokeQuery(InboxAmp inbox, StubAmp actorDeliver)
  {
    StubAmp actorMessage = serviceRef().stub();

    actorDeliver.load(actorMessage, this)
                .query(actorDeliver, actorMessage,
                       method(),
                       getHeaders(),
                       this,
                       _args);
  }

  @Override
  public void handle(Object value, Throwable fail) throws Exception
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
}
