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

import java.util.function.Supplier;

import javax.inject.Provider;

import io.baratine.convert.Convert;
import io.baratine.inject.Injector;
import io.baratine.inject.Injector.BindingBuilder;
import io.baratine.inject.Injector.InjectAutoBind;
import io.baratine.inject.Injector.InjectorBuilder;
import io.baratine.inject.Key;
import io.baratine.service.ServiceRef.ServiceBuilder;

/**
 * Interface WebBuilder provides methods to assemble a basic set of services
 * exposed via Web
 */
public interface WebBuilder
{
  /**
   * Adds a class for binding as a service. The class should be a valid service
   * class.
   *
   * @param type
   * @return
   */
  WebBuilder include(Class<?> type);
  
  //
  // inject methods
  //

  /**
   * Configures a generic Bean provider for a specified class
   *
   * @param impl
   * @param <T>
   * @return
   */
  <T> BindingBuilder<T> bean(Class<T> impl);

  /**
   * Configures a generic bean provider for a specified instance
   *
   * @param instance
   * @param <T>
   * @return
   */
  <T> BindingBuilder<T> bean(T instance);

  /**
   * Adds a bean provider for type &lth;T>
   *
   * @param provider
   * @param <T>
   * @return
   */
  <T> BindingBuilder<T> beanProvider(Provider<T> provider);
  //<T,X> BindingBuilder<T> beanFunction(Function<X,T> function);
  //<T,U> BindingBuilder<T> provider(Key<U> parent, Method m);

  InjectorBuilder autoBind(InjectAutoBind autoBind);

  //
  // route methods
  //

  /**
   * Configures a route builder for a specified HTTP method and path.
   *
   * @param method
   * @param path
   * @return
   */
  RouteBuilder route(HttpMethod method, String path);

  /**
   * Configures route builder for a HTTP DELETE method at a specified path
   * @param path
   * @return
   */
  default RouteBuilder delete(String path)
  {
    return route(HttpMethod.DELETE, path);
  }

  /**
   * Configures route builder for HTTP GET method at specified path
   * @param path
   * @return
   */
  default RouteBuilder get(String path)
  {
    return route(HttpMethod.GET, path);
  }

  /**
   * Configures route builder for HTTP OPTIONS method at specified path
   * @param path
   * @return
   */
  default RouteBuilder options(String path)
  {
    return route(HttpMethod.OPTIONS, path);
  }

  /**
   * Configures route buidler for HTTP PATCH method at specified path
   * @param path
   * @return
   */
  default RouteBuilder patch(String path)
  {
    return route(HttpMethod.PATCH, path);
  }

  /**
   * Configures RouteBuilder for HTTP POST method at specified path
   * @param path
   * @return
   */
  default RouteBuilder post(String path)
  {
    return route(HttpMethod.POST, path);
  }

  /**
   * Configures RouteBuilder for HTTP PUT method at specified path
   * @param path
   * @return
   */
  default RouteBuilder put(String path)
  {
    return route(HttpMethod.PUT, path);
  }

  /**
   * Configures RouteBuilder for HTTP TRACE method for specified path
   * @param path
   * @return
   */
  default RouteBuilder trace(String path)
  {
    return route(HttpMethod.TRACE, path);
  }

  /**
   * Configures generic RouteBuilder for a specified path
   * @param path
   * @return
   */
  default RouteBuilder route(String path)
  {
    return route(HttpMethod.UNKNOWN, path);
  }

  /**
   * Configures WebSocketBuilder for a specified path
   * @param path
   * @return
   */
  WebSocketBuilder websocket(String path);

  /**
   * Adds an instance of a ViewRender to the list of available view renders.
   * A view render is selected based on &lth;T> type of object
   * supplied with {@code RequestWeb#ok()}
   *
   * @param view
   * @param <T>
   * @return
   */
  <T> WebBuilder view(ViewRender<T> view);

  /**
   * Adds a ViewRender to the list of available view renders.
   * A view render is selected based on &lth;T> type of object
   * supplied with {@code RequestWeb#ok()}
   * @param view
   * @param <T>
   * @return
   */
  <T> WebBuilder view(Class<? extends ViewRender<T>> view);

  default WebBuilder push()
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns an instance of Injector used with this instance of WebBuilder.
   *
   * @return
   */
  Injector injector();

  /**
   * Configures a ServiceBuilder for a specified service class
   *
   * @param serviceClass
   * @param <T>
   * @return
   */
  <T> ServiceBuilder service(Class<T> serviceClass);

  /**
   * Configures a ServiceBuilder for a specified service class and a service
   * instance supplier
   *
   * @param serviceClass
   * @param supplier
   * @param <T>
   * @return
   */
  <T> ServiceBuilder service(Class<T> serviceClass,
                             Supplier<? extends T> supplier);

  /**
   * Configures a ServiceBuilder for a specified Key (used in binding) and a service
   * class.
   *
   * @param key
   * @param apiClass
   * @return
   */
  ServiceBuilder service(Key<?> key, Class<?> apiClass);

  /**
   * Obtains a converter for converting objects from a source to target.
   *
   * @param source
   * @param target
   * @param <S>
   * @param <T>
   * @return
   *
   * @see Convert
   */
  default <S,T> Convert<S,T> converter(Class<S> source, Class<T> target)
  {
    return injector().converter().converter(source, target);
  }
}
