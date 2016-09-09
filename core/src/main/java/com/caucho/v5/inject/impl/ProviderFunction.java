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

import java.util.function.Function;

import javax.inject.Provider;

import io.baratine.inject.Key;

import com.caucho.v5.inject.BindingAmp;
import com.caucho.v5.inject.InjectorAmp;
import com.caucho.v5.inject.type.TypeRef;

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
public class ProviderFunction<T,X> implements BindingAmp<T>, Function<X,T>
{
  private InjectorAmp _manager;
  private final Function<X,T> _function;
  private final Key<T> _key;
  private final int _priority;
  
  private Class<?> _argType;

  public ProviderFunction(InjectorAmp manager,
                          Key<T> key,
                          int priority,
                          Function<X,T> function)
  {
    _manager = manager;

    TypeRef paramRef = TypeRef.of(function.getClass())
                              .to(Function.class)
                              .param(0);
    
    _argType = paramRef.rawClass();
    
    _key = key;
    _priority = priority;
    
    _function = function;
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
  }
  
  @Override
  public Provider<T> provider()
  {
    System.out.println("PROV: " + this);
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
    return _function.apply(arg);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _key + "]";
  }
}

