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

package com.caucho.v5.amp.actor;

import com.caucho.v5.amp.manager.AmpManager;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.QueryRefAmp;

/**
 * System actor
 */
public final class ActorAmpSystem extends ActorAmpNull
{
  private final AmpManager _manager;
  private InboxAmp _inbox;
  
  public ActorAmpSystem(String address, AmpManager manager)
  {
    super(address);
    
    _manager = manager;
  }

  public void setInbox(InboxAmp inbox)
  {
    _inbox = inbox;
  }
  
  @Override
  public boolean isClosed()
  {
    return _inbox.isClosed();
  }
  
  @Override
  public boolean isUp()
  {
    return ! _inbox.isClosed();
  }

  @Override
  public void queryReply(HeadersAmp headers,
                         ActorAmp actor,
                         long qid, 
                         Object value)
  {
    QueryRefAmp queryRef = _inbox.removeQueryRef(qid);
    
    if (queryRef != null) {
      queryRef.complete(headers, value);
    }
    else {
      super.queryReply(headers, actor, qid, value);
    }
  }

  @Override
  public void queryError(HeadersAmp headers,
                         ActorAmp actor,
                         long qid, 
                         Throwable exn)
  {
    QueryRefAmp queryRef = _inbox.removeQueryRef(qid);
    
    if (queryRef != null) {
      queryRef.fail(headers, exn);
    }
    else {
      super.queryError(headers, actor, qid, exn);
    }
  }
}
