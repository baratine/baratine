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

package com.caucho.v5.bartender.pod;

import io.baratine.service.Result;
import io.baratine.service.ServiceExceptionUnavailable;
import io.baratine.spi.Headers;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.service.MethodRefBase;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MethodAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.util.L10N;

/**
 * Method for a pod node call.
 * 
 * The method calls the owning service, and fails over if the primary is
 * down.
 * 
 * The node has already been selected using the hash of the URL.
 */
public class MethodRefPod extends MethodRefBase
{
  private static final L10N L = new L10N(MethodRefPod.class);
  private static final Logger log = Logger.getLogger(MethodRefPod.class.getName());
  
  private final ServiceRefPod _serviceRef;
  private final String _name;
  private final Type _type;
  
  private final MethodPod _methodPod;
  
  private MethodRefActive _methodRefActive;
  private MethodRefActive _methodRefLocal;
  
  MethodRefPod(ServiceRefPod serviceRefPodNode,
               String name,
               Type type)
  {
    _serviceRef = serviceRefPodNode;
    _name = name;
    _type = type;
    
    _methodPod = new MethodPod(serviceRefPodNode, name, type);
    // XXX: adapter
  }
  
  @Override
  public String getName()
  {
    return _name;
  }

  @Override
  public ServiceRefAmp getService()
  {
    return _serviceRef;
  }
  
  @Override
  public Type []getParameterTypes()
  {
    MethodRefAmp localMethod = findLocalMethod();
    
    if (localMethod != null) {
      return localMethod.getParameterTypes();
    }
    else {
      return super.getParameterTypes();
    }
  }
  
  @Override
  public Type getReturnType()
  {
    MethodRefAmp localMethod = findLocalMethod();
    
    if (localMethod != null) {
      return localMethod.getReturnType();
    }
    else {
      return Object.class;
    }
  }
  
  @Override
  public Class<?> []getParameterClasses()
  {
    MethodRefAmp localMethod = findLocalMethod();
    
    if (localMethod != null) {
      return localMethod.getParameterClasses();
    }
    else {
      return null;
    }
  }
  
  @Override
  public Annotation []getAnnotations()
  {
    MethodRefAmp localMethod = findLocalMethod();
    
    if (localMethod != null) {
      return localMethod.getAnnotations();
    }
    else {
      return null;
    }
  }
  
  @Override
  public boolean isVarArgs()
  {
    MethodRefAmp localMethod = findLocalMethod();
    
    if (localMethod != null) {
      return localMethod.isVarArgs();
    }
    else {
      return super.isVarArgs();
    }
  }

  @Override
  public InboxAmp getInbox()
  {
    return _serviceRef.inbox();
  }
  
  @Override
  public MethodAmp method()
  {
    return _methodPod;
  }

  @Override
  public void send(Headers headers,
                   Object... args)
  {
    MethodRefAmp method = findActiveMethod();

    method.send(headers, args);
  }

  @Override
  public <T> void query(Headers headers,
                        Result<T> result, 
                        long timeout, TimeUnit timeUnit,
                        Object... args)
  {
    MethodRefAmp method = findActiveMethod();

    method.query(headers, result, timeout, timeUnit, args);
  }

  /*
  @Override
  public <T> void stream(Headers headers,
                         ResultStream<T> result, 
                         long timeout, TimeUnit timeUnit,
                         Object... args)
  {
    try {
      MethodRefAmp method = findActiveMethod();

      method.stream(headers, result, timeout, timeUnit, args);
    } catch (Throwable e) {
      result.fail(e);
    }
  }
  */
  
  private MethodRefAmp findLocalMethod()
  {
    ServiceRefAmp serviceRefLocal = _serviceRef.getLocalService();
    
    if (serviceRefLocal == null) {
      return null;
    }
    
    MethodRefActive methodRefLocal = _methodRefLocal;
    MethodRefAmp methodRef;
    
    if (methodRefLocal != null) {
      methodRef = methodRefLocal.getMethod(serviceRefLocal);
      
      if (methodRef != null) {
        return methodRef;
      }
    }

    if (_type != null) {
      methodRef = serviceRefLocal.getMethod(_name, _type);
    }
    else {
      methodRef = serviceRefLocal.getMethod(_name);
    }
    
    _methodRefLocal = new MethodRefActive(serviceRefLocal, methodRef);
    
    return methodRef;
  }
  
  @Override
  public MethodRefAmp getActive()
  {
    return findActiveMethod();
  }
  
  private MethodRefAmp findActiveMethod(int hash)
  {
    ServiceRefAmp serviceRefActive = _serviceRef.getActiveService(hash);

    if (_type != null) {
      return serviceRefActive.getMethod(_name, _type);
    }
    else {
      return serviceRefActive.getMethod(_name);
    }
  }
  
  private MethodRefAmp findActiveMethod()
  {
    ServiceRefAmp serviceRefActive = _serviceRef.getActiveService();
    
    if (serviceRefActive == null) {
      throw new ServiceExceptionUnavailable(L.l("No service available for {0}",
                                                getService()));
    }

    MethodRefActive methodRefActive = _methodRefActive;
    MethodRefAmp methodRef;
    
    if (methodRefActive != null) {
      methodRef = methodRefActive.getMethod(serviceRefActive);
      
      if (methodRef != null) {
        return methodRef;
      }
    }
    
    if (_type != null) {
      methodRef = serviceRefActive.getMethod(_name, _type);
    }
    else {
      methodRef = serviceRefActive.getMethod(_name);
    }
    
    _methodRefActive = new MethodRefActive(serviceRefActive, methodRef);
    
    return methodRef;
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + getName()
            + "," + _serviceRef.address() + "]");
  }
  
  private static class MethodRefActive {
    private final ServiceRefAmp _serviceRef;
    private final MethodRefAmp _methodRef;
    
    MethodRefActive(ServiceRefAmp serviceRef,
                    MethodRefAmp methodRef)
    {
      _serviceRef = serviceRef;
      _methodRef = methodRef;
    }
    
    MethodRefAmp getMethod(ServiceRefAmp serviceRefActive)
    {
      if (_serviceRef == serviceRefActive) {
        return _methodRef;
      }
      else {
        return null;
      }
    }
  }
}
