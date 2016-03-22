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

import java.util.Objects;

import javax.inject.Provider;

import com.caucho.v5.inject.BindingAmp;

import io.baratine.inject.Key;

/**
 * Bean provider base.
 */
abstract public class ProviderBase<T> implements Provider<T>, BindingAmp<T>
{
  private final Key<T> _key;
  private final int _priority;
  private final InjectScope<T> _scope;

  public ProviderBase(Key<T> key,
                      int priority,
                      InjectScope<T> scope)
  {
    Objects.requireNonNull(scope);
    Objects.requireNonNull(key);
    
    _scope = scope;
    _priority = priority;
    _key = key;
  }
  
  @Override
  public Key<T> key()
  {
    return _key;
  }
  
  @Override
  public int priority()
  {
    return _priority;
  }
  
  @Override
  public Provider<T> provider()
  {
    return this;
  }
  
  @Override
  public T get()
  {
    T bean = _scope.get();
    
    if (bean == null) {
      bean = create();
      
      if (bean != null) {
        _scope.set(bean);
      }
    }
    
    return bean;
  }
  
  abstract protected T create();
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _key + "]";
  }
}

