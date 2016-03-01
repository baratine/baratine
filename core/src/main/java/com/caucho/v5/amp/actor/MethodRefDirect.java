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

package com.caucho.v5.amp.actor;

import io.baratine.service.Result;
import io.baratine.spi.Headers;

import java.util.concurrent.TimeUnit;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.message.HeadersNull;
import com.caucho.v5.amp.message.QueryWithResultMessage_N;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.MethodAmp;

/**
 * Handles the context for an actor, primarily including its
 * query map.
 */
public class MethodRefDirect extends MethodRefImpl
{
  public MethodRefDirect(ServiceRefAmp serviceRef,
                        MethodAmp method,
                        InboxAmp mailbox)
  {
    //super(serviceRef, method, mailbox);
    super(serviceRef, method);
  }
  /*
  @Override
  public void offer(MessageAmp message)
  {
    message.invoke(getInbox(), getInbox().getDirectActor());
  }

  @Override
  public <T> void query(Headers headers,
                        Result<T> cb, 
                        long timeout, TimeUnit timeUnit,
                        Object... args)
  {
    QueryWithResultMessage_N<T> msg;
    
    if (timeUnit != null) {
      timeout = timeUnit.toMillis(timeout);
    }
    
    ActorAmp actor = getInbox().getDirectActor();
    
    MethodAmp method = getMethod();
    HeadersAmp rampHeaders;
    
    if (headers instanceof HeadersAmp)
      rampHeaders = (HeadersAmp) headers;
    else
      rampHeaders = HeadersNull.NULL;
    
    msg = new QueryWithResultMessage_N<T>(cb, timeout, 
                                          getService(), getMethod(), 
                                          args);
    
    method.query(rampHeaders, msg, actor, args);
  }
  */
}
