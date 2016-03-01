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

package com.caucho.v5.config.reflect;

import java.lang.reflect.Type;
import java.util.HashMap;

import com.caucho.v5.loader.EnvironmentLocal;
import com.caucho.v5.util.LruCache;
import com.caucho.v5.util.ModulePrivate;

/**
 * type matching the web bean
 */
@ModulePrivate
public class BaseTypeFactory
{
  private static EnvironmentLocal<BaseTypeFactory> _factory
    = new EnvironmentLocal<>();
  
  private LruCache<Type,BaseType> _sourceCache
    = new LruCache<Type,BaseType>(128);
  
  private LruCache<Type,BaseType> _targetCache
    = new LruCache<Type,BaseType>(128);

  private LruCache<Class<?>,BaseType> _classCache
    = new LruCache<Class<?>,BaseType>(128);

  public BaseTypeFactory()
  {
    if (_factory.getLevel() == null) {
      _factory.set(this);
    }
  }
  
  public BaseType createForSource(Type type)
  {
    if (type instanceof BaseType) {
      return (BaseType) type;
    }
    
    BaseType baseType = _sourceCache.get(type);

    if (baseType == null) {
      baseType = BaseType.createForSource(type,
                                          new HashMap<String,BaseType>(),
                                          null);

      if (baseType == null)
        throw new NullPointerException("unsupported BaseType: " + type + " " + type.getClass());

      _sourceCache.put(type, baseType);
    }

    return baseType;
  }

  public BaseType createForTarget(Type type)
  {
    if (type instanceof BaseType)
      return (BaseType) type;
    
    BaseType baseType = _targetCache.get(type);

    if (baseType == null) {
      baseType = BaseType.createForTarget(type,
                                          new HashMap<String,BaseType>(),
                                          null);

      if (baseType == null)
        throw new NullPointerException("unsupported BaseType: " + type + " " + type.getClass());

      _targetCache.put(type, baseType);
    }

    return baseType;
  }

  public static BaseTypeFactory current()
  {
    synchronized (_factory) {
      BaseTypeFactory factory = _factory.getLevel();
    
      if (factory == null) {
        factory = new BaseTypeFactory();
      
        _factory.set(factory);
      }
    
      return factory;
    }
  }
}
