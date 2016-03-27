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

package com.caucho.v5.amp.stub;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.proxy.ProxyHandleAmp;
import com.caucho.v5.util.L10N;

import io.baratine.service.Pin;
import io.baratine.service.ServiceRef;

/**
 * Pins an argument to the calling service.
 */
public class FilterPinArg
{
  private static final L10N L = new L10N(FilterPinArg.class);

  private final ServicesAmp _manager;
  private final Class<?> _api;

  FilterPinArg(ServicesAmp manager,
                       Class<?> api)
  {
    _manager = manager;
    _api = api;
  }

  public Object filter(Object arg)
  {
    if (arg == null) {
      return null;
    }

    if (_api.isAssignableFrom(arg.getClass())) {
      if (! (arg instanceof ProxyHandleAmp)) {
        throw new IllegalArgumentException(L.l("{0} is an invalid @{1} argument because it is not an AMP service.",
                                               arg, Pin.class));
      }

      return arg;
    }
    else if (ServiceRef.class.isAssignableFrom(_api)) {
      if (arg instanceof ServiceRef) {
        return arg;
      }
      else if (arg instanceof ProxyHandleAmp) {
        ProxyHandleAmp proxy = (ProxyHandleAmp) arg;
        
        return proxy.__caucho_getServiceRef();
      }
      else {
        throw new IllegalArgumentException(L.l("{0} is an invalid @{1} argument because it is not an AMP service.",
                                               arg, Pin.class));
      }
    }

    if (arg instanceof String) {
      String address = (String) arg;

      return _manager.service(address).as(_api);
    }
    else if (arg instanceof ServiceRef) {
      ServiceRef serviceRef = (ServiceRef) arg;
      
      return serviceRef.as(_api); 
    }

    throw new IllegalArgumentException(L.l("Argument {0} is invalid because callback class {1} isn't pinned to the calling service.",
                                           arg,
                                           _api.getName()));
  }
}
