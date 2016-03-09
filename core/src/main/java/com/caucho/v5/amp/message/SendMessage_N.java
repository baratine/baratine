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

import io.baratine.spi.Headers;

import java.util.Arrays;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.proxy.ActorAmpBean;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MethodAmp;
import com.caucho.v5.amp.spi.OutboxAmp;

/**
 * Handle to an amp instance.
 */
public final class SendMessage_N extends MethodMessageBase
{
  private static final Logger log
    = Logger.getLogger(SendMessage_N.class.getName());
  
  private final Object []_args;

  /*
  public SendMessage_N(ServiceRefAmp serviceRef,
                       MethodAmp method,
                       Object []args)
  {
    super(serviceRef, method);
    
    _args = args;
  }
  */

  public SendMessage_N(OutboxAmp outbox,
                       ServiceRefAmp serviceRef,
                       MethodAmp method,
                       Object []args)
  {
    super(outbox, serviceRef, method);
    
    _args = args;
  }

  /*
  public SendMessage_N(Headers headers,
                       ServiceRefAmp serviceRef,
                       MethodAmp method,
                       Object []args)
  {
    super(headers, serviceRef, method);
    
    _args = args;
  }
  */

  public SendMessage_N(OutboxAmp outboxCaller,
                       HeadersAmp callerHeaders,
                       ServiceRefAmp serviceRef,
                       MethodAmp method,
                       Object []args)
  {
    super(outboxCaller, callerHeaders, serviceRef, method);
    
    _args = args;
  }

  @Override
  public final void invoke(InboxAmp inbox, ActorAmp actorDeliver)
  {
    ActorAmp actorMessage = serviceRef().getActor();
    
    actorDeliver.load(actorMessage, this)
                .send(actorDeliver,
                      actorMessage,
                      getMethod(),
                      getHeaders(),
                      _args);
  }
}
