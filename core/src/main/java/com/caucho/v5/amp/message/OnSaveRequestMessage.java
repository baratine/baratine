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

import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.InboxAmp;

/**
 * Message to shut down an instance.
 */
public class OnSaveRequestMessage extends MessageAmpBase
  implements Result<Boolean>
{
  private boolean _isDisable;

  private final InboxAmp _inbox;
  private final Result<Void> _result;

  public OnSaveRequestMessage(InboxAmp inbox,
                              Result<Void> result)
  {
    Objects.requireNonNull(inbox);
    Objects.requireNonNull(result);
    
    _inbox = inbox;
    _result = result;
  }
  
  @Override
  public InboxAmp getInboxTarget()
  {
    return _inbox;
  }
  
  public void setDisable(boolean isDisable)
  {
    _isDisable = isDisable;
  }
  
  private boolean isDisable()
  {
    return _isDisable;
  }
  
  @Override
  public void invoke(InboxAmp inbox, 
                     ActorAmp actorDeliver)
  {
    if (! isDisable() && actorDeliver != null) {
      if (! actorDeliver.onSave(this)) {
        setDisable(true);
      }
    }
  }
  
  /*
  public void offer()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */

  @Override
  public void handle(Boolean result, Throwable exn)
  {
    long timeout = InboxAmp.TIMEOUT_INFINITY;
    
    if (! isDisable() && _inbox != null && ! _inbox.isClosed()) {
      _inbox.offerAndWake(new OnSaveCompleteMessage(exn == null), timeout);
      //_queue.wake();
    }
    
    setDisable(true);
    
    if (exn != null) {
      _result.fail(exn);
    }
    else {
      _result.ok(null);
    }
  }
}
