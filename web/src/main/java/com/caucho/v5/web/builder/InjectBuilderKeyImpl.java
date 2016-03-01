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
 * @author Scott Ferguson
 */

package com.caucho.v5.web.builder;

import java.lang.reflect.Method;
import java.util.Objects;

import javax.inject.Provider;

import io.baratine.inject.InjectManager.BindingBuilder;
import io.baratine.inject.InjectManager.InjectBuilderRoot;
import io.baratine.inject.InjectManager.ScopeBuilder;
import io.baratine.inject.Key;
import io.baratine.web.IncludeWeb;
import io.baratine.web.WebBuilder;

public class InjectBuilderKeyImpl<T>
  implements BindingBuilder<T>, ScopeBuilder, IncludeWeb
{
  private Key<T> _key;
  
  private Class<? extends T> _providerClass;
  private Provider<? extends T> _providerSupplier;

  private InjectBuilderRoot _injectServer;

  private BindingBuilder<T> _bindingServer;

  private Key<?> _providerBaseKey;
  private int _priority;

  private Method _providerMethod;

  InjectBuilderKeyImpl(InjectBuilderRoot injectServer, 
                       Key<T> key)
  {
    Objects.requireNonNull(injectServer);
    Objects.requireNonNull(key);
    
    _injectServer = injectServer;
    _key = key;
    
    _bindingServer = injectServer.bind(key);
  }
  
  @Override
  public InjectBuilderKeyImpl<T> priority(int priority)
  {
    _priority = priority;
    
    return this;
  }
  
  @Override
  public <U extends T> ScopeBuilder to(Class<U> providerClass)
  {
    _providerClass = providerClass;
    
    return this;
  }
  
  @Override
  public <U extends T> ScopeBuilder toProvider(Provider<U> providerSupplier)
  {
    _providerSupplier = providerSupplier;
    
    return this;
  }
  
  @Override
  public void toSupplier(Key<?> baseKey,
                         Method method)
  {
    _providerBaseKey = baseKey;
    _providerMethod = method;
  }

  @Override
  public void build(WebBuilder builder)
  {
    BindingBuilder<T> binder = builder.bind(_key);

    if (_providerClass != null) {
      binder.to(_providerClass);
    }
    else if (_providerSupplier != null) {
      binder.toProvider(_providerSupplier);
    }
  }

  /*
  @Override
  public void build(InjectManager.InjectBuilder builder)
  {
    BindingBuilder<T> binder = builder.bind(_type);
    
    if (_providerClass != null) {
      binder.to(_providerClass);
    }
  }
  */
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _key + "]";
  }
}
