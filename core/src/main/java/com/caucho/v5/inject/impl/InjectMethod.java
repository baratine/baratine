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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import javax.inject.Provider;

import com.caucho.v5.config.ConfigExceptionLocation;
import com.caucho.v5.inject.InjectorAmp;
import com.caucho.v5.inject.InjectProgram;

import io.baratine.inject.InjectionPoint;
import io.baratine.inject.Key;

/**
 * @Inject for a method
 */
class InjectMethod<T> extends InjectProgram
  implements InjectionPoint<T>
{
  private InjectorAmp _inject;
  private Key<T> _key;
  
  private Method _method;
  private MethodHandle _methodHandle;
  private Provider<?>[] _program;
  
  InjectMethod(InjectorAmp inject,
                      Method method)
  {
    _inject = inject;
    _method = method;
  
    _program = inject.program(method.getParameters());
    
    try {
      method.setAccessible(true);
    
      _methodHandle = MethodHandles.lookup().unreflect(method);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    
    _key = Key.of(method);
  }
  
  @Override
  public Key<T> key()
  {
    return _key;
  }
  
  @Override
  public Type type()
  {
    return _method.getGenericReturnType();
  }
  
  @Override
  public String name()
  {
    return _method.getName();
  }
  
  @Override
  public Annotation []annotations()
  {
    return _method.getAnnotations();
  }
  
  @Override
  public Class<?> declaringClass()
  {
    return _method.getDeclaringClass();
  }

  @Override
  public <X> void inject(X bean, InjectContext env)
  {
    Object []args = new Object[_program.length];
    
    for (int i = 0; i < args.length; i++) {
      args[i] = _program[i].get();
    }

    try {
      _method.invoke(bean, args);
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable e) {
      throw ConfigExceptionLocation.wrap(_method, e);
    }
  }
}
