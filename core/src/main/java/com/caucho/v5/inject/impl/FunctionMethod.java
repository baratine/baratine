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

import java.lang.reflect.Method;
import java.util.function.Function;

import javax.inject.Provider;

import com.caucho.v5.config.Priority;
import com.caucho.v5.inject.BindingAmp;
import com.caucho.v5.inject.InjectorAmp;

import io.baratine.inject.Key;

/**
 * A method producer that returns a new injection bean for the given type and
 * qualifiers.
 * 
 * <pre><code>
 * public class MyFactory
 * {
 *   &#64;Bean
 *   MyBean myMethod() { ... }
 * }
 * </code></pre> 
 */
public class FunctionMethod<T,X,P> implements BindingAmp<T>, Function<X,T>
{
  private InjectorAmp _manager;
  private Method _method;
  private Key<T> _key;
  private int _priority;
  
  private BindingAmp<P> _ownerBinding;
  private Provider<P> _ownerProvider;
  private Provider<?>[] _program;
  private int _argIndex;
  private Class<?> _argType;

  public FunctionMethod(InjectorAmp manager,
                       BindingAmp<P> ownerBinding,
                       Method method,
                       int argIndex)
  {
    _manager = manager;
    _ownerBinding = ownerBinding;
    _method = method;
    
    _argIndex = argIndex;
    _argType = method.getParameterTypes()[argIndex];
    
    Priority priority = _method.getAnnotation(Priority.class);
    
    if (priority != null) {
      _priority = priority.value();
    }
    
    _program = manager.program(method.getParameters());
    
    method.setAccessible(true);
    
    _key = Key.of(method);
  }
  
  @Override  
  public Key<T> key()
  {
    return _key;
  }
  
  public int priority()
  {
    return _priority;
  }
  
  @Override
  public void bind()
  {
    if (_ownerProvider == null) {
      _ownerBinding.bind();
      
      _ownerProvider = _ownerBinding.provider(); 

      _program = _manager.program(_method.getParameters());
    }
  }
  
  @Override
  public Provider<T> provider()
  {
    throw new UnsupportedOperationException(getClass().getSimpleName());
  }

  @Override
  public <Y> Function<Y,T> function(Class<Y> paramType)
  {
    if (_argType.isAssignableFrom(paramType)) {
      return (Function) this;
    }
    else {
      return null;
    }
  }

  @Override
  public T apply(X arg)
  {
    P bean = _ownerProvider.get();
    
    try {
      Object []args = new Object[_program.length];
      
      for (int i = 0; i < args.length; i++) {
        if (_argIndex == i) {
          args[i] = arg;
        }
        else {
          args[i] = _program[i].get();
        }
      }
      
      T value = (T) _method.invoke(bean, args);

      return value;
    } catch (Exception e) {
      throw new RuntimeException(e); 
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _key + "]";
  }
}

