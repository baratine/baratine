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

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;

/**
 * Implementation of generic types.
 */
public class GenericArrayTypeImpl extends TypeImpl
  implements GenericArrayType
{
  private final Class<?> _cl;
  private final TypeImpl _eltType;
  
  public GenericArrayTypeImpl(TypeImpl eltType)
  {
    _eltType = eltType;
    
    Object v = Array.newInstance(eltType.getTypeClass(), 0);
      
    _cl = v.getClass();
  }
  
  @Override
  public Class<?> getTypeClass()
  {
    return _cl;
  }

  @Override
  public Type getGenericComponentType()
  {
    return _eltType;
  }

  @Override
  public TypeImpl getArg(int index, TypeFactoryReflect factory)
  {
    return _eltType;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getTypeClass() + "]"; 
  }
}
