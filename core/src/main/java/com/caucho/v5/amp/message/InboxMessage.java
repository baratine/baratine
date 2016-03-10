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

import com.caucho.v5.amp.queue.WorkerDeliver;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.InboxAmp;

/**
 * context message for non-actor clients.
 */
public final class InboxMessage extends MessageAmpBase
{
  private final InboxAmp _inbox;

  public InboxMessage(final InboxAmp mailbox)
  {
    _inbox = mailbox;
  }

  @Override
  public final InboxAmp inboxTarget()
  {
    return _inbox;
  }

  @Override
  public final WorkerDeliver worker()
  {
    return _inbox.getWorker();
  }

  @Override
  public void offerQueue(long timeout)
  {
    throw new IllegalStateException(getClass().getName());
  }

  /*
  @Override
  public void wakeQueue()
  {
    throw new IllegalStateException(getClass().getName());
  }
  */

  @Override
  public void invoke(InboxAmp mailbox, ActorAmp actor)
  {
    throw new IllegalStateException(getClass().getName());
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _inbox + "]";
  }
}
