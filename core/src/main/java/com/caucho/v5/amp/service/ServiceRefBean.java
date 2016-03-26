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

package com.caucho.v5.amp.service;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Type;
import java.util.function.Supplier;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.manager.ServiceManagerAmpImpl;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.StubAmp;

/**
 * Lazy service ref using during scanning.
 */
public class ServiceRefBean extends ServiceRefLazy
{
  private String _address;
  private Supplier<?> _serviceSupplier;
  private ServiceConfig _config;
  private boolean _isClosed;
  private Class<?> _serviceClass;
  private StubAmp _stub;

  public ServiceRefBean(ServiceManagerAmpImpl manager, 
                        String path,
                        Class<?> serviceClass, 
                        Supplier<?> serviceSupplier, 
                        ServiceConfig config)
  {
    super(manager);

    _address = path;
    _serviceClass = serviceClass;
    _serviceSupplier = serviceSupplier;
    _config = config;
    
    _stub = manager.stubFactory().createSkeletonMain(_serviceClass, path, config);
    
    Thread.dumpStack();
  }
  
  @Override
  public ServiceManagerAmpImpl manager()
  {
    return (ServiceManagerAmpImpl) super.manager();
  }
  
  @Override
  public String address()
  {
    if (_address != null) {
      return _address;
    }
    else {
      return super.address();
    }
  }
  
  @Override
  public AnnotatedType api()
  {
    return _stub.api();
  }
  
  @Override
  public MethodRefAmp getMethod(String name)
  {
    MethodAmp methodAmp = _stub.getMethod(name);
    
    return new MethodRefImpl(this, methodAmp);
  }
  
  @Override
  public MethodRefAmp getMethod(String name, Type type)
  {
    // XXX: type?
    MethodAmp methodAmp = _stub.getMethod(name);
    
    return new MethodRefImpl(this, methodAmp);
  }
  
  @Override
  public boolean isPublic()
  {
    return _config.isPublic();
  }
  
  @Override
  public boolean isClosed()
  {
    return _isClosed;
  }
  
  @Override
  public ServiceRefAmp start()
  {
    delegate().start();
    
    return this;
  }

  @Override
  protected ServiceRefAmp newDelegate()
  {
    /*
    ServiceRefAmp serviceRef
      = manager().service(_serviceSupplier, _address, _config);
      */
    
    //ActorAmp actorAmp = manager().createActor(_serviceSupplier.get(), _config);
    //
    //ActorFactoryAmp actorFactory = new ActorFactoryImpl(manager(), _serviceSupplier, _config);
    
    //return serviceRef;
    
    //return manager().service(actorFactory);
    throw new UnsupportedOperationException();
  }
  
  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    _isClosed = true;
    
    super.shutdown(mode);
  }
  
  @Override
  public void close()
  {
    _isClosed = true;
    
    super.close();
  }
  
  @Override
  public String toString()
  {
    if (_address != null) {
      return getClass().getSimpleName() + "[" + _address + "," + _serviceClass.getSimpleName() + "]";
    }
    else {
      return getClass().getSimpleName() + "[anon:" + _serviceClass.getName() + "]";
    }
  }
}
