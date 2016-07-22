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

package io.baratine.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.baratine.web.View.ViewBuilder;

class ViewImpl implements ViewBuilder, View
{
  private String _view;
  
  private Map<String,Object> _map;
  private Object _value;
  
  ViewImpl(String view)
  {
    Objects.requireNonNull(view);
    
    _view = view;
  }
  
  @Override
  public String name()
  {
    return _view;
  }
  
  @Override
  public Map<String,Object> map()
  {
    if (_map == null) {
      _map = new LinkedHashMap<>();
    }
    
    return _map;
  }
  
  @Override
  public Object get(String key)
  {
    Map<String,Object> map = _map;
    
    if (map == null) {
      return null;
    }
    
    return map.get(key);
  }
  
  @Override
  public <X> X get(Class<X> type)
  {
    Map<String,Object> map = _map;
    
    if (map == null) {
      return null;
    }
    
    return (X) map.get(type.getSimpleName());
  }
  
  @Override
  public Object get()
  {
    return _value;
  }
  
  @Override
  public ViewImpl set(Object value)
  {
    _value = value;
    
    return this;
  }

  @Override
  public ViewBuilder add(String key, Object value)
  {
    Objects.requireNonNull(key);
    Objects.requireNonNull(value);
    
    map().put(key, value);
    
    return this;
  }

  @Override
  public <X> ViewBuilder add(X value)
  {
    Objects.requireNonNull(value);
    
    String name = value.getClass().getSimpleName();
    
    _map.put(name, value);

    return this;
  }
}
