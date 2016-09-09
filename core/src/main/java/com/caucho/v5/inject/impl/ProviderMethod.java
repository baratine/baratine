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

import javax.inject.Provider;

import io.baratine.inject.Key;

import com.caucho.v5.inject.BindingAmp;
import com.caucho.v5.inject.InjectorAmp;

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
public class ProviderMethod<T,X> extends ProviderBase<T>
{
  private InjectorAmp _manager;
  private Method _method;
  //private Key<T> _key;
  //private int _priority;
  
  private BindingAmp<X> _ownerBinding;
  private Provider<X> _ownerProvider;
  private Provider<?>[] _program;

  public ProviderMethod(InjectorAmp manager,
                        int priority,
                        InjectScope<T> scope,
                        BindingAmp<X> ownerBinding,
                        Method method)
  {
    super(Key.of(method), priority, scope);
    
    _manager = manager;
    _ownerBinding = ownerBinding;
    _method = method;
    
    /*
    Priority priority = _method.getAnnotation(Priority.class);
    
    if (priority != null) {
      _priority = priority.value();
    }
    */
    
    _program = manager.program(method.getParameters());
    
    method.setAccessible(true);
    
    //_key = Key.of(method);
  }
  
  /*
  @Override  
  public Key<T> key()
  {
    return _key;
  }
  */
  
  /*
  public int priority()
  {
    return _priority;
  }
  */
  
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
    return this;
  }
  
  @Override
  public T create()
  {
    X bean = _ownerProvider.get();
    
    try {
      Object []args = new Object[_program.length];
      
      for (int i = 0; i < args.length; i++) {
        args[i] = _program[i].get(); 
      }
      
      T value = (T) _method.invoke(bean, args);
      
      return value;
    } catch (Exception e) {
      throw new RuntimeException(e); 
    }
  }
  
  /*
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _key + "]";
  }
  */
}

