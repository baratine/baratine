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

package com.caucho.v5.config.inject;

import java.lang.annotation.Annotation;

import javax.inject.Singleton;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.ServicesAmp;

import io.baratine.inject.Bean;
import io.baratine.inject.InjectionPoint;
import io.baratine.service.Service;

/**
 * Producer for baratine
 */
@Singleton
public class BaratineProducer
{
  @Bean
  public ServicesAmp getServiceManager()
  {
    return ServicesAmp.current();
  }
  
  /*
  @Bean
  public ServiceServer getServiceServer()
  {
    return ServiceServer.current();
  }
  */
  
  /*
  @Bean
  public SessionContext getSessionContext()
  {
    return SessionContextJamp.getCurrent();
  }
  */
  
  /*
  @OnLookup
  public Object lookup(InjectionPoint ip)
  {
    Class<?> type = (Class<?>) ip.type();
    
    Lookup lookup = findLookup(ip.annotations());
    
    if (lookup == null) {
      throw new IllegalStateException();
    }
    
    ServicesAmp manager = ServicesAmp.current();
    
    ServiceRefAmp serviceRef = manager.service(lookup.value());
    
    if (type.isAssignableFrom(ServiceRefAmp.class)) {
      return serviceRef;
    }
    
    return serviceRef.as(type);
  }
  
  private Lookup findLookup(Annotation...qualifiers)
  {
    for (Annotation ann : qualifiers) {
      if (ann.annotationType().equals(Lookup.class)) {
        return (Lookup) ann;
      }
    }
    
    return null;
  }
  */
  
  @Service
  public Object service(InjectionPoint ip)
  {
    Class<?> type = ip.key().rawClass();
    
    Service service = findService(ip.annotations());
    
    if (service == null) {
      throw new IllegalStateException();
    }
    
    ServicesAmp manager = ServicesAmp.current();
    
    String address = service.value();
    
    if (address.isEmpty()) {
      Service apiService = type.getAnnotation(Service.class);
      
      if (apiService != null && ! apiService.value().isEmpty()) {
        address = apiService.value();
      }
      else {
        address = "/" + type.getSimpleName();
      }
    }
    
    ServiceRefAmp serviceRef = manager.service(address);
    
    if (type.isAssignableFrom(ServiceRefAmp.class)) {
      return serviceRef;
    }
    
    return serviceRef.as(type);
  }
  
  private Service findService(Annotation...qualifiers)
  {
    for (Annotation ann : qualifiers) {
      if (ann.annotationType().equals(Service.class)) {
        return (Service) ann;
      }
    }
    
    return null;
  }
}

