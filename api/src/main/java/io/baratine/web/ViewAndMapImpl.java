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

import io.baratine.web.Views.ViewAndMapBuilder;

class ViewAndMapImpl implements ViewAndMapBuilder, ViewAndMap
{
  private String _view;
  private LinkedHashMap<String,Object> _map = new LinkedHashMap<>();
  
  ViewAndMapImpl(String view)
  {
    Objects.requireNonNull(view);
    
    _view = view;
  }
  
  @Override
  public String view()
  {
    return _view;
  }
  
  @Override
  public Map<String,Object> map()
  {
    return _map;
  }
  
  @Override
  public Object get(String key)
  {
    return map().get(key);
  }

  @Override
  public ViewAndMapBuilder add(String key, Object value)
  {
    Objects.requireNonNull(key);
    Objects.requireNonNull(value);
    
    _map.put(key, value);
    
    return this;
  }

  @Override
  public ViewAndMapBuilder add(Object value)
  {
    Objects.requireNonNull(value);
    
    String name = value.getClass().getSimpleName();
    
    char ch = name.charAt(0);
    
    if (Character.isUpperCase(ch)) {
      name = new StringBuilder().append(Character.toLowerCase(ch))
                                .append(name.substring(1))
                                .toString();
    }
    
    _map.put(name, value);

    return this;
  }
}
