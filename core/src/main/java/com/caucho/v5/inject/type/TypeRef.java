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

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TypeRef is an introspected type.
 */
public interface TypeRef extends Type
{
  TypeRef to(Class<?> subclass);
  
  Class<?> rawClass();
  Type type();

  TypeRef superClass();
  
  TypeRef param(String name);
  TypeRef param(int index);

  TypeRef child(Type type);
  
  public static TypeRef of(Type type)
  {
    return of(type, Collections.EMPTY_MAP);
  }
  
  public static TypeRef of(Type type, Map<String,TypeRef> paramMap)
  {
    if (type instanceof TypeRef) {
      return (TypeRef) type;
    }
    else if (type instanceof Class<?>) {
      return new TypeRefClass((Class<?>) type);
    }
    else if (type instanceof ParameterizedType) {
      ParameterizedType pType = (ParameterizedType) type;
      
      Class<?> rawType = (Class<?>) pType.getRawType();
      LinkedHashMap<String,Type> map = new LinkedHashMap<>();
      
      for (int i = 0; i < rawType.getTypeParameters().length; i++) {
        TypeVariable<?> var = rawType.getTypeParameters()[i];
        
        Type val = pType.getActualTypeArguments()[i];
        
        val = TypeRef.of(val, paramMap);
        
        map.put(var.getName(), val);
      }
      
      return new TypeRefClass(rawType, map);
    }
    else if (type instanceof TypeVariable) {
      TypeVariable<?> var = (TypeVariable<?>) type;
      
      String name = var.getName();
      
      TypeRef val = paramMap.get(name);
      
      if (val != null) {
        return val;
      }
      
      TypeRef lowerBound = of(var.getBounds()[0]);
      
      //Thread.dumpStack();
      
      return lowerBound;
    }
    else if (type instanceof WildcardType) {
      WildcardType var = (WildcardType) type;
      
      // String name = var.getName();
      
      Type[] lowerBounds = var.getLowerBounds();
      
      if (lowerBounds.length > 0) {
        TypeRef lowerBound = of(var.getLowerBounds()[0]);
      
        return lowerBound;
      }
      else {
        return of(Object.class);
      }
    }
    else if (type instanceof GenericArrayType) {
      GenericArrayType array = (GenericArrayType) type;
      
      TypeRef eltRef = of(array.getGenericComponentType(), paramMap);
      
      return new TypeRefArray(eltRef);
    }
    else {
      System.out.println("TYPE: " + type + " " + type.getClass().getName());
      throw new UnsupportedOperationException(type + " " + type.getClass().getName());
    }
  }
  
  public static TypeRef of(Class<?> rawType, Class<?> ...param)
  {
    LinkedHashMap<String,Type> map = new LinkedHashMap<>();
    
    for (int i = 0; i < rawType.getTypeParameters().length; i++) {
      map.put(rawType.getTypeParameters()[i].getName(),
              param[i]);
    }
    
    Type type = new ParameterizedTypeImpl(rawType, param);
    
    return new TypeRefClass(rawType, map, type);
  }
  
  public static TypeRef of(Class<?> rawType, TypeRef ...param)
  {
    LinkedHashMap<String,Type> map = new LinkedHashMap<>();
    
    for (int i = 0; i < rawType.getTypeParameters().length; i++) {
      map.put(rawType.getTypeParameters()[i].getName(),
              param[i]);
    }
    
    Type type = new ParameterizedTypeImpl(rawType, param);
    
    return new TypeRefClass(rawType, map, type);
  }
}
