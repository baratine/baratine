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

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TypeRef is an introspected type.
 */
abstract class TypeRefBase implements TypeRef
{
  static TypeRef visit(TypeVisitor visitor, Type type)
  {
    return visit(visitor, type, Collections.EMPTY_MAP);
  }
  
  static TypeRef visit(TypeVisitor visitor,
                       Type type,
                       Map<String,? extends Type> paramMap)
  {
    TypeRef value = null;
    
    if (type == null) {
      return null;
    }
    else if (type instanceof Class<?>) {
      Class<?> cl = (Class<?>) type;
      
      value = visitor.onClass((Class<?>) type, paramMap);
      
      if (value != null) {
        return value;
      }
      
      value = visit(visitor, cl.getGenericSuperclass(), paramMap);
      
      if (value != null) {
        return value;
      }
      
      for (Type superType : cl.getGenericInterfaces()) {
        value = visit(visitor, superType, paramMap);
        
        if (value != null) {
          return value;
        }
      }
    }
    else if (type instanceof ParameterizedType) {
      ParameterizedType pType = (ParameterizedType) type;
      
      return visitParameterizedType(visitor, 
                                    pType.getRawType(), 
                                    pType.getActualTypeArguments(),
                                    paramMap);
    }
    else {
      System.out.println("UNKNOWN: " + type + " " + type.getClass());
    }
    
    return null;
  }
  
  private static TypeRef visitParameterizedType(TypeVisitor visitor,
                                                Type rawType,
                                                Type []typeArguments,
                                                Map<String,? extends Type> parentMap)
  {
    if (rawType instanceof GenericDeclaration) {
      GenericDeclaration decl = (GenericDeclaration) rawType;
      
      TypeVariable<?> []vars = decl.getTypeParameters();
      
      Map<String,Type> varMap = new LinkedHashMap<>();
      
      for (int i = 0; i < vars.length; i++) {
        Type typeArg = typeArguments[i];
        
        if (typeArg instanceof TypeVariable) {
          TypeVariable<?> typeVar = (TypeVariable<?>) typeArg;
          
          Type parentType = parentMap.get(typeVar.getName());
          
          if (parentType != null) {
            varMap.put(vars[i].getName(), parentType);
          }
          else {
            varMap.put(vars[i].getName(), typeVar.getBounds()[0]);
          }
        }
        else {
          varMap.put(vars[i].getName(), typeArg);
        }
      }
      
      return visit(visitor, rawType, varMap);
    }
    else {
      System.out.println("UNKNOWN2: " + rawType);
      return null;
    }
  }
  
  class ParentVisitor implements TypeVisitor
  {
    private Class<?> _target;
    
    ParentVisitor(Class<?> target)
    {
      _target = target;
    }
    
    @Override
    public TypeRef onClass(Class<?> type, 
                           Map<String,? extends Type> typeMap)
    {
      if (type.equals(_target)) {
        return new TypeRefClass(_target, typeMap);
      }
      
      return null;
    }
    
  }
}
