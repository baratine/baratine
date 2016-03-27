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

package com.caucho.v5.inject.impl;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;

import javax.inject.Provider;

import com.caucho.v5.config.ConfigExceptionLocation;
import com.caucho.v5.inject.InjectorAmp;
import com.caucho.v5.inject.InjectProgram;

import io.baratine.inject.InjectionPoint;
import io.baratine.inject.Key;

/**
 * @Inject for a field
 */
class InjectField<T> extends InjectProgram
  implements InjectionPoint<T>
{
  private InjectorAmp _inject;
  private Field _field;
  private Key<T> _key;
  private Provider<T> _program;
  
  InjectField(InjectorAmp inject,
                     Field field)
  {
    _inject = inject;
    
    _field = field;
    _field.setAccessible(true);
    
    _key = Key.of(field);
    
    _program = _inject.provider(this);
  }
  
  @Override
  public Key<T> key()
  {
    return _key;
  }
  
  @Override
  public Type type()
  {
    return _field.getGenericType();
  }
  
  @Override
  public String name()
  {
    return _field.getName();
  }
  
  @Override
  public Annotation []annotations()
  {
    return _field.getAnnotations();
  }
  
  @Override
  public Class<?> declaringClass()
  {
    return _field.getDeclaringClass();
  }

  @Override
  public <X> void inject(X bean, InjectContext env)
  {
    //Object value = _inject.instance(this);

    try {
      _field.set(bean, _program.get());
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw ConfigExceptionLocation.wrap(_field, e);
    }
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + key()
            + "," + name()
            + "," + declaringClass().getSimpleName()
            + "]");
  }
}
