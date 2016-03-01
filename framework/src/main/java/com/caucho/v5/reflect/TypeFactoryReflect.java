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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.reflect;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of generic types.
 */
public class TypeFactoryReflect
{
  private ConcurrentHashMap<TypeKey,TypeImpl> _typeMap
    = new ConcurrentHashMap<>();
    
  public TypeImpl getType(Type type)
  {
    return getType(type, null);
  }
  
  public TypeImpl getType(Type type, TypeImpl contextType)
  {
    TypeKey key = new TypeKey(type, contextType);
    
    TypeImpl typeImpl = _typeMap.get(key);
    
    if (typeImpl != null) {
      return typeImpl;
    }

    if (type instanceof TypeImpl) {
      typeImpl = (TypeImpl) type;
    }
    else if (type instanceof Class) {
      typeImpl = new ClassImpl((Class) type, this, key);
    }
    else if (type instanceof ParameterizedType) {
      ParameterizedType pType = (ParameterizedType) type;
      
      TypeImpl rawType = getType(pType.getRawType());
      
      Type []pArgs = pType.getActualTypeArguments();
      TypeImpl []param = new TypeImpl[pArgs.length];
      
      for (int i = 0; i < pArgs.length; i++) {
        param[i] = getType(pArgs[i], contextType);
      }
      
      return new ParameterizedTypeImpl(rawType, param);
    }
    else if (type instanceof GenericArrayType) {
      GenericArrayType pType = (GenericArrayType) type;
      
      Type componentType = pType.getGenericComponentType();
      
      TypeImpl eltType = getType(componentType, contextType);
      
      return new GenericArrayTypeImpl(eltType);
    }
    else if (type instanceof TypeVariable) {
      TypeVariable var = (TypeVariable) type;
      
      if (contextType != null) {
        return contextType.getArg(var.getName(), this);
      }
      else {
        Type []bounds = var.getBounds();
        
        // for <T extends List>, bounds[0] is List.class
        // for <T>, bounds[0] is Object.class
        Type parentType = bounds[0];
        
        return getType(parentType);
      }
    }
    else if (type instanceof WildcardType) {
      return getType(Object.class);
    }
    else {
      throw new UnsupportedOperationException(type.getClass().getName());
    }
      
    _typeMap.putIfAbsent(key, typeImpl);
    
    return _typeMap.get(key);
  }
  
  void addType(TypeKey key, TypeImpl contextType)
  {
    _typeMap.putIfAbsent(key, contextType);
  }
  
  static class TypeKey {
    private Type _type;
    private TypeImpl _context;
    
    TypeKey(Type type, TypeImpl context)
    {
      _type = type;
      _context = context;
    }
    
    @Override
    public int hashCode()
    {
      int hash = _type.hashCode();
      
      if (_context != null) {
        hash = 65521 * hash + _context.hashCode();
      }
      
      return hash;
    }
    
    @Override
    public boolean equals(Object o)
    {
      if (! (o instanceof TypeKey)) {
        return false;
      }
      
      TypeKey key = (TypeKey) o;
      
      if (! _type.equals(key._type)) {
        return false;
      }
      
      if (_context == null && key._context == null) {
        return true;
      }
      else if (_context == null || key._context == null) {
        return false;
      }
      
      return _context.equals(key._context);
    }
  }
}
