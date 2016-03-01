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

package com.caucho.v5.convert;

import java.util.HashMap;
import java.util.Map;

import io.baratine.convert.Convert;
import io.baratine.convert.ConvertManager;
import io.baratine.convert.ConvertManagerType;

/**
 * Default converter.
 */
public class ConvertManagerImpl implements ConvertManager
{
  private static final Map<Class<?>,ConvertManagerType<?>> _defaultConverterMap
    = new HashMap<>();
  
  private static final Convert<?,?> IDENTITY = new ConverterIdentity<>();
  
  private final Map<Class<?>,ConvertManagerType<?>> _converterMap
    = new HashMap<>();
  
  public ConvertManagerImpl()
  {
    _converterMap.putAll(_defaultConverterMap);
  }
  
  public static Iterable<ConvertManagerType<?>> convertManagers()
  {
    return _defaultConverterMap.values();
  }

  @Override
  public <S, T> Convert<S, T> converter(Class<S> source, Class<T> target)
  {
    ConvertManagerType<S> convertType
      = (ConvertManagerType<S>) _defaultConverterMap.get(source);
    
    if (convertType != null) {
      Convert<S,T> converter = convertType.converter(target);
      
      if (converter != null) {
        return converter;
      }
      else {
        return (Convert<S,T>) IDENTITY;
      }
    }
    else {
      return (Convert<S,T>) IDENTITY;
    }
  }
  
  static final class ConverterIdentity<T> implements Convert<T,T>
  {
    @Override
    public T convert(T source)
    {
      return source;
    }
  }
  
  static {
    _defaultConverterMap.put(String.class, ConvertStringDefault.get());
  }
}
