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

package com.caucho.v5.web.builder;

import java.util.Objects;
import java.util.function.Supplier;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.service.ServiceBuilderAmp;

import io.baratine.inject.Key;
import io.baratine.service.ServiceRef;
import io.baratine.service.ServiceRef.ServiceBuilder;
import io.baratine.web.IncludeWeb;
import io.baratine.web.WebBuilder;

public class ServiceBuilderWebImpl implements ServiceRef.ServiceBuilder, IncludeWeb
{
  private WebServerBuilderImpl _serverBuilder;
  
  private Supplier<?> _supplier;
  private Class<?> _serviceClass;
  
  private String _address;

  private Key<?> _key;

  private boolean _isAddressAuto;
  
  ServiceBuilderWebImpl(WebServerBuilderImpl serverBuilder,
                        Supplier<?> supplier)
  {
    Objects.requireNonNull(supplier);
    
    _serverBuilder = serverBuilder;
    _supplier = supplier;
    throw new UnsupportedOperationException();
  }
  
  ServiceBuilderWebImpl(WebServerBuilderImpl serverBuilder,
                        Key<?> key,
                        Class<?> serviceClass)
  {
    Objects.requireNonNull(serviceClass);
    Objects.requireNonNull(key);
    
    _serverBuilder = serverBuilder;
    _serviceClass = serviceClass;
    _key = key;
  }

  @Override
  public ServiceBuilderWebImpl address(String address)
  {
    _address = address;
    
    return this;
  }

  @Override
  public ServiceBuilderWebImpl addressAuto()
  {
    _isAddressAuto = true;
    
    return this;
  }

  @Override
  public ServiceRef ref()
  {
    //throw new UnsupportedOperationException();
    return null;
  }
  
  public void build(ServiceManagerAmp manager)
  {
    ServiceBuilderAmp builder;
    
    if (_supplier != null) {
      builder = manager.newService(_supplier);
      Objects.requireNonNull(builder);
    }
    else if (_key != null) {
      builder = manager.service(_key, _serviceClass);
      Objects.requireNonNull(builder);
    }
    else if (_serviceClass != null) {
      builder = manager.newService(_serviceClass);
      Objects.requireNonNull(builder);
    }
    else {
      throw new IllegalStateException();
    }
    
    if (_address != null) {
      builder.address(_address);
    }
    
    builder.ref();
  }

  @Override
  public void build(WebBuilder webBuilder)
  {
    ServiceRef.ServiceBuilder builder = null;
    
    if (_supplier != null) {
      //builder = webBuilder.service(_supplier);
    }
    else if (_key != null) {
      builder = webBuilder.service(_key, _serviceClass);
      Objects.requireNonNull(builder);
    }
    else if (_serviceClass != null) {
      builder = webBuilder.service(_serviceClass);
      Objects.requireNonNull(builder);
    }
    else {
      throw new IllegalStateException();
    }
    
    if (_address != null) {
      builder.address(_address);
    }
    else {
      builder.addressAuto();
    }

    builder.ref();
  }

  @Override
  public ServiceBuilder workers(int workers)
  {
    return this;
  }

  @Override
  public ServiceBuilder api(Class<?> api)
  {
    System.out.println("API: " + api);

    return this;
  }
}
