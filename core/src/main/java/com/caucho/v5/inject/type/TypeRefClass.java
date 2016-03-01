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

package com.caucho.v5.inject.type;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.caucho.v5.util.L10N;

/**
 * TypeRef is an introspected type.
 */
public class TypeRefClass extends TypeRefBase implements TypeRef
{
  private static final L10N L = new L10N(TypeRefClass.class);
  
  private Class<?> _rawClass;
  private Map<String,TypeRef> _typeRefMap;

  private Type _type;
  
  TypeRefClass(Class<?> type)
  {
    Objects.requireNonNull(type);
    _rawClass = type;
    _type = type;

    TypeVariable<?>[] params = type.getTypeParameters();
    
    if (params == null || params.length == 0) {
      _typeRefMap = Collections.EMPTY_MAP;
    }
    else {
      _typeRefMap = new LinkedHashMap<>();
      
      for (TypeVariable<?> param : params) {
        _typeRefMap.put(param.getName(), TypeRef.of(Object.class));
      }
    }
  }
  
  TypeRefClass(Class<?> type, Map<String,Type> typeMap)
  {
    Objects.requireNonNull(type);
    _rawClass = type;
    _type = type;
    
    LinkedHashMap<String,TypeRef> typeRefMap = new LinkedHashMap<>();
    
    for (Map.Entry<String, Type> entry : typeMap.entrySet()) {
      String key = entry.getKey();
      Type entryType = entry.getValue();
      typeRefMap.put(key, TypeRef.of(entryType));
    }
    
    _typeRefMap = typeRefMap;
  }
  
  TypeRefClass(Class<?> rawClass,
               Map<String,Type> typeMap,
               Type type)
  {
    Objects.requireNonNull(rawClass);
    _rawClass = rawClass;
    _type = type;
    
    LinkedHashMap<String,TypeRef> typeRefMap = new LinkedHashMap<>();
    
    for (Map.Entry<String, Type> entry : typeMap.entrySet()) {
      String key = entry.getKey();
      Type entryType = entry.getValue();

      typeRefMap.put(key, TypeRef.of(entryType));
    }
    
    _typeRefMap = typeRefMap;
  }
  
  @Override
  public Type type()
  {
    return _type;
  }

  @Override
  public Class<?> rawClass()
  {
    return _rawClass;
  }
  
  @Override
  public TypeRef superClass()
  {
    Type superClass = rawClass().getGenericSuperclass();
    
    if (superClass != null) {
      return TypeRef.of(superClass);
    }
    else {
      return null;
    }
  }
  
  @Override
  public TypeRef param(String name)
  {
    return _typeRefMap.get(name);
  }

  @Override
  public TypeRef param(int index)
  {
    for (TypeRef param : _typeRefMap.values()) {
      if (index-- == 0) {
        return param;
      }
    }
    
    return null;
  }
  
  @Override
  public TypeRef to(Class<?> parentClass)
  {
    Objects.requireNonNull(parentClass);
    
    if (! parentClass.isAssignableFrom(_rawClass)) {
      throw new IllegalArgumentException(L.l("{0} is not a parent of {1}",
                                             parentClass.getName(),
                                             _rawClass.getName()));
    }
    
    if (parentClass.equals(_rawClass)) {
      return this;
    }
    
    TypeVisitor visitor = new ParentVisitor(parentClass); 
    
    TypeRef typeRef = visit(visitor, _rawClass);
    
    return typeRef;
  }

  @Override
  public String toString()
  {
    if (_typeRefMap.size() == 0) {
      return getClass().getSimpleName() + "[" + _rawClass.getSimpleName() + "]";
    }
    else {
      return getClass().getSimpleName() + "[" + _rawClass.getSimpleName() + "," + _typeRefMap + "]";
    }
  }
}
