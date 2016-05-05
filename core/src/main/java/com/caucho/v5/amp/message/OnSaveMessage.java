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

import java.util.Objects;

import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.stub.StubAmp;

import io.baratine.service.Result;

/**
 * Message to shut down an instance.
 */
public class OnSaveMessage extends MessageAmpBase
{
  private boolean _isDisable;

  private final StubAmp _stubMessage;
  private final Result<Void> _result;

  private InboxAmp _inbox;

  public OnSaveMessage(InboxAmp inbox,
                              StubAmp stubMessage,
                              Result<Void> result)
  {
    Objects.requireNonNull(stubMessage);
    Objects.requireNonNull(result);
    
    _inbox = inbox;
    _stubMessage = stubMessage;
    _result = result;
  }
  
  @Override
  public InboxAmp inboxTarget()
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
                     StubAmp stubDeliver)
  {
    StubAmp stub = stubDeliver.worker(_stubMessage);
    
    stub.onSaveRequest((v,exn)->afterSave(exn));
  }
  
  private void afterSave(Throwable exn)
  {
    if (exn != null) {
      exn.printStackTrace();
      _result.fail(exn);
    }
    else {
      _result.ok(null);
    }
    
    long timeout = 10000;
    inboxTarget().offer(new OnSaveCompleteMessage(inboxTarget(), 
                                                  _stubMessage, 
                                                  exn == null),
                        timeout);
  }
  
  /*
  public void offer()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */

  /*
  @Override
  public void handle(Void result, Throwable exn)
  {
    long timeout = InboxAmp.TIMEOUT_INFINITY;
    
    if (exn != null) {
      _result.fail(exn);
    }
    else {
      _result.ok(null);
    }
  }
  */
}
