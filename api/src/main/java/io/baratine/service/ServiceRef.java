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
 * ServiceRef allows an actor service to get to its service reference.
 * This can be useful for invoking methods which are in fact queued for later execution.
 * 
 * Normally service actors do not need to access their underlying ServiceRef.
 * 
 * There may be some special circumstances for debugging, logging, auditing, security where a service might need to access
 * this information. One could also setup their own topology of services based on custom shard rules.
 * 
 * Example:
 * 
 * <pre>
 * //Inside of a service @OnInit
 * ServiceManager manager = ServiceManager.current();
 * 
 * MyService southWestRegionService = manager.newService()
 *                                           .service(new MyServiceImpl(SOUTH_WEST_REGION)
 *                                           .build()
 *                                           .as(MyService.class);
 *                                           
 * MyService northEastRegionService = manager.newService()
 *                                           .service(new MyServiceImpl(NORTH_EAST_REGION)
 *                                           .build()
 *                                           .as(MyService.class);
 * 
 * //Bind a service that is available for remote calls with a given interface for internal usage
 * MyServiceRegionSharder sharder = manager.bind(new MyServiceRegionSharderImpl(southWestRegionService, northEastRegionService),
 * "/internal/interface", MyService.class);
 * 
 * //Bind a service that is available for remote calls with a given interface for public usage
 * MyServiceRegionSharderPublic sharder2 = manager.bind(new MyServiceRegionSharderImpl(), "/externalInterface", MyServicePublic.class);
 * 
 * </pre>
 */

public interface ServiceRef
{
  /**
   * Returns the current ServiceRef
   */
  static ServiceRef current()
  {
    return ServiceManagerProvider.current().getCurrentServiceRef();
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
  ServiceManager manager();
  
  /**
   * Lookup a specific method of this service.
   * @param name name of the method
   * @return
   */
  MethodRef getMethod(String name);

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
  <T> T as(Class<T> api, Class<?> ... apis);
  
  /**
   * Lookup a child service in the ServiceManager with a relative path.
   * 
   * The lookup is a convenience method based on the ServiceManager's lookup.
   * 
   * @param path a relative path to the service.
   */
  default ServiceRef lookup(String path)
  {
    if (! path.startsWith("/")) {
      throw new IllegalArgumentException(path + " must start with '/'");
    }
    
    return manager().service(address() + path);
  }
  
  /**
   * Restrict the service to a specific node for a pod service.
   */
  default ServiceRef node(int hash)
  {
    return this;
  }
  
  default int nodeCount()
  {
    return 1;
  }
  
  /**
   * Subscribes a listener to the service. Multiple subscribers all receive
   * a copy of a callback method.
   * 
   * The listener will be pinned to the context service.
   *
   * @return a CancelHandle for unsubscribing
   */
  Cancel subscribe(Object listener);
  
  /**
   * Registers a listener as a consumer of the service. Only a single consumer
   * receives a copy of a callback method.
   * 
   * The listener will be pinned to the context service.
   *
   * @return a CancelHandle for unsubscribing
   */
  Cancel consume(Object listener);
  
  /**
   * Start the service if it's not already started.
   */
  ServiceRef start();
  
  /**
   * Request a save/flush.
   */
  ServiceRef save(Result<Void> result);

  boolean isClosed();
  void close();
  
  /**
   * Returns the ServiceRef for a proxy. <code>toServiceRef()</code>
   * is the inverse of <code>as()</code>.
   *  
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
  public interface ServiceBuilder
  {
    /**
     * Address to lookup the service in the ServiceManager.
     * 
     * <pre><code>
     * manager.newService()
     *        .service(new MyServiceImpl())
     *        .address("local:///my-service")
     *        .build();
     *        
     * proxy = manager.lookup("/my-service")
     *                .as(MyService.class);
     * </code></pre>
     * 
     * @param address
     * @return
     */
    ServiceBuilder address(String address);
    
    ServiceBuilder addressAuto();
    
    ServiceBuilder workers(int workers);
    
    ServiceBuilder api(Class<?> api);
    
    ServiceRef ref();
    
    default ServiceRef start()
    {
      return ref().start();
    }
    
    default <T> T as(Class<T> api)
    {
      return ref().as(api);
    }
  }
}
