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

package com.caucho.v5.loader;

import java.util.Collection;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Set;

/**
 * Creates a ClassLoader-dependent properties table.
 * The value of the EnvironmentLocal variable depends on the
 * context ClassLoader.
 */
public class EnvironmentProperties extends Properties {
  private static Properties _origSystemProperties;
  private static Properties _envSystemProperties;
  
  private transient EnvironmentLocal<Properties> _envProps
    = new EnvironmentLocal<Properties>();

  private Properties _global;
  
  public EnvironmentProperties(Properties global)
  {
    _global = global;
  }
  
  public EnvironmentProperties()
  {
    this(new Properties());
  }

  public static void enableEnvironmentSystemProperties(boolean isEnable)
  {
    if (_origSystemProperties == null) {
      _origSystemProperties = System.getProperties();
      _envSystemProperties = new EnvironmentProperties(_origSystemProperties);
    }

    if (isEnable)
      System.setProperties(_envSystemProperties);
    else
      System.setProperties(_origSystemProperties);
  }

  public Properties getGlobalProperties()
  {
    return _global;
  }

  public void setGlobalProperties(Properties global)
  {
    _global = global;
  }

  @Override
  public int size()
  {
    return getEnvironmentProperties().size();
  }

  @Override
  public boolean isEmpty()
  {
    return getEnvironmentProperties().isEmpty();
  }

  public Enumeration keys()
  {
    return getEnvironmentProperties().keys();
  }

  public Enumeration elements()
  {
    return getEnvironmentProperties().elements();
  }

  @Override
  public boolean contains(Object value)
  {
    return getEnvironmentProperties().contains(value);
  }

  @Override
  public boolean containsValue(Object value)
  {
    return getEnvironmentProperties().containsValue(value);
  }

  @Override
  public boolean containsKey(Object value)
  {
    return getEnvironmentProperties().containsKey(value);
  }

  public Object get(String key)
  {
    Properties props = getEnvironmentProperties();

    String value = props.getProperty(key);

    if (value != null)
      return value;
    else
      return _global.get(key);
  }

  @Override
  public Object get(Object key)
  {
    return get((String) key);
  }

  @Override
  public String getProperty(String key)
  {
    Properties props = getEnvironmentProperties();

    String value = props.getProperty(key);

    if (value != null)
      return value;
    else
      return _global.getProperty(key);
  }

  @Override
  public String getProperty(String key, String defaultValue)
  {
    Properties props = getEnvironmentProperties();

    String value = props.getProperty(key);

    if (value != null)
      return value;
    else
      return _global.getProperty(key, defaultValue);
  }

  public Enumeration propertyNames()
  {
    return getEnvironmentProperties().propertyNames();
  }

  public String put(String key, String value)
  {
    return (String) getPutEnvironmentProperties().put(key, value);
  }

  public Object setProperty(String key, String value)
  {
    return put(key, value);
  }

  public Object put(Object key, Object value)
  {
    return getPutEnvironmentProperties().put(key, value);
  }

  public String remove(Object key)
  {
    return (String) getPutEnvironmentProperties().remove(key);
  }

  /*
  public void putAll(Map<K2,V2> map)
  {
    getPutEnvironmentProperties().putAll(map);
  }
  */

  public void clear()
  {
    getPutEnvironmentProperties().clear();
  }

  public Object clone()
  {
    return getEnvironmentProperties().clone();
  }

  public String toString()
  {
    return getEnvironmentProperties().toString();
  }

  public Set keySet()
  {
    return getPutEnvironmentProperties().keySet();
  }

  public Set entrySet()
  {
    return getPutEnvironmentProperties().entrySet();
  }

  public Collection values()
  {
    return getPutEnvironmentProperties().values();
  }

  public boolean equals(Object o)
  {
    return getEnvironmentProperties().equals(o);
  }

  public int hashCode()
  {
    return getEnvironmentProperties().hashCode();
  }

  private Properties getEnvironmentProperties()
  {
    Properties props = _envProps.get();

    if (props != null)
      return props;
    else
      return _global;
  }

  private synchronized Properties getPutEnvironmentProperties()
  {
    Properties props = _envProps.getLevel();
    
    if (props == null) {
      props = new Properties();
      Properties parentProps = _envProps.get();
      if (parentProps == null)
        parentProps = _global;

      props.putAll(parentProps);

      _envProps.set(props);
      
      return props;
    }
    else
      return props;
  }

  public Object writeReplace() throws java.io.ObjectStreamException
  {
    return getEnvironmentProperties();
  }
}
