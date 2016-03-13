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

package com.caucho.v5.amp.remote;

import java.util.function.Supplier;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.spi.RegistryAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.util.LruCache;

/**
 * Server registry shared across all clients, which caches the lookup.
 */
public class RegistryAmpServerShared implements RegistryAmp
{
  private static final Logger log
    = Logger.getLogger(RegistryAmpServerShared.class.getName());
  
  private final LruCache<String,ServiceRefAmp> _serviceCache
    = new LruCache<>(8192);
  
  private final LruCache<MethodKey,MethodRefAmp> _methodCache
    = new LruCache<>(8192);
  
  private final Supplier<ServiceManagerAmp> _managerRef;
  private final String _podName;

  public RegistryAmpServerShared(Supplier<ServiceManagerAmp> managerRef,
                                 String podName)
  {
    _managerRef = managerRef;
    _podName = podName;
  }
  
  @Override
  public MethodRefAmp method(String address, String methodName)
  {
    MethodKey key = new MethodKey(address, methodName);
    
    MethodRefAmp methodRef = _methodCache.get(key);
    
    if (methodRef == null || methodRef.isClosed()) {
      ServiceRefAmp serviceRef = lookupImpl(address);
      
      methodRef = serviceRef.getMethod(methodName);
      
      _methodCache.put(key, methodRef);
    }
    
    return methodRef;
  }
  
  @Override
  public ServiceRefAmp service(String address)
  {
    ServiceRefAmp serviceRef = _serviceCache.get(address);
    
    if (serviceRef == null || serviceRef.isClosed()) {
      serviceRef = lookupImpl(address);

      _serviceCache.put(address, serviceRef);
    }
    
    return serviceRef;
  }
  
  private ServiceRefAmp lookupImpl(String address)
  {
    ServiceManagerAmp manager = _managerRef.get();

    if (address.startsWith("public://")) {
      return manager.service(address);
    }
    else if (address.startsWith("session://")){
      return manager.service(address);
    }
    else if (address.startsWith("/")) {
      ServiceRefAmp serviceRef = manager.service("session://" + address);
      
      if (! serviceRef.isClosed()) {
        return serviceRef;
      }

      serviceRef = manager.service("public://" + address);

      if (! serviceRef.isClosed()) {
        return serviceRef;
      }

      /*
      // XXX: should be local with validation of name?
      if (_podName != null) {
        ServiceRefAmp podRef = _manager.lookup("pod://" + _podName + address);
        
        if (podRef.isValid()) {
          return podRef;
        }
      }
      */

      return serviceRef;
    }
    else {
      // XXX:
      return manager.service(address);
    }
  }
  
  /**
   * Adds a new link actor.
   */
  @Override
  public void bind(String address, ServiceRefAmp linkService)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public void unbind(String address)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public Iterable<ServiceRefAmp> getServices()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Called when the link is closing.
   */
  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _managerRef + "]";
  }
  
  private static class MethodKey {
    private final String _address;
    private final String _methodName;
    
    MethodKey(String address, String methodName)
    {
      _address = address;
      _methodName = methodName;
    }
    
    @Override
    public int hashCode()
    {
      int hash = _address.hashCode();
      
      hash = 65521 * hash + _methodName.hashCode();
      
      return hash;
    }
    
    public boolean equals(Object o)
    {
      if (! (o instanceof MethodKey)) {
        return false;
      }
      
      MethodKey key = (MethodKey) o;
      
      return key._address.equals(_address) && key._methodName.equals(_methodName);
    }
  }
}
