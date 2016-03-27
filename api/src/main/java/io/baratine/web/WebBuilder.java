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

package io.baratine.web;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import javax.inject.Provider;

import io.baratine.convert.Convert;
import io.baratine.inject.Injector;
import io.baratine.inject.Injector.BindingBuilder;
import io.baratine.inject.Injector.InjectAutoBind;
import io.baratine.inject.Injector.InjectBuilder;
import io.baratine.inject.Key;
import io.baratine.service.ServiceRef.ServiceBuilder;


public interface WebBuilder
{
  WebBuilder include(Class<?> type);
  
  //
  // inject methods
  //
  
  <T> BindingBuilder<T> bean(Class<T> impl);
  <T> BindingBuilder<T> bean(T instance);
  
  <T> BindingBuilder<T> provider(Provider<T> provider);
  <T,U> BindingBuilder<T> provider(Key<U> parent, Method m);
  
  InjectBuilder autoBind(InjectAutoBind autoBind);

  //
  // route methods
  //
  
  WebResourceBuilder route(HttpMethod method, String path);

  default WebResourceBuilder delete(String path)
  {
    return route(HttpMethod.DELETE, path);
  }

  default WebResourceBuilder get(String path)
  {
    return route(HttpMethod.GET, path);
  }
  
  default WebResourceBuilder options(String path)
  {
    return route(HttpMethod.OPTIONS, path);
  }
  
  default WebResourceBuilder patch(String path)
  {
    return route(HttpMethod.PATCH, path);
  }
  
  default WebResourceBuilder post(String path)
  {
    return route(HttpMethod.POST, path);
  }
  
  default WebResourceBuilder put(String path)
  {
    return route(HttpMethod.PUT, path);
  }
  
  default WebResourceBuilder trace(String path)
  {
    return route(HttpMethod.TRACE, path);
  }
  
  default WebResourceBuilder route(String path)
  {
    return route(HttpMethod.UNKNOWN, path);
  }

  WebSocketBuilder websocket(String path);

  <T> WebBuilder view(ViewWeb<T> view);
  <T> WebBuilder view(Class<? extends ViewWeb<T>> view);

  default WebBuilder push()
  {
    throw new UnsupportedOperationException();
  }
  
  
  Injector inject();

  <T> ServiceBuilder service(Class<T> serviceClass);
  
  <T> ServiceBuilder service(Class<T> serviceClass,
                             Supplier<? extends T> supplier);

  ServiceBuilder service(Key<?> key, Class<?> apiClass);

  default <S,T> Convert<S,T> converter(Class<S> source, Class<T> target)
  {
    return inject().converter(source, target);
  }
}
