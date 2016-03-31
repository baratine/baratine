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
import io.baratine.service.ServiceRef;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.journal.StubJournal;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.StubAmp;

/**
 * Handle to an amp instance.
 */
public class ReplayQueryMessage<V> extends MessageAmpResult<V>
{
  private final String _keyPath;
  private final String _methodName;
  private final Object []_args;

  public ReplayQueryMessage(String keyPath,
                            String methodName,
                            Object []args)
  {
    _keyPath = keyPath;
    _methodName = methodName;
    _args = args;
  }

  @Override
  public void invoke(InboxAmp inbox, StubAmp actor)
  {
    if (actor instanceof StubJournal) {
      return;
    }
    
    if (_keyPath != null) {
      ServiceRef parentRef = inbox.serviceRef();
      
      Object childLookup = parentRef.service(_keyPath);
      
      if (childLookup instanceof ServiceRefAmp) {
        ServiceRefAmp childServiceRef = (ServiceRefAmp) childLookup;
        
        actor = childServiceRef.stub();
      }
    }
    
    MethodAmp method = actor.methodByName(_methodName);
    
    if (method != null) {
      actor = actor.worker(actor);
      
      actor.loadReplay(inbox, this)
           .query(actor, actor,
                  method,
                  getHeaders(),
                  this,
                  _args);
    }
  }

  @Override
  public void ok(V value)
  {
  }

  @Override
  public void fail(Throwable exn)
  {

  }

  @Override
  public InboxAmp inboxTarget()
  {
    return null;
  }
}
