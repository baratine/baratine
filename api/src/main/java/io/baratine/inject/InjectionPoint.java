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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;

/**
 * Corresponds to an injection point, like a field.
 */
public interface InjectionPoint<T>
{
  Key<T> key();
  
  Type type();
  
  String name();
  
  Annotation []annotations();
  
  Class<?> declaringClass();
  
  static <T> InjectionPoint<T> of(Class<T> type)
  {
    return new InjectionPointImpl<>(Key.of(type),
        type,
        type.getSimpleName(),
        type.getAnnotations(),
        type);
  }
  
  static <T> InjectionPoint<T> of(Key<T> key)
  {
    return new InjectionPointImpl<>(key,
        key.type(),
        key.rawClass().getSimpleName(),
        key.annotations(),
        key.rawClass());
  }
  
  static <T> InjectionPoint<T> of(Method m)
  {
    return new InjectionPointImpl<>(Key.of(m),
        m.getGenericReturnType(),
        m.getName(),
        m.getAnnotations(),
        m.getDeclaringClass());
  }
  
  static <T> InjectionPoint<T> of(Constructor<?> c)
  {
    return new InjectionPointImpl<>(Key.of(c),
        c.getDeclaringClass(),
        c.getDeclaringClass().getSimpleName(),
        c.getAnnotations(),
        c.getDeclaringClass());
  }
  
  static <T> InjectionPoint<T> of(Field f)
  {
    return new InjectionPointImpl<>(Key.of(f),
        f.getGenericType(),
        f.getName(),
        f.getAnnotations(),
        f.getDeclaringClass());
  }
  
  static <T> InjectionPoint<T> of(Parameter p)
  {
    return new InjectionPointImpl<>((Key<T>) Key.of(p),
        p.getParameterizedType(),
        p.getName(),
        p.getAnnotations(),
        p.getDeclaringExecutable().getDeclaringClass());
  }
  
  static <T> InjectionPoint<T> of(Class<?> declaringClass,
                           Class<T> paramType,
                           Annotation []annotations)
  {
    return new InjectionPointImpl<>(Key.of(paramType),
        paramType,
        declaringClass.getName(),
        annotations,
        declaringClass);
  }
  
  static <T> InjectionPoint<T> of(Key<T> key,
                                  Type type,
                                  String name,
                                  Annotation []annotations,
                                  Class<?> declaringClass)
  {
    return new InjectionPointImpl<>(key, type, name, annotations, declaringClass);
  }
}

