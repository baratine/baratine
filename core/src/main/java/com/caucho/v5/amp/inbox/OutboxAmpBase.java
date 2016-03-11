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

package com.caucho.v5.amp.inbox;

import com.caucho.v5.amp.outbox.OutboxImpl;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.OutboxAmp;

/**
 * Thread context for ramp events.
 */
public class OutboxAmpBase
  extends OutboxImpl implements OutboxAmp
{
  private MessageAmp _message;
  
  private int _openCount;
  
  public OutboxAmpBase()
  {
  }

  @Override
  public InboxAmp inbox()
  {
    return (InboxAmp) context();
  }
  
  @Override
  public void inbox(InboxAmp inbox)
  {
    getAndSetContext(inbox);
  }

  @Override
  public MessageAmp message()
  {
    return _message;
  }
  
  @Override
  public void message(MessageAmp message)
  {
    _message = message;
  }
  
  @Override
  public Object getAndSetContext(Object context)
  {
    _message = null;
    
    return super.getAndSetContext(context);
  }

  @Override
  public final void open()
  {
    _openCount++;
  }
  
  @Override
  public final void close()
  {
    if (closeImpl()) {
      super.close();
    }
  }
  
  protected boolean closeImpl()
  {
    return --_openCount <= 0;
  }
  
}
