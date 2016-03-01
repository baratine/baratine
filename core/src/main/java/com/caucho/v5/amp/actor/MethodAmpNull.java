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

import io.baratine.service.Result;
import io.baratine.service.ServiceExceptionMethodNotFound;
import io.baratine.service.ServiceExceptionNotFound;

import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.util.L10N;

/**
 * Sender for an actor ref.
 */
public final class MethodAmpNull extends MethodAmpBase
{
  private static final L10N L = new L10N(MethodAmpNull.class);
  
  private final ActorAmp _actor;
  private final String _methodName;
  
  public MethodAmpNull(ActorAmp actor, String methodName)
  {
    _actor = actor;
    _methodName = methodName;
  }

  @Override
  public void send(HeadersAmp headers,
                   ActorAmp actor,
                   Object[] args)
  {
  }

  @Override
  public void query(HeadersAmp headers, 
                    Result<?> result,
                    ActorAmp actor,
                    Object[] args)
  {
    if (! _actor.isClosed()) {
      String msg = L.l("{0} is an unknown method in {1}", 
                       _methodName, _actor.getName());
      
      result.fail(new ServiceExceptionMethodNotFound(msg));
    }
    else {
      String msg = L.l("{0} is an unknown service", _actor);
      
      result.fail(new ServiceExceptionNotFound(msg));
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _methodName + "," + _actor + "]";
  }
}
