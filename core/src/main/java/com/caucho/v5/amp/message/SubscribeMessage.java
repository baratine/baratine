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

import io.baratine.service.ServiceRef;

import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.InboxAmp;

/**
 * Message to shut down an instance.
 */
public class SubscribeMessage extends MessageAmpBase
{
  private final InboxAmp _inbox;
  private final ServiceRef _service;

  public SubscribeMessage(InboxAmp mailbox,
                          ServiceRef service)
  {
    _inbox = mailbox;
    _service = service;
  }
  
  protected ServiceRef getService()
  {
    return _service;
  }
  
  @Override
  public InboxAmp getInboxTarget()
  {
    return _inbox;
  }
  
  @Override
  public void invoke(InboxAmp inbox, ActorAmp actor)
  {
    actor.subscribe(_service);
  }
  
  public void offer()
  {
    long timeout = InboxAmp.TIMEOUT_INFINITY;
    
    getInboxTarget().offerAndWake(this, timeout);
  }
}
