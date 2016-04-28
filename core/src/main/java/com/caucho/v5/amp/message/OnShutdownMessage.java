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

import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.stub.StubAmp;

/**
 * Message to shut down an instance.
 */
public class OnShutdownMessage extends MessageAmpBase
{
  private InboxAmp _targetInbox;
  private final ResultFuture<Boolean> _future = new ResultFuture<>();
  private ShutdownModeAmp _mode;
  private boolean _isSingle;

  public OnShutdownMessage(InboxAmp inbox,
                           ShutdownModeAmp mode,
                           boolean isSingle)
  {
    _targetInbox = inbox;
    _mode = mode;
    _isSingle = isSingle;
  }
  
  @Override
  public InboxAmp inboxTarget()
  {
    return _targetInbox;
  }
  
  @Override
  public void invoke(InboxAmp inbox, StubAmp actorDeliver)
  {
    try {
      //actorDeliver.afterBatch();
      
      if (_isSingle) {
        // baratine/9260
        actorDeliver.state().shutdown(actorDeliver, _mode);
      }
      
      //inbox.close();
    } catch (ServiceExceptionClosed e) {
      _future.ok(true);
    } catch (Throwable e) {
      _future.fail(e);
    } finally {
      _future.ok(true);
    }
  }
  
  @Override
  public void fail(Throwable exn)
  {
    _future.fail(exn);
  }
  
  public Object waitFor(long time, TimeUnit unit)
  {
    return _future.get(time, unit);
  }
}
