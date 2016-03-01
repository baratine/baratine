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
import java.util.Set;

import com.caucho.v5.util.ModulePrivate;

/**
 * class type matching
 */
@ModulePrivate
public class BoxType extends ClassType
{
  private Class<?> _boxType;

  public BoxType(Class<?> type, Class<?> boxType)
  {
    super(type);
    
    _boxType = boxType;
  }
  
  @Override
  public Class<?> getRawClass()
  {
    return _boxType;
  }

  @Override
  public boolean isParamAssignableFrom(BaseType type)
  {
    if (_boxType.equals(type.getRawClass()))
      return true;
    else if (type.isWildcard())
      return true;
    else
      return false;
  }
    
  @Override
  public boolean isAssignableFrom(BaseType type)
  {
    if (! _boxType.isAssignableFrom(type.getRawClass()))
      return false;
    else if (type.getParameters().length > 0) {
      for (BaseType param : type.getParameters()) {
        if (! OBJECT_TYPE.isParamAssignableFrom(param))
          return false;
      }

      return true;
    }
    else
      return true;
  }

  @Override
  public boolean isPrimitive()
  {
    return true;
  }
  
  @Override
  public void fillTypeClosure(BaseTypeFactory typeFactory, Set<Type> typeSet)
  {
    typeSet.add(toType());
    typeSet.add(Object.class);
  }

  @Override
  public int hashCode()
  {
    return _boxType.hashCode();
  }

  @Override
  public boolean equals(Object o)
  {
    if (o == this)
      return true;
    else if (! (o instanceof BoxType))
      return false;

    BoxType type = (BoxType) o;

    return _boxType.equals(type._boxType);
  }

  @Override
  public String toString()
  {
    return super.getRawClass().getName();
  }
}
