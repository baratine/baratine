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

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.Set;

import com.caucho.v5.util.ModulePrivate;

/**
 * class type matching
 */
@ModulePrivate
public class ArrayType extends BaseType implements GenericArrayType
{
  private BaseType _componentType;
  private Class<?> _rawType;

  public ArrayType(BaseType componentType, Class<?> rawType)
  {
    _componentType = componentType;
    _rawType = rawType;
  }
  
  @Override
  public Class<?> getRawClass()
  {
    return _rawType;
  }

  @Override
  public Type getGenericComponentType()
  {
    return _componentType.toType();
  }

  @Override
  public Type toType()
  {
    return this;
  }
  
  @Override
  public boolean isParamAssignableFrom(BaseType type)
  {
    if (type instanceof ArrayType) {
      ArrayType aType = (ArrayType) type;

      return getGenericComponentType().equals(aType.getGenericComponentType());
    }
    else
      return false;
  }

  @Override
  protected void fillTypeClosure(BaseTypeFactory typeFactory, Set<Type> typeSet)
  {
    typeSet.add(toType());
    typeSet.add(Object.class);
  }
  
  @Override
  public boolean isAssignableFrom(BaseType type)
  {
    return equals(type);
  }

  @Override
  public int hashCode()
  {
    return 17 + 37 * getGenericComponentType().hashCode();
  }

  @Override
  public boolean equals(Object o)
  {
    if (o == this)
      return true;
    else if (o instanceof GenericArrayType) {
      GenericArrayType type = (GenericArrayType) o;

      return getGenericComponentType().equals(type.getGenericComponentType());
    }
    else
      return false;
  }

  @Override
  public String getSimpleName()
  {
    return _componentType.getSimpleName() + "[]";
  }

  @Override
  public String toString()
  {
    return _componentType + "[]";
  }
}
