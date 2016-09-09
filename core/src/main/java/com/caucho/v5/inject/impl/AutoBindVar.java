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

import javax.inject.Provider;

import io.baratine.config.Var;
import io.baratine.inject.InjectionPoint;
import io.baratine.inject.Injector;
import io.baratine.inject.Injector.InjectAutoBind;
import io.baratine.inject.Key;
import io.baratine.inject.Priority;

import com.caucho.v5.inject.InjectorAmp;

/**
 * AutoBinding for the @Var.
 */
@Priority(-10)
class AutoBindVar implements InjectAutoBind
{
  @Override
  public <T> Provider<T> provider(Injector injector, 
                                  InjectionPoint<T> ip)
  {
    Var var = ip.annotation(Var.class);

    if (var == null) {
      return null;
    }
    
    String name = var.value();
    
    if (name.isEmpty()) {
      name = ip.name();
    }
    
    String defaultValue = var.defaultValue();

    return new ProviderVar<T>((InjectorAmp) injector,
                              (Class<T>) ip.key().type(),
                              name,
                              defaultValue);
  }
  
  

  @Override
  public <T> Provider<T> provider(Injector manager, Key<T> key)
  {
    return null;
  }
  
  private static class ProviderVar<T> implements Provider<T>
  {
    private InjectorAmp _injector;
    private Class<T> _type;
    private String _var;
    private String _defaultValue;
    
    ProviderVar(InjectorAmp injector,
                Class<T> type,
                String var,
                String defaultValue)
    {
      _injector = injector;
      _var = var;
      _type = type;
      _defaultValue = defaultValue;
    }

    @Override
    public T get()
    {
      T value = _injector.config().get(_var, _type, _defaultValue);
      
      if (value == null
          && ! _type.isPrimitive()
          && ! (Enum.class.isAssignableFrom(_type))
          && ! _type.isArray()) {
        value = _injector.instance(_type);
        
        if (value != null) {
          _injector.config().inject(value, _var);
        }
      }
      
      return value;
    }
  }
}

