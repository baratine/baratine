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
 * 
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.config.impl;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

import com.caucho.v5.convert.ConvertStringDefault;
import com.caucho.v5.util.L10N;

import io.baratine.config.Config;
import io.baratine.convert.ConvertFrom;

/**
 * ConfigEnv is the configuration environment, which contains a
 * read-only properties map.
 */
public class ConfigImpl extends AbstractMap<String,String>
  implements Config
{
  private static final L10N L = new L10N(ConfigImpl.class);
  private static final Logger log
    = Logger.getLogger(ConfigImpl.class.getName());
  
  private final Map<String,String> _map;
  private final ConvertFrom<String> _converter;
  
  ConfigImpl(Map<String,String> map,
             ConvertFrom<String> converter)
  {
    Objects.requireNonNull(map);
    Objects.requireNonNull(converter);
    
    _map = Collections.unmodifiableMap(new TreeMap<>(map));
    _converter = converter;
  }
  
  ConfigImpl(Properties properties)
  {
    HashMap<String,String> map = new HashMap<>();
    
    for (Map.Entry<Object,Object> entry : properties.entrySet()) {
      Object key = entry.getKey();
      Object value = entry.getValue();
      
      if (key instanceof String && value instanceof String) {
        map.put((String) key, (String) value); 
      }
    }
    
    _map = map;
    _converter = ConvertStringDefault.get();
  }
  
  /**
   * Return the configuration value for the given key.
   */
  @Override
  public String get(Object key)
  {
    return _map.get(key);
  }
  
  /**
   * Configuration value with a default value.
   */
  @Override
  public String get(String key, String defaultValue)
  {
    String value = _map.get(key);
    
    if (value != null) {
      return value;
    }
    else {
      return defaultValue;
    }
  }
  
  /**
   * Gets a configuration value converted to a type.
   */
  @SuppressWarnings("unchecked")
  public <T> T get(String key, Class<T> type, T defaultValue)
  {
    Objects.requireNonNull(key);
    Objects.requireNonNull(type);
    
    String value = _map.get(key);
    
    if (value == null) {
      return defaultValue;
    }
    
    if (type.equals(String.class)) {
      return (T) value;
    }
    
    T valueType = _converter.convert(type, value);
    
    if (valueType != null) {
      return valueType;
    }
    else {
      log.warning(L.l("Unexpected type for key={0} type={1} value={2}",
                      key, type, value));
      /*
      throw new ConfigException(L.l("Unexpected type for key={0} type={1} value={2}",
                                    key, type, value));
                                    */
      return defaultValue;
    }
  }
  
  /**
   * Gets a configuration value converted to a type.
   */
  @SuppressWarnings("unchecked")
  public <T> T get(String key, Class<T> type, String defaultValue)
  {
    Objects.requireNonNull(key);
    Objects.requireNonNull(type);
    
    String value = _map.get(key);
    
    if (value == null) {
      value = defaultValue;
    }
    
    if (type.equals(String.class)) {
      return (T) value;
    }
    
    T valueType = _converter.convert(type, value);
    
    return valueType;
  }
  
  @Override
  public Set<Map.Entry<String, String>> entrySet()
  {
    return _map.entrySet();
  }
  
  @Override
  public ConfigBuilder subEnv()
  {
    return new ConfigBuilderImpl(_map);
  }
  
  @Override
  public void clear()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
