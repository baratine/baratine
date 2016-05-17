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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Objects;

import com.caucho.v5.convert.bean.FieldBean;
import com.caucho.v5.convert.bean.FieldBeanFactory;

import io.baratine.config.Config;

/**
 * Stub for configuring a bean.
 */
class ConfigStub<T>
{
  private final Class<T> _type;
  private ConfigField<T,?> []_fields;
  
  ConfigStub(Class<T> type)
  {
    Objects.requireNonNull(type);
    
    if (type.isPrimitive()
        || type.isArray()
        || type.isInterface()) {
      throw new IllegalArgumentException(type.toString());
    }
    
    _type = type;
    
    String prefix = prefix(type.getSimpleName());
    
    ArrayList<ConfigField<T,?>> fields = new ArrayList<>();
    
    introspect(prefix, _type, fields);
    
    _fields = new ConfigField[fields.size()];
    fields.toArray(_fields);
  }
  
  private String prefix(String name)
  {
    StringBuilder sb = new StringBuilder();
    
    for (int i = 0; i < name.length(); i++) {
      char ch = name.charAt(i);
      
      if (Character.isUpperCase(ch)) {
        sb.append(Character.toLowerCase(ch));
      }
      else {
        sb.append(ch);
      }
      
      if (Character.isLowerCase(ch)
          && i + 1 < name.length()
          && Character.isUpperCase(name.charAt(i + 1))) {
        sb.append(".");
      }
    }
    
    return sb.toString();
  }
  
  private void introspect(String prefix, 
                          Class<?> type,
                          ArrayList<ConfigField<T,?>> fields)
  {
    for (Field field : type.getDeclaredFields()) {
      String name = field.getName();
      
      if (name.startsWith("_")) {
        name = name.substring(1);
      }
      
      FieldBean<T> fieldBean = FieldBeanFactory.get(field);
      
      ConfigField<T,?> fieldConfig = new ConfigField<>(prefix + "." + name, fieldBean);
      
      fields.add(fieldConfig);
    }
  }
  
  void inject(T bean, Config config)
  {
    for (ConfigField<T,?> field : _fields) {
      field.inject(bean, config);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _type.getSimpleName() + "]";
  }
  
  private static class ConfigField<X,T> {
    private String _name;
    private FieldBean<X> _field;
    private Class<T> _type;
    
    ConfigField(String name, FieldBean<X> field)
    {
      _name = name;
      _field = field;
      _type = (Class<T>) field.field().getType();
    }
    
    public void inject(X bean, Config config)
    {
      Objects.requireNonNull(bean);
      
      T value = (T) _field.getObject(bean);
      
      value = config.get(_name, _type, value);
      
      _field.setObject(bean, value);
    }
    
  }
}
