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

package com.caucho.v5.amp.manager;

import java.util.ArrayList;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.actor.ServiceRefException;
import com.caucho.v5.amp.actor.ServiceRefLazyProxy;
import com.caucho.v5.amp.spi.RegistryAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.LruCache;

import io.baratine.service.ResultFuture;

/**
 * Lookup manager manages the address lookup.
 */
class RegistryImpl implements RegistryAmp
{
  private static final Logger log
    = Logger.getLogger(RegistryImpl.class.getName());
  private static final L10N L = new L10N(RegistryImpl.class);
  
  private final ServiceManagerAmp _manager;
  
  private final ConcurrentHashMap<String,ServiceRefAmp> _serviceMap
    = new ConcurrentHashMap<>();
  
  private final LruCache<String,ServiceRefAmp> _cacheServiceMap;
  
  public RegistryImpl(ServiceManagerAmp manager)
  {
    _manager = manager;
    
    // XXX: need this configurable
    int cacheSize = 1024;
    
    _cacheServiceMap = new LruCache<>(cacheSize);
  }

  @Override
  public ServiceRefAmp service(String address)
  {
    ServiceRefAmp serviceRef = _cacheServiceMap.get(address);

    //if (serviceRef != null && serviceRef.isUp()) {
    if (serviceRef != null && ! serviceRef.isClosed()) {
      ServiceRefAmp instanceRef = serviceRef.lookup();
      
      if (instanceRef != null) {
        return instanceRef;
      }
    }
    else {
      serviceRef = lookupImpl(ServiceManagerAmpImpl.toCanonical(address));

      if (serviceRef != null) {
        //_cacheServiceMap.putIfAbsent(address, serviceRef);
        _cacheServiceMap.put(address, serviceRef);
      }
    }
    
    return serviceRef;
  }
  
  @Override
  public Iterable<ServiceRefAmp> getServices()
  {
    ArrayList<ServiceRefAmp> services = new ArrayList<>(_serviceMap.values());
    
    return services;
  }

  private ServiceRefAmp lookupImpl(String address)
  {
    ServiceRefAmp serviceRef = lookupSingle(address);

    if (serviceRef != null) {
      return serviceRef;
    }
    
    int hostIndex = address.indexOf("://");
    int p = address.length();
    
    while ((p = address.lastIndexOf('/', p - 1)) >= 0
           && (hostIndex < 0 || hostIndex + 3 <= p)) {
      String prefix = address.substring(0, p);
      String suffix = address.substring(p);
      ServiceRefAmp parentRef = lookupSingle(prefix);
      
      if (parentRef != null) {
        ServiceRefAmp childRef = parentRef.onLookup(suffix);
        
        if (childRef != null) {
          return childRef;
        }
      }
    }
    
    if (hostIndex > 0) {
      String scheme = address.substring(0, hostIndex + 1);
      String suffix = address.substring(hostIndex + 1);
      
      ServiceRefAmp parentRef = lookupSingle(scheme);
      
      if (parentRef != null) {
        ServiceRefAmp childRef = parentRef.onLookup(suffix);
        
        if (childRef != null) {
          return childRef;
        }
      }
    }
    
    return new ServiceRefLazyProxy(_manager, address);
  }
  
  private ServiceRefAmp lookupSingle(String address)
  {
    ServiceRefAmp serviceRef = _serviceMap.get(address);

    if (serviceRef == null) {
      return null;
    }
    
    ServiceRefAmp instanceRef = serviceRef.lookup();

    if (instanceRef != null) {
      return instanceRef;
    }
    
    return serviceRef;
  }

  @Override
  public void bind(String address, ServiceRefAmp serviceRef)
  {
    if (_manager != serviceRef.manager()) {
      throw new IllegalStateException(L.l("Binding in mismatched manager {0} with {1} and {2}",
                                          _manager, serviceRef, serviceRef.manager()));
    }
    ServiceRefAmp oldServiceRef = _serviceMap.get(address);
    
    if (oldServiceRef == null) {
      
      _serviceMap.put(address, serviceRef);
    }
    else {
      IllegalStateException exn = new IllegalStateException(L.l("Conflicting serviceRef {0} and {1} in {2}",
                                                                oldServiceRef, serviceRef, _manager));
      
      log.log(Level.FINE, exn.toString(), exn);
      
      ServiceRefAmp errorServiceRef
        = new ServiceRefException(_manager, address, exn);
      
      _serviceMap.put(address, errorServiceRef);
    }
  }

  /*
  @Override
  public void publish(String address, RampBroker broker)
  {
    _brokerMap.put(address, broker);
  }
  */
  
  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    TreeSet<String> serviceNames = new TreeSet<>(_serviceMap.keySet());
    
    // HashSet<ServiceRefAmp> serviceSet = new HashSet<>(_serviceMap.values());
    
    if (mode == ShutdownModeAmp.GRACEFUL) {
      save(serviceNames);
    }
    
    for (String serviceName : serviceNames) {
      try {
        ServiceRefAmp serviceRef = _serviceMap.get(serviceName);
        
        serviceRef.shutdown(mode);
      } catch (Throwable e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }
  }
  
  private void save(TreeSet<String> names)
  {
    ArrayList<ResultFuture<Void>> saveResults = new ArrayList<>();
    
    for (String serviceName : names) {
      try {
        ServiceRefAmp serviceRef = _serviceMap.get(serviceName);
        
        ResultFuture<Void> result = new ResultFuture<>();
        
        serviceRef.save(result);
        
        saveResults.add(result);
      } catch (Throwable e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }
    
    for (ResultFuture<Void> result : saveResults) {
      result.get(10, TimeUnit.SECONDS);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
