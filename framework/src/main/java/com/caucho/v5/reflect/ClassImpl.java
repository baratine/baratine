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

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

import com.caucho.v5.reflect.TypeFactoryReflect.TypeKey;

/**
 * Implementation of generic types.
 */
public class ClassImpl<T> extends TypeImpl
{
  private final Class<T> _cl;
  
  private final TypeImpl _superclass;
  
  ClassImpl(Class<T> cl,
            TypeFactoryReflect typeFactory,
            TypeKey key)
  {
    _cl = cl;
    
    typeFactory.addType(key, this);
    
    Type superClass = cl.getGenericSuperclass();
    
    if (superClass != null && ! Object.class.equals(superClass)) {
      _superclass = typeFactory.getType(superClass);
    }
    else {
      _superclass = null;
    }
  }
  
  @Override
  public Class<T> getTypeClass()
  {
    return _cl;
  }
  
  public TypeImpl getSuperclass()
  {
    return _superclass;
  }
  
  public TypeVariable<Class<T>> []getVariables()
  {
    return _cl.getTypeParameters();
  }

  @Override
  public int findArg(String name)
  {
    TypeVariable<?> []vars = getVariables();
    
    if (vars == null) {
      return -1;
    }
    
    for (int i = 0; i < vars.length; i++) {
      if (vars[i].getName().equals(name)) {
        return i;
      }
    }
      
    return -1;
  }

  @Override
  public String toString()
  {
    return String.valueOf(getTypeClass());
  }
}
