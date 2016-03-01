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

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.util.CacheListener;

/**
 * Handles the context for an actor, primarily including its
 * query map.
 */
public class ServiceRefChild extends ServiceRefPin implements CacheListener
{
  private ActorAmp _actor;
  private ActorAmp _prevActor;

  public ServiceRefChild(String address,
                         ActorAmp actor,
                         InboxAmp inbox)
  {
    super(address, actor, inbox);
    
    _actor = actor;
  }
  
  @Override
  public ActorAmp getActor()
  {
    ActorAmp actor = _actor;
    
    if (actor != null) {
      return actor;
    }
    
    ServiceRefAmp selfRef = manager().service(address());
    
    if (selfRef == this) {
      _actor = _prevActor;
    }
    else {
      _actor = selfRef.getActor();
    }
    
    return _actor;
  }
  
  @Override
  public boolean isPublic()
  {
    return inbox().serviceRef().isPublic();
  }

  @Override
  public void removeEvent()
  {
    ActorAmp actor = _actor;
    
    if (actor != null) {
      _prevActor = actor;
      _actor = null;
      actor.onLru(this);
    }
  }
}
