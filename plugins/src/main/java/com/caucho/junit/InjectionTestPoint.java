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
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Alex Rojkov
 */

package com.caucho.junit;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.util.Arrays;

import javax.inject.Inject;

class InjectionTestPoint
{
  private Class<?> _type;
  private Annotation[] _annotations;
  private MethodHandle _setter;

  public InjectionTestPoint(Class<?> type,
                            Annotation[] annotations,
                            MethodHandle setter)
  {
    _type = type;
    _annotations = annotations;
    _setter = setter;
  }

  public Class<?> getType()
  {
    return _type;
  }

  public Inject getInject()
  {
    return getAnnotation(Inject.class);
  }

  public <A extends Annotation> A getAnnotation(Class<A> target)
  {
    A result = null;
    for (Annotation annotation : _annotations) {
      if (target.isAssignableFrom(annotation.annotationType())) {
        result = (A) annotation;

        break;
      }
    }

    return result;
  }

  public Annotation[] getAnnotations()
  {
    return _annotations;
  }

  public void inject(Object... args) throws Throwable
  {
    _setter.invokeWithArguments(args);
  }

  @Override
  public String toString()
  {
    return this.getClass().getSimpleName() + "["
           + _type
           + ", "
           + Arrays.toString(_annotations)
           + ']';
  }
}
