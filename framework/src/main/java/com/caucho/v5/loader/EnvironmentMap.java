/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.loader;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Creates a ClassLoader-dependent hashmap.
 */
public class EnvironmentMap extends HashMap {
  private EnvironmentLocal<HashMap> _envMap = new EnvironmentLocal<HashMap>();

  private HashMap _global;

  /**
   * Creates a new environment map.
   */
  public EnvironmentMap(HashMap global)
  {
    _global = global;
  }
  
  public EnvironmentMap()
  {
    this(new HashMap());
  }

  public HashMap getGlobal()
  {
    return _global;
  }

  public HashMap setGlobal(HashMap global)
  {
    HashMap old = _global;
    
    _global = global;

    return old;
  }

  public int size()
  {
    return getEnvironmentMap().size();
  }

  public boolean isEmpty()
  {
    return getEnvironmentMap().isEmpty();
  }

  public boolean containsValue(Object value)
  {
    return getEnvironmentMap().containsValue(value);
  }

  public boolean containsKey(Object value)
  {
    return getEnvironmentMap().containsKey(value);
  }

  public Object get(Object key)
  {
    Map map = getEnvironmentMap();

    Object value = map.get(key);

    if (value != null)
      return value;
    else
      return _global.get(key);
  }

  public Object put(Object key, Object value)
  {
    return getPutEnvironmentMap().put(key, value);
  }

  public Object remove(Object key)
  {
    return getPutEnvironmentMap().remove(key);
  }

  public void putAll(Map map)
  {
    getPutEnvironmentMap().putAll(map);
  }

  public void clear()
  {
    getPutEnvironmentMap().clear();
  }

  public Object clone()
  {
    return getEnvironmentMap().clone();
  }

  public String toString()
  {
    return getEnvironmentMap().toString();
  }

  public Set keySet()
  {
    return getPutEnvironmentMap().keySet();
  }

  public Set entrySet()
  {
    return getPutEnvironmentMap().entrySet();
  }

  public Collection values()
  {
    return getPutEnvironmentMap().values();
  }

  public boolean equals(Object o)
  {
    return getEnvironmentMap().equals(o);
  }

  public int hashCode()
  {
    return getEnvironmentMap().hashCode();
  }

  private HashMap getEnvironmentMap()
  {
    HashMap map = _envMap.get();

    if (map != null)
      return map;
    else
      return _global;
  }

  private synchronized HashMap getPutEnvironmentMap()
  {
    HashMap map = _envMap.getLevel();
    
    if (map == null) {
      map = new HashMap();
      HashMap parentMap = _envMap.get();
      if (parentMap == null)
        parentMap = _global;

      map.putAll(parentMap);

      _envMap.set(map);
      
      return map;
    }
    else
      return map;
  }
}
