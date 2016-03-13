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

import java.util.Objects;

import io.baratine.inject.InjectManager.BindingBuilder;
import io.baratine.inject.InjectManager.InjectBuilder;
import io.baratine.inject.Key;
import io.baratine.web.IncludeWeb;
import io.baratine.web.WebBuilder;

public class InjectBuilderWebImpl<T>
  implements BindingBuilder<T>, IncludeWeb
{
  private T _bean;
  private Class<T> _type;
  
  private Class<? super T> _api;
  private Key<? super T> _key;
  private int _priority;
  
  private BindingBuilder<T> _builder;
  
  InjectBuilderWebImpl(InjectBuilder injectBuilder, Class<T> type)
  {
    Objects.requireNonNull(injectBuilder);
    Objects.requireNonNull(type);
    
    _type = type;
    
    _builder = injectBuilder.bean(type);
  }
  
  InjectBuilderWebImpl(InjectBuilder injectBuilder, T bean)
  {
    Objects.requireNonNull(injectBuilder);
    Objects.requireNonNull(bean);
    
    if (bean instanceof Class<?>) {
      throw new IllegalArgumentException(String.valueOf(bean));
    }
    
    _bean = bean;
    
    _builder = injectBuilder.bean(bean);
  }
  
  @Override
  public BindingBuilder<T> to(Class<? super T> api)
  {
    Objects.requireNonNull(api);
    
    _api = api;
    
    _builder.to(api);

    return this;
  }

  @Override
  public BindingBuilder<T> to(Key<? super T> key)
  {
    Objects.requireNonNull(key);
    
    _key = key;
    
    _builder.to(key);

    return this;
  }

  @Override
  public BindingBuilder<T> priority(int priority)
  {
    _priority = priority;
    
    _builder.priority(priority);

    return this;
  }
  

  @Override
  public void build(WebBuilder builderWeb)
  {
    BindingBuilder<T> builder;
    
    if (_bean != null) {
      builder = builderWeb.bean(_bean);
    }
    else {
      builder = builderWeb.bean(_type);
    }
    
    if (_api != null) {
      builder.to(_api);
    }
    
    if (_key != null) {
      builder.to(_key);
    }
    
    if (_priority != 0) {
      builder.priority(_priority);
    }
    
    //builder.
  }
}
