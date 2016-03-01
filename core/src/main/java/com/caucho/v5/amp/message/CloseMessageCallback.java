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

import io.baratine.service.ResultFuture;
import io.baratine.service.ServiceExceptionClosed;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

/**
 * Message to shut down an instance.
 */
public class CloseMessageCallback extends MessageAmpBase
{
  private static final Logger log
    = Logger.getLogger(CloseMessageCallback.class.getName());
  
  private InboxAmp _targetMailbox;
  private ActorAmp _actor;
  
  private ResultFuture<Boolean> _future = new ResultFuture<>();

  public CloseMessageCallback(InboxAmp mailbox, 
                              ActorAmp actor)
  {
    _targetMailbox = mailbox;
    _actor = actor;
  }
  
  @Override
  public InboxAmp getInboxTarget()
  {
    return _targetMailbox;
  }
  
  @Override
  public void invoke(InboxAmp mailbox, 
                     ActorAmp actor)
  {
    try {
      _actor.onShutdown(ShutdownModeAmp.GRACEFUL);
    
      _future.ok(true);
    } catch (Throwable e) {
      _future.fail(e);
    }
  }
  
  public Boolean get(long timeout, TimeUnit unit)
  {
    return _future.get(timeout, unit);
  }

  @Override
  public void fail(Throwable exn)
  {
    if (exn instanceof ServiceExceptionClosed) {
      log.log(Level.FINEST, exn.toString(), exn);
      _future.ok(true);
    }
    else {
      _future.fail(exn);
    }
  }
}
