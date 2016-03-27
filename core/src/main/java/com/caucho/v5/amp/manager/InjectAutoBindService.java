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

import java.lang.annotation.Annotation;
import java.util.Arrays;

import javax.inject.Provider;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.inject.impl.ServiceImpl;

import io.baratine.inject.Injector;
import io.baratine.inject.Injector.InjectAutoBind;
import io.baratine.inject.InjectionPoint;
import io.baratine.inject.Key;
import io.baratine.service.Service;

/**
 * Baratine core service manager.
 */
public class InjectAutoBindService implements InjectAutoBind
{
  private ServicesAmp _serviceManager;
  
  public InjectAutoBindService(ServicesAmp serviceManager)
  {
    _serviceManager = serviceManager;
  }

  @Override
  public <T> Provider<T> provider(Injector manager, Key<T> key)
  {
    Class<T> rawClass = key.rawClass();
    
    Service service = rawClass.getAnnotation(Service.class);
    
    if (service == null) {
      return null;
    }
    
    if (key.isAnnotationPresent(ServiceImpl.class)) {
      return null;
    }
    
    String address = _serviceManager.address(rawClass);

    if (address != null && ! address.isEmpty()) {
      T proxy = _serviceManager.service(address).as(rawClass);
      
      return ()->proxy;
    }
    else {
      return null;
    }
  }

  @Override
  public <T> Provider<T> provider(Injector manager, 
                                  InjectionPoint<T> ip)
  {
    Service service = ip.annotation(Service.class);
    Class<T> rawClass = ip.key().rawClass();
    
    if (service == null) {
      service = metaService(ip.key());
    }

    if (service == null) {
      service = rawClass.getAnnotation(Service.class);
    }
    
    if (service == null) {
      return null;
    }
    
    String address = _serviceManager.address(rawClass, service.value());

    if (address != null && ! address.isEmpty()) {
      T proxy = _serviceManager.service(address).as(rawClass);
      
      return ()->proxy;
    }
    else {
      return null;
    }
  }
  
  private Service metaService(Key<?> key)
  {
    for (Annotation ann : key.annotations()) {
      Service service = ann.annotationType().getAnnotation(Service.class);

      if (service != null) {
        return service;
      }
    }
    
    for (Class<?> annType : key.annotationTypes()) {
      Service service = annType.getAnnotation(Service.class);

      if (service != null) {
        return service;
      }
    }
    
    return null;
  }
}
