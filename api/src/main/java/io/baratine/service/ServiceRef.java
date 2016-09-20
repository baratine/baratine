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

import io.baratine.spi.ServiceManagerProvider;

/**
 * ServiceRef allows a service to get to its service reference.
 * This can be useful for invoking methods which are in fact queued for later execution.
 * <p>
 * Normally services do not need to access their underlying ServiceRef.
 * <p>
 * There may be some special circumstances for debugging, logging, auditing, security where a service might need to access
 * this information. One could also setup their own topology of services based on custom shard rules.
 * <p>
 * Example:
 * <p>
 * <pre>
 * //Inside of a service @OnInit
 * Services manager = Services.current();
 *
 * MyService southWestRegionService = manager.newService(new MyServiceImpl(SOUTH_WEST_REGION)
 *                                           .as(MyService.class);
 *
 * MyService northEastRegionService = manager.newService(new MyServiceImpl(NORTH_EAST_REGION)
 *                                           .as(MyService.class);
 * </pre>
 */

public interface ServiceRef
{
  /**
   * Returns the current ServiceRef
   */
  static ServiceRef current()
  {
    return ServiceManagerProvider.current().currentServiceRef();
  }

  /**
   * Get the address for this ServiceRef
   */
  String address();

  /**
   * Get the manager associated with this service.
   *
   * @return the service's manager
   */
  Services services();

  /**
   * Pin an object to the service, creating a dependent service
   * using the inbox and thread of the parent service.
   * Useful for callbacks and listeners. The returned ServiceRef
   * will call the service in the context of the parent service.
   */
  ServiceRef pin(Object callback);

  /**
   * Create a proxy for the service with a given api.
   */
  <T> T as(Class<T> api);

  /**
   * Lookup a child service in the ServiceManager with a relative path.
   * <p>
   * The lookup is a convenience method based on the ServiceManager's lookup.
   *
   * @param path a relative path to the service.
   */
  default ServiceRef service(String path)
  {
    if (!path.startsWith("/")) {
      throw new IllegalArgumentException(path + " must start with '/'");
    }

    return services().service(address() + path);
  }

  /**
   * Start the service if it's not already started.
   * <p>
   * Method start will invoke &#64;OnInit marked method of a service instance.
   *
   * @return this instance of ServiceRef for chaining method calls
   * @see OnInit
   */
  ServiceRef start();

  /**
   * Request a save/flush.
   */
  ServiceRef save(Result<Void> result);

  /**
   * Tests if the service has been closed.
   *
   * @return true if service was closed
   */
  boolean isClosed();

  /**
   * Closes the service
   *
   * @param result ignore handler
   */
  default void close(Result<Void> result)
  {
    result.ok(null);
  }

  default void closeImmediate()
  {
  }

  static ServiceRef valueOf(String address)
  {
    if (address == null || address.isEmpty()) {
      return null;
    }
    else {
      return Services.current().service(address);
    }
  }

  /**
   * Returns the ServiceRef for a proxy. <code>toRef()</code>
   * is the inverse of <code>as()</code>.
   */
  static ServiceRef toRef(Object proxy)
  {
    return ServiceManagerProvider.current().toRef(proxy);
  }

  /**
   * Flushes the current outbox, delivering any pending messages to their
   * target inboxes. Useful when a service might block for external data.
   */
  static void flushOutbox()
  {
    ServiceManagerProvider.current().flushOutbox();
  }

  static boolean flushOutboxAndExecuteLast()
  {
    return ServiceManagerProvider.current().flushOutboxAndExecuteLast();
  }

  /**
   * ServiceRef builder
   */
  interface ServiceBuilder
  {
    /**
     * Address to lookup the service in the ServiceManager.
     * <p>
     * <pre><code>
     * manager.newService(new MyServiceImpl())
     *        .address("local:///my-service")
     *        .build();
     *
     * proxy = manager.service("/my-service")
     *                .as(MyService.class);
     * </code></pre>
     *
     * @param address
     * @return
     */
    ServiceBuilder address(String address);

    /**
     * Introspects service class to build its address and workers executors if
     * &#64;Workers annotation is present
     *
     * @return this instance of ServiceBuilder for chaining configuration calls
     */
    ServiceBuilder auto();

    /**
     * Configures executors pool for a service that uses &#64;Workers model
     *
     * @param workers size of the pool
     * @return this instance of ServiceBuilder for chaining configuration calls
     */
    ServiceBuilder workers(int workers);

    /**
     * Binds Service to an class representing its api. The api should typically be
     * an interface which.
     *
     * @param api interface class that service implements
     * @return this instance of ServiceBuilder for chaining configuration calls
     */
    ServiceBuilder api(Class<?> api);

    /**
     * Obtains an instance of ServiceRef for this service. Terminal call after
     * which configuration should be complete.
     *
     * @return instance of ServiceRef for this service
     */
    ServiceRef ref();

    /**
     * Starts service if not already started, calls service method marked with &#64;OnInit.
     * Terminal call after which configuration should be complete and service started.
     *
     * @return instance of ServiceRef for this service
     * @see OnInit
     */
    default ServiceRef start()
    {
      return ref().start();
    }

    /**
     * Creates a proxy to the underlying Service where proxy implements supplied
     * api, which can be interface for cleaner code or a service class.
     * <p>
     *
     * @param api Service interface or implementation
     * @param <T> type
     * @return proxy which implements api specified interface or extends
     * implementation if api is a class
     */
    default <T> T as(Class<T> api)
    {
      return ref().as(api);
    }
  }
}
