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

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.caucho.v5.amp.ServicesAmp;

/**
 * Creates class stubs for service implementation beans..
 */
public class StubClassFactory
{
  private final ServicesAmp _services;
  
  private ConcurrentHashMap<StubKey,StubClass> _stubMap
    = new ConcurrentHashMap<>();
      
  public StubClassFactory(ServicesAmp services)
  {
    Objects.requireNonNull(services);
    
    _services = services;
  }

  public StubClass stubClass(Class<?> type,
                        Class<?> api)
  {
    Objects.requireNonNull(type);
    Objects.requireNonNull(api);
    
    StubKey key = new StubKey(type, api);
    
    StubClass stub = _stubMap.get(key);
    
    if (stub == null) {
      stub = new StubClass(_services, type, api);
      stub.introspect();
    
      _stubMap.putIfAbsent(key, stub);
    
      stub = _stubMap.get(key);
    }
    
    return stub;
  }
  
  private static class StubKey
  {
    private Class<?> _type;
    private Class<?> _api;
    
    StubKey(Class<?> type, Class<?> api)
    {
      Objects.requireNonNull(type);
      Objects.requireNonNull(api);
      
      _type = type;
      _api = api;
    }
    
    @Override
    public int hashCode()
    {
      return 65521 * _type.hashCode() + _api.hashCode();
    }
    
    public boolean equals(Object o)
    {
      if (! (o instanceof StubKey)) {
        return false;
      }
      
      StubKey key = (StubKey) o;
      
      return _type.equals(key._type) && _api.equals(key._api);
    }
  }
}
