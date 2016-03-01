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

package io.baratine.inject;

import java.lang.reflect.Method;
import java.util.List;

import javax.inject.Provider;

import io.baratine.config.IncludeGenerator;
import io.baratine.convert.Convert;
import io.baratine.spi.ServiceManagerProvider;

/**
 * The injection manager interface
 */
public interface InjectManager
{
  /**
   * Returns an instance provider for the given Key, which combines
   * a Type with &64;Qualifier annotations.
   */
  <T> Provider<T> provider(Key<T> key);
  
  /**
   * Returns an instance provider for the given InjectionPoint.
   */
  <T> Provider<T> provider(InjectionPoint<T> atPoint);
  
  /**
   * Returns an injected instance for the given type. The instance returned
   * depends on the bindings of the inject manager.
   */
  <T> T instance(Class<T> type);

  /**
   * Returns an injected instance for the given Key, which combines
   * a type with annotations.
   */
  <T> T instance(Key<T> key);
  
  <T> T instance(InjectionPoint<T> ip);
  
  /**
   * Injects dependencies.
   */
  void inject(Object bean);

  <T> List<Binding<T>> bindings(Key<T> key);

  <S,T> Convert<S, T> converter(Class<S> source, Class<T> target);

  public static InjectBuilderRoot newManager(ClassLoader classLoader)
  {
    return ServiceManagerProvider.current().injectManager(classLoader);
  }

  public static InjectBuilderRoot newManager()
  {
    return newManager(Thread.currentThread().getContextClassLoader());
  }
  
  public interface InjectBuilder
  {
    default InjectBuilder include(Class<?> includeClass)
    {
      throw new UnsupportedOperationException(getClass().getName());
    }
    
    <T> BindingBuilder<T> bind(Class<T> api);
    <T> BindingBuilder<T> bind(Key<T> key);
    
    InjectBuilder autoBind(InjectAutoBind autoBind);
  }
  
  public interface InjectBuilderRoot extends InjectBuilder
  {
    InjectManager get();
  }
  
  public interface BindingBuilder<T>
  {
    <U extends T> ScopeBuilder to(Class<U> impl);
    
    <U extends T> ScopeBuilder toProvider(Provider<U> impl);
    
    default <U extends T> ScopeBuilder to(U impl)
    {
      return toProvider(()->impl);
    }

    void toSupplier(Key<?> baseKey, Method m);

    BindingBuilder<T> priority(int priority);
  }
  
  public interface ScopeBuilder
  {
  }
  
  public interface IncludeInject extends IncludeGenerator<InjectBuilder>
  {
  }
  
  public interface InjectAutoBind
  {
    <T> Provider<T> provider(InjectManager manager, Key<T> key);
  }
}

