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

package com.caucho.v5.ramp.jamp;

import io.baratine.service.ServiceExceptionIllegalState;
import io.baratine.service.SessionContext;

import java.util.function.Function;

import com.caucho.v5.inject.impl.InjectContext;
import com.caucho.v5.util.L10N;

/**
 * CDI context for a channel.
 */
public class SessionContextJamp implements InjectContext
{
  private static final L10N L = new L10N(SessionContextJamp.class);
  
  private static ThreadLocal<SessionContext> _contextLocal
    = new ThreadLocal<>();
  
  public static SessionContext getCurrent()
  {
    SessionContext context = _contextLocal.get();
    
    if (context == null) {
      throw new ServiceExceptionIllegalState(L.l("Service is not called from a session context. jamp-rpc and REST are not valid session contexts. Use websockets or push/pull/poll instead."));
    }
    
    return context;
  }
  
  /*
  @Override
  public Class<? extends Annotation> getScope()
  {
    return SessionScoped.class;
  }

  @Override
  public boolean isActive()
  {
    // TODO Auto-generated method stub
    return false;
  }
  */

  /*
  @Override
  public <T> T get(Contextual<T> bean, CreationalContext<T> env)
  {
    ChannelContextJamp broker = _contextLocal.get();
    
    if (broker == null) {
      return bean.create(env);
    }

    T value = broker.get(bean);
    
    if (value == null) {
      value = bean.create(env);
      
      broker.put(bean, value);
    }
        
    return value;
  }
*/
  
  public void start(SessionContext broker)
  {
    _contextLocal.set(broker);
  }

  @Override
  public Function<String, Object> getVarMap()
  {
    return x->null;
  }
}
