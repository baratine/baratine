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

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.journal.JournalAmp;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.InboxAmp;

/**
 * Message to shut down an instance.
 */
public class OnSaveMessage extends MessageAmpBase
  implements Result<Boolean>
{
  private static final Logger log
    = Logger.getLogger(OnSaveMessage.class.getName());
  
  private final JournalAmp _journal;

  //private final ServiceQueue<RampMessage> _queue;
  private final InboxAmp _inbox;

  private boolean _isDisable;

  public OnSaveMessage(JournalAmp journal,
                                InboxAmp inbox)
  {
    Objects.requireNonNull(inbox);
    
    _journal = journal;
    _inbox = inbox;
  }
  
  @Override
  public InboxAmp inboxTarget()
  {
    return null;
  }
  
  @Override
  public void invoke(InboxAmp inbox, 
                     ActorAmp actor)
  {
    if (! _isDisable) {
      _isDisable = ! actor.load(this).onSave(actor);
    }
  }
  
  public void offer()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public void handle(Boolean result, Throwable exn)
  {
    long timeout = InboxAmp.TIMEOUT_INFINITY;
    
    if (! _isDisable && ! _inbox.isClosed()) {
      _inbox.offerAndWake(new OnSaveCompleteMessage(exn == null), timeout);
      // _mailbox.wake();
    }
    
    if (exn != null) {
      log.log(Level.FINER, exn.toString(), exn);
    }
  }
}
