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

package com.caucho.v5.amp.marshal;

import java.lang.reflect.Type;
import java.util.Objects;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.service.ServiceRefWrapper;
import com.caucho.v5.amp.spi.MethodRefAmp;

/**
 * ServiceRef wrapper that imports from a foreign class loader.
 */
public class ServiceRefImport extends ServiceRefWrapper implements ImportAware
{
  private final ServiceRefAmp _delegate;
  private final ServicesAmp _manager;
  
  private final PodImport _moduleImport;

  public ServiceRefImport(ServiceRefAmp delegate,
                          PodImport moduleImport)
  {
    if (delegate instanceof ImportAware) {
      System.out.println("DUPLICATE_IMPORT: " + delegate);
      Thread.dumpStack();
    }
    
    _delegate = delegate;
    _manager = moduleImport.getImportManager();
    _moduleImport = moduleImport;
    
    Objects.requireNonNull(delegate);
    Objects.requireNonNull(moduleImport);
  }
  
  @Override
  public ServiceRefAmp delegate()
  {
    return _delegate;
  }
  
  @Override
  public ServiceRefAmp export(ClassLoader importLoader)
  {
    ClassLoader exportLoader = _moduleImport.getExportLoader();
    
    PodImport moduleImport;
    
    moduleImport = PodImportContext.create(importLoader).getPodImport(exportLoader);
    
    return new ServiceRefImport(_delegate, moduleImport); 
  }
  
  @Override
  public ServiceRefAmp start()
  {
    delegate().start();
    
    return this;
  }

  public PodImport getModuleImport()
  {
    return _moduleImport;
  }

  @Override
  public ServiceRefAmp bind(String address)
  {
    services().bind(this, address);
    
    return this;
  }
  
  @Override
  public ServicesAmp services()
  {
    return _manager;
  }
  
  @Override
  public ServiceRefAmp onLookup(String path)
  {
    ServiceRefAmp serviceRef = delegate().onLookup(path);
    
    if (serviceRef != null) {
      System.out.println("SRI-ONL: " + serviceRef);
      return serviceRef;
    }
    else {
      return null;
    }
  }
  
  @Override
  public MethodRefAmp methodByName(String methodName)
  {
    MethodRefAmp methodRef = delegate().methodByName(methodName);
    
    if (methodRef != null) {
      return new MethodRefImport(this, methodRef, methodName, Object.class);
    }
    else {
      return null;
    }
  }
  
  @Override
  public MethodRefAmp methodByName(String methodName, Type retType)
  {
    MethodRefAmp methodRef = delegate().methodByName(methodName);
    
    if (methodRef != null) {
      return new MethodRefImport(this, methodRef, methodName, (Class<?>) retType);
    }
    else {
      return null;
    }
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + address() + "," + _moduleImport.getExportLoader() + "]";
  }
}
