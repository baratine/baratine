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
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.journal.JournalFactoryBase;
import com.caucho.v5.amp.spi.ServiceBuilderAmp;
import com.caucho.v5.config.Priorities;
import com.caucho.v5.inject.impl.ServiceImpl;
import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.util.ConcurrentArrayList;

import io.baratine.inject.Key;
import io.baratine.service.Service;
import io.baratine.service.ServiceRef;
import io.baratine.service.Vault;
import io.baratine.service.ServiceRef.ServiceBuilder;

/**
 * Default AMP provider.
 */
public class ServiceManagerBuilderImpl extends ServiceManagerBuilderBase
{
  private static final Logger log = Logger.getLogger(ServiceManagerBuilderImpl.class.getName());
  
  private Supplier<Executor> _systemExecutor;
  
  private ArrayList<ServiceBuilderStart> _services = new ArrayList<>();

  private ServiceManagerBuildTemp _buildManager;

  private ServiceManagerAmp _manager;
  
  private ConcurrentArrayList<ActorGenerator> _actorFactories
    = new ConcurrentArrayList<>(ActorGenerator.class);
  
  public ServiceManagerBuilderImpl()
  {
    super.setJournalFactory(new JournalFactoryBase());
    super.name("system");
    
    if (log.isLoggable(Level.FINER)) {
      debug(true);
    }
  }

  @Override
  public ServiceManagerBuilderImpl systemExecutor(Supplier<Executor> supplier)
  {
    Objects.requireNonNull(supplier);
    
    _systemExecutor = supplier;
    
    return this;
  }
  
  @Override
  public Supplier<Executor> systemExecutor()
  {
    return _systemExecutor;
  }
  
  @Override
  public ServiceManagerBuilderImpl actorGenerator(ActorGenerator factory)
  {
    Objects.requireNonNull(factory);
    _actorFactories.add(factory);
    
    return this;
  }
  
  @Override
  public ActorGenerator []actorGenerators()
  {
    ActorGenerator []factories = _actorFactories.toArray();
    Arrays.sort(factories, Priorities::compare);
    
    return factories;
  }
  
  @Override
  public ServiceManagerAmp start()
  {
    ServiceManagerAmp manager = get();
    
    for (ServiceBuilderStart service : _services) {
      service.build(manager);
    }

    if (isAutoStart()) {
      manager.start();
    }
    
    return manager;
  }
  
  @Override
  public ServiceManagerAmp get()
  {
    ServiceManagerAmp manager = _manager;
    
    if (manager == null) {
      manager = _manager = newManager();
    
      if (_buildManager != null) {
        _buildManager.delegate(manager);
      }
    
      if (isContextManager()) {
        bind(manager);
      }
    }
    
    return manager;
  }
  
  protected AmpManager newManager()
  {
    return new AmpManager(this);
  }
  
  @Override
  public ServiceManagerAmp managerBuild()
  {
    if (_manager != null) {
      return _manager;
    }
    else if (_buildManager == null) {
      _buildManager = new ServiceManagerBuildTemp(this);
    }
    
    return _buildManager;
  }

  @Override
  public <T> ServiceBuilder service(Class<T> type)
  {
    Key<?> key = Key.of(type, ServiceImpl.class);
    
    ServiceBuilderStart service = new ServiceBuilderStart(key, type);
    
    _services.add(service);
    
    return service;
  }

  @Override
  public <T> ServiceBuilder service(Key<?> key, Class<T> type)
  {
    Objects.requireNonNull(key);
    Objects.requireNonNull(type);
    
    ServiceBuilderStart service = new ServiceBuilderStart(key, type);
    
    _services.add(service);
    
    return service;
  }

  @Override
  public <T> ServiceBuilder service(Supplier<? extends T> supplier)
  {
    Objects.requireNonNull(supplier);
    
    ServiceBuilderStart service = new ServiceBuilderStart(supplier);
    
    _services.add(service);
    
    return service;
  }
  
  //
  // internal classes
  //
  
  private class ServiceBuilderStart implements ServiceRef.ServiceBuilder
  {
    private Key<?> _key;
    private Class<?> _type;
    private Supplier<?> _supplier;
    private String _address = "";
    private int _workers;
    private boolean _isAddressAuto;
    
    ServiceBuilderStart(Key<?> key, Class<?> type)
    {
      _key = key;
      _type = type;
      
      /*
      Service service = type.getAnnotation(Service.class);
      
      if (service != null) {
        _address = service.value();
      }
      
      if ("".equals(_address)) {
        addressAuto();
      }
      */
    }
    
    ServiceBuilderStart(Supplier<?> supplier)
    {
      _supplier = supplier;
    }

    @Override
    public ServiceBuilder address(String address)
    {
      _address = address;
      
      return this;
    }

    @Override
    public ServiceBuilder addressAuto()
    {
      _isAddressAuto = true;
      
      return this;
    }

    @Override
    public ServiceBuilder workers(int workers)
    {
      _workers = workers;

      return this;
    }

    @Override
    public ServiceRef ref()
    {
      //throw new UnsupportedOperationException();
      return null;
    }

    private void build(ServiceManagerAmp manager)
    {
      ServiceBuilderAmp builder;
      
      if (_supplier != null) {
        builder = manager.newService(_supplier);
      }
      else {
        builder = manager.service(_key, _type);
      }
      
      if (_address != null && ! _address.isEmpty()) {
        builder.address(_address);
      }
      else if (_isAddressAuto) {
        builder.addressAuto();
      }
      
      builder.ref();
    }
  }
}
