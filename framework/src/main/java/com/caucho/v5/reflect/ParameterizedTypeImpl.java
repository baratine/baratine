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

import java.lang.reflect.ParameterizedType;

/**
 * Implementation of generic types.
 */
public class ParameterizedTypeImpl extends TypeImpl implements ParameterizedType
{
  private final TypeImpl _rawType;
  private final TypeImpl []_typeArguments;
  
  public ParameterizedTypeImpl(TypeImpl rawType,
                               TypeImpl []typeArguments)
  {
    _rawType = rawType;
    _typeArguments = typeArguments;
  }
  
  @Override
  public Class<?> getTypeClass()
  {
    return _rawType.getTypeClass();
  }
  
  @Override
  public TypeImpl getRawType()
  {
    return _rawType;
  }
  
  @Override
  public TypeImpl []getActualTypeArguments()
  {
    return _typeArguments;
  }

  @Override
  public TypeImpl getArg(String name, TypeFactoryReflect factory)
  {
    int index = _rawType.findArg(name);
    
    if (index >= 0) {
      return getActualTypeArguments()[index];
    }
    else {
      return factory.getType(Object.class);
    }
  }

  @Override
  public TypeImpl getArg(int index, TypeFactoryReflect factory)
  {
    return getActualTypeArguments()[index];
  }

  @Override
  public TypeImpl getOwnerType()
  {
    return null;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getTypeClass() + "]"; 
  }
}
