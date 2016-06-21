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

package com.caucho.v5.autoconf.spring;

import javax.inject.Provider;

import org.springframework.context.ApplicationContext;

import com.caucho.v5.config.IncludeOnClass;

import io.baratine.config.Include;
import io.baratine.inject.Injector;
import io.baratine.inject.Injector.IncludeInject;
import io.baratine.inject.Injector.InjectAutoBind;
import io.baratine.inject.Injector.InjectorBuilder;
import io.baratine.inject.Key;

/**
 * spring
 */
@Include
@IncludeOnClass(ApplicationContext.class)
public class IncludeSpring implements IncludeInject
{
  public IncludeSpring()
  {
  }

  @Override
  public void build(InjectorBuilder builder)
  {
    builder.autoBind(new SpringAutoBind());
  }
  
  static class SpringAutoBind implements InjectAutoBind
  {
    @Override
    public <T> Provider<T> provider(Injector injector, Key<T> key)
    {
      Class<?> type = key.rawClass();
      
      if (ApplicationContext.class.equals(type)) {
        return null;
      }
      
      ApplicationContext context = context(injector);
      
      if (context == null) {
        return null;
      }
      
      String[] names = context.getBeanNamesForType(type);
      
      if (names == null || names.length == 0) {
        return null;
      }
      
      return ()->(T) context.getBean(type);
    }
    
    private ApplicationContext context(Injector injector)
    {
      return injector.instance(ApplicationContext.class);
    }
  }
}
