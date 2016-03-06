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

import io.baratine.service.ServiceExceptionClosed;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.queue.MessageDeliverBase;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.OutboxAmp;

/**
 * Handle to an amp instance.
 */
abstract public class MessageAmpBase extends MessageDeliverBase
  implements MessageAmp
{
  private static final Logger log
    = Logger.getLogger(MessageAmpBase.class.getName());
  
  public Type getType()
  {
    return Type.UNKNOWN;
  }
  
  @Override
  public HeadersAmp getHeaders()
  {
    return HeadersNull.NULL;
  }
  
  /*
  @Override
  public InboxAmp getInboxContext()
  {
    return getInboxTarget();
  }
  */
  
  @Override
  public OutboxAmp getOutboxCaller()
  {
    //return getInboxTarget();
    throw new UnsupportedOperationException();
  }
  
  /*
  @Override
  public ActorAmp getActorInvoke(ActorAmp actorDeliver)
  {
    return actorDeliver;
  }
  */
  
  @Override
  public void offer(long timeout)
  {
    inboxTarget().offerAndWake(this, timeout);
  }

  @Override
  public void fail(Throwable exn)
  {
    if (exn instanceof ServiceExceptionClosed) {
      log.log(Level.FINEST, exn.toString(), exn);
    }
    else {
      log.log(Level.FINE, exn.toString(), exn);
    }
  }
}
