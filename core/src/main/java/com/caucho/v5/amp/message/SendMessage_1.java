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

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.StubStateAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.StubAmp;

/**
 * Handle to an amp instance.
 */
public final class SendMessage_1 extends MethodMessageBase
{
  private final Object _arg1;

  /*
  public SendMessage_1(ServiceRefAmp serviceRef,
                       MethodAmp method,
                       Object arg1)
  {
    super(serviceRef, method);
    
    _arg1 = arg1;
  }
  */

  public SendMessage_1(OutboxAmp outbox,
                       ServiceRefAmp serviceRef,
                       MethodAmp method,
                       Object arg1)
  {
    super(outbox, serviceRef, method);

    _arg1 = arg1;
  }

  public SendMessage_1(OutboxAmp outbox,
                       HeadersAmp headers,
                       ServiceRefAmp serviceRef,
                       MethodAmp method,
                       Object arg1)
  {
    super(outbox, headers, serviceRef, method);
    
    _arg1 = arg1;
  }

  @Override
  public final void invoke(InboxAmp inbox, StubAmp actorDeliver)
  {
    StubAmp actorMessage = serviceRef().stub();
    
    StubStateAmp load = actorDeliver.load(actorMessage, this);
    
    load.send(actorDeliver,
               actorMessage,
               method(),
               getHeaders(),
               _arg1);
  }
}
