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

package io.baratine.inject;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Corresponds to an injection point, like a field.
 */
class InjectionPointImpl<T> implements InjectionPoint<T>
{
  private final Key<T> _key;
  private final Type _type;
  private final String _name;
  private final Annotation []_annotations;
  private final Class<?> _declaringClass;
  
  InjectionPointImpl(Key<T> key,
                     Type type,
                     String name,
                     Annotation []annotations,
                     Class<?> declaringClass)
  {
    Objects.requireNonNull(key);
    Objects.requireNonNull(type);
    Objects.requireNonNull(name);
    Objects.requireNonNull(annotations);
    Objects.requireNonNull(declaringClass);

    _key = key;
    _type = type;
    _name = name;
    _annotations = annotations;
    _declaringClass = declaringClass;
  }
  
  @Override
  public Key<T> key()
  {
    return _key;
  }
  
  @Override
  public Type type()
  {
    return _type;
  }
  
  @Override
  public String name()
  {
    return _name;
  }
  
  @Override
  public Annotation []annotations()
  {
    return _annotations;
  }
  
  @Override
  public Class<?> declaringClass()
  {
    return _declaringClass;
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
             + "[" + _key
             + "," + name()
             + "," + declaringClass().getSimpleName()
             + "]");
  }
}

