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

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
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
public class TypeRefArray extends TypeRefBase implements TypeRef, GenericArrayType
{
  private static final L10N L = new L10N(TypeRefArray.class);
  
  private TypeRef _eltRef;
  private Class<?> _rawClass;
  
  TypeRefArray(TypeRef eltRef)
  {
    Objects.requireNonNull(eltRef);
    
    _eltRef = eltRef;
    
    _rawClass = Array.newInstance(_eltRef.rawClass(), 0).getClass();
  }
  
  @Override
  public Type type()
  {
    return this;
  }

  @Override
  public Class<?> rawClass()
  {
    return _rawClass;
  }
  
  @Override
  public TypeRef superClass()
  {
    return null;
  }
  
  @Override
  public TypeRef param(String name)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public TypeRef param(int index)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public TypeRef to(Class<?> parentClass)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public TypeRef child(Type childType)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public Type getGenericComponentType()
  {
    return _eltRef.type();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _rawClass.getSimpleName() + "]";
  }
}
