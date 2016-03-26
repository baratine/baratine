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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * AnnotatedTypeClass wraps a class.
 */
public class AnnotatedTypeClass implements AnnotatedType
{
  private static final AnnotatedType _annotatedTypeObject
    = new AnnotatedTypeClass(Object.class);
  
  private Class<?> _type;
  
  public AnnotatedTypeClass(Class<?> type)
  {
    Objects.requireNonNull(type);
    _type = type;
  }
  
  @Override
  public Type getType()
  {
    return _type;
  }

  @Override
  public <T extends Annotation> T getAnnotation(Class<T> annotationClass)
  {
    return _type.getAnnotation(annotationClass);
  }

  @Override
  public Annotation[] getAnnotations()
  {
    return _type.getAnnotations();
  }

  @Override
  public Annotation[] getDeclaredAnnotations()
  {
    return _type.getDeclaredAnnotations();
  }

  public static AnnotatedType ofObject()
  {
    return _annotatedTypeObject;
  }
}
