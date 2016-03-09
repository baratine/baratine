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

import io.baratine.service.Result;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MethodAmp;
import com.caucho.v5.amp.spi.OutboxAmp;

/**
 * Handle to an amp instance.
 */
public class QueryWithResultMessage_1<V> 
  extends QueryWithResultMessage<V>
{
  private final Object _arg1;

  /*
  public QueryWithResultMessage_1(Result<V> result,
                                     long timeout,
                                     ServiceRefAmp serviceRef,
                                     MethodAmp method,
                                     Object arg1)
  {
    super(serviceRef, method, timeout, result);
    
    _arg1 = arg1;
  }
  */

  public QueryWithResultMessage_1(OutboxAmp outbox,
                                  Result<V> result,
                                  long timeout,
                                  ServiceRefAmp serviceRef,
                                  MethodAmp method,
                                  Object arg1)
  {
    super(outbox, serviceRef, method, timeout, result);
    
    _arg1 = arg1;
  }

  public QueryWithResultMessage_1(OutboxAmp outbox,
                                  HeadersAmp headers,
                                  Result<V> result,
                                  long timeout,
                                  ServiceRefAmp serviceRef,
                                  MethodAmp method,
                                  Object arg1)
  {
    super(outbox, headers, serviceRef, method, timeout, result);
    
    _arg1 = arg1;
  }

  @Override
  public final void invokeQuery(InboxAmp inbox, ActorAmp actorDeliver)
  {
    try {
      ActorAmp actorMessage = serviceRef().getActor();
      
      actorDeliver.load(actorMessage, this)
                  .query(actorDeliver, actorMessage,
                         getMethod(),
                         getHeaders(),
                         this,
                         _arg1);
    } catch (Throwable e) {
      fail(e);
    }
  }
}
