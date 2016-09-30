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

package io.baratine.service;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

import io.baratine.inject.Injector;
import io.baratine.inject.Injector.InjectorBuilder;
import io.baratine.inject.Key;
import io.baratine.spi.ServiceManagerProvider;

/**
 * Management for Baratine services.
 * 
 * <pre><code>
 * &#64;Inject Services _manager;
 * ...
 *  //Create a new local service
 *  MyService service = _manager.newService(new MyServiceImpl())
 *                              .as(MyService.class);
 *  
 *  //Bind a service that is available for remote calls with a given interface
 *  MySub sub = _manager.newService(new MySubImpl())
 *                      .address("/sub")
 *                      .as(MySub.class);
 * </code></pre>
 */
public interface Services
{
  /**
   * Returns the current Services manager
   */
  static Services current()
  {
    return ServiceManagerProvider.current().currentManager();
  }

  /**
   * Creates a new Services manager for using Baratine embedded in another
   * application.
   * 
   * <pre><code>
   * manager = Services.newManager()
   *                         .start();
   * </code></pre>
   */
  static Services.ServicesBuilder newManager()
  {
    return ServiceManagerProvider.current().newManager();
  }

  /**
   * Looks up a service from a particular address.
   *
   * @param address the address to lookup
   * @return an ServiceRef
   */
  ServiceRef service(String address);
  
  <T> T service(Class<T> api);
  
  <T> T service(Class<T> api, String id);
  
  /**
   * Creates a new service programmatically.
   * 
   * <pre><code>
   * services.newService(myServiceImpl)
   *         .ref();
   * </code></pre>
   * 
   * @param serviceImpl the service implementation.
   * @return builder for fluent dsl
   */
  <T> ServiceRef.ServiceBuilder newService(T serviceImpl);
  
  <T> ServiceRef.ServiceBuilder newService(Class<T> type);
  
  <T> ServiceRef.ServiceBuilder newService(Class<T> type, 
                                           Supplier<? extends T> supplier);

  Injector injector();

  /**
   * ServicesBuilder provides interface to building a service.
   *
   * @see ServiceInitializer
   * @see io.baratine.service.ServiceRef.ServiceBuilder
   */
  public interface ServicesBuilder
  {
    /**
     * Scans META-INF/services for built-in services.
     */
    ServicesBuilder autoServices(boolean isAutoServices);

    /**
     * Creates a ServiceBuilder from a specific Service class
     *
     * @param type service class
     * @param <T> service type
     * @return instance of ServiceBuilder
     */
    <T> ServiceRef.ServiceBuilder service(Class<T> type);

    /**
     * Creates a ServiceBuilder from a specific Service class and binds it to
     * the supplied Key
     *
     * @param key binding key for injecting service into Injection points
     * @param type service class
     * @param <T> service type
     * @return instance of ServiceBuilder
     */
    <T> ServiceRef.ServiceBuilder service(Key<?> key, Class<T> type);

    /**
     * Creates a ServiceBuilder from a specific Type
     *
     * @param type Service type
     * @param <T> type
     * @return instance of ServiceBuilder
     */
    <T> ServiceRef.ServiceBuilder service(T type);

    /**
     * Creates a ServiceBuilder from a specific Service class and Service instance
     * supplier.
     *
     * @param type service class
     * @param supplier supplier of Service instance
     * @param <T> service type
     * @return instance of ServiceBuilder
     */
    <T> ServiceRef.ServiceBuilder service(Class<T> type, 
                                          Supplier<? extends T> supplier);

    /**
     * Context InjectorBuilder for configuring new Beans for injection
     *
     * @return instance of InjectorBuilder
     */
    InjectorBuilder injector();
    
    /**
     * Sets an executor factory for context-dependent integration.
     * 
     * When a Baratine service is called outside of Baratine, the result
     * callback is executed in the caller's context.
     * 
     * @param factory the supplied factory for thread execution context
     */
    ServicesBuilder systemExecutor(Supplier<Executor> factory);

    /**
     * Obtains instance of Services (service manager)
     *
     * @return instance of Services
     */
    Services get();

    /**
     * Starts instance of Services (service manager)
     *
     * @return instance of Services
     */
    Services start();
  }
}
