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

package com.caucho.v5.convert.bean;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.function.Function;

/**
 * factory for field bean setters.
 */
public class FieldBeanFactory
{
  private static final HashMap<Class<?>,Function<Field,FieldBean<?>>> _typeMap;
  
  @SuppressWarnings("unchecked")
  public static <T> FieldBean<T> get(Field field)
  {
    Class<T> type = (Class<T>) field.getType();
    
    Function<Field, FieldBean<?>> fun = _typeMap.get(type);
    
    if (fun != null) {
      return (FieldBean<T>) fun.apply(field);
    }
    else {
      return (FieldObject<T,?>) new FieldObject<>(field);
    }
  }
  
  static {
    _typeMap = new HashMap<>();
    
    _typeMap.put(boolean.class, FieldBoolean::new);
    _typeMap.put(Boolean.class, FieldBoolean::new);
    
    _typeMap.put(char.class, FieldChar::new);
    _typeMap.put(Character.class, FieldChar::new);
    
    _typeMap.put(byte.class, FieldByte::new);
    _typeMap.put(Byte.class, FieldByte::new);
    
    _typeMap.put(short.class, FieldShort::new);
    _typeMap.put(Short.class, FieldShort::new);
    
    _typeMap.put(int.class, FieldInt::new);
    _typeMap.put(Integer.class, FieldInt::new);
    
    _typeMap.put(long.class, FieldLong::new);
    _typeMap.put(Long.class, FieldLong::new);
    
    _typeMap.put(float.class, FieldFloat::new);
    _typeMap.put(Float.class, FieldFloat::new);
    
    _typeMap.put(double.class, FieldDouble::new);
    _typeMap.put(Double.class, FieldDouble::new);
    
    _typeMap.put(String.class, FieldString::new);
  }
}
