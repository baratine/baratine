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

import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.stub.StubAmp;

/**
 * Message to complete a checkpoint
 */
public class OnSaveCompleteMessage extends MessageAmpBase
{
  private static final Logger log
    = Logger.getLogger(OnSaveCompleteMessage.class.getName());
  
  private final boolean _isValid;

  private InboxAmp _inbox;

  private StubAmp _stubMessage;

  public OnSaveCompleteMessage(InboxAmp inbox,
                               StubAmp stub,
                               boolean isValid)
  {
    _inbox = inbox;
    _stubMessage = stub;
    _isValid = isValid;
  }
  
  @Override
  public InboxAmp inboxTarget()
  {
    return _inbox;
  }
  
  @Override
  public void invoke(InboxAmp inbox, 
                     StubAmp stub)
  {
    try {
      stub.onSaveEnd(_isValid);
    } catch (Throwable e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }
  
  public void offer()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public void fail(Throwable exn)
  {
    log.finer(String.valueOf(exn));

    if (log.isLoggable(Level.FINEST)) {
      log.log(Level.FINEST, exn.toString(), exn);
    }
  }
}
