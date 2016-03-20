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
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.actor.StubGenerator;
import com.caucho.v5.amp.actor.StubGeneratorService;
import com.caucho.v5.amp.journal.JournalFactoryAmp;
import com.caucho.v5.amp.journal.JournalFactoryBase;
import com.caucho.v5.amp.service.ServiceBuilderAmp;
import com.caucho.v5.amp.spi.ProxyFactoryAmp;
import com.caucho.v5.amp.spi.ServiceManagerBuilderAmp;
import com.caucho.v5.config.Priorities;
import com.caucho.v5.inject.impl.ServiceImpl;
import com.caucho.v5.util.ConcurrentArrayList;
import com.caucho.v5.util.L10N;

import io.baratine.inject.Key;
import io.baratine.service.QueueFullHandler;
import io.baratine.service.ServiceInitializer;
import io.baratine.service.ServiceNode;
import io.baratine.service.ServiceRef;
import io.baratine.service.ServiceRef.ServiceBuilder;

/**
 * Builder for a ServiceManager.
 * 
 * <pre><code>
 * ServiceManager manager = ServiceManager.newManager().start();
 * </code></pre>
 */
public class ServiceManagerBuilderImpl implements ServiceManagerBuilderAmp
{
  private static final L10N L = new L10N(ServiceManagerBuilderImpl.class);
  private static final Logger log
    = Logger.getLogger(ServiceManagerBuilderImpl.class.getName());
  
  
  private String _name;
  private String _debugId;
  
  private ProxyFactoryAmp _proxyFactory;
  private JournalFactoryAmp _journalFactory;
  private QueueFullHandler _queueFullHandler;
  private boolean _isContextManager = true;
  private ServiceNode _podNode;
  private ClassLoader _loader = Thread.currentThread().getContextClassLoader();
  
  private boolean _isAutoStart = true;
  
  private boolean _isAutoServices;
  
  private boolean _isDebug;
  private long _debugQueryTimeout;
  private Supplier<Executor> _systemExecutor;
  
  private ArrayList<ServiceBuilderStart> _services = new ArrayList<>();

  //private ServiceManagerBuildTemp _buildManager;

  private ServiceManagerAmp _manager;
  
  private ConcurrentArrayList<StubGenerator> _stubGenerators
    = new ConcurrentArrayList<>(StubGenerator.class);
  
  public ServiceManagerBuilderImpl()
  {
    journalFactory(new JournalFactoryBase());
    name("system");
    
    if (log.isLoggable(Level.FINER)) {
      debug(true);
    }
    
    stubGenerator(new StubGeneratorService());
  }
  
  @Override
  public ServiceManagerBuilderImpl name(String name)
  {
    _name = name;
    
    return this;
  }
  
  @Override
  public String name()
  {
    return _name;
  }
  
  @Override 
  public ServiceManagerBuilderImpl classLoader(ClassLoader loader)
  {
    _loader = loader;
    
    return this;
  }
  
  @Override 
  public ClassLoader classLoader()
  {
    return _loader;
  }
  
  public void debugId(String debugId)
  {
    _debugId = debugId;
  }
  
  @Override
  public String debugId()
  {
    if (_debugId != null) {
      return _debugId;
    }
    else {
      return _name;
    }
  }

  /*
  @Override
  public LookupManagerBuilderAmp getBrokerFactory()
  {
    return _brokerFactory;
  }

  @Override
  public ServiceManagerBuilderAmp setBrokerFactory(LookupManagerBuilderAmp factory)
  {
    _brokerFactory = factory;

    return this;
  }
  */

  @Override
  public ProxyFactoryAmp proxyFactory()
  {
    return _proxyFactory;
  }

  @Override
  public ServiceManagerBuilderAmp proxyFactory(ProxyFactoryAmp factory)
  {
    _proxyFactory = factory;

    return this;
  }
  
  @Override
  public ServiceNode podNode()
  {
    return _podNode;
  }
  
  @Override
  public ServiceManagerBuilderImpl podNode(ServiceNode podNode)
  {
    _podNode = podNode;
    
    return this;
  }

  @Override
  public JournalFactoryAmp journalFactory()
  {
    return _journalFactory;
  }

  @Override
  public ServiceManagerBuilderAmp journalFactory(JournalFactoryAmp factory)
  {
    Objects.requireNonNull(factory);

    _journalFactory = factory;

    return this;
  }

  /**
   * Sets the journal maxCount default value, which itself defaults to 0.
   * 
   * maxCount is used to limit journal flushes, so checkpoints are
   * issued after maxCount requests.
   */
  @Override
  public ServiceManagerBuilderAmp journalMaxCount(int maxCount)
  {
    _journalFactory.setMaxCount(maxCount);

    return this;
  }

  @Override
  public ServiceManagerBuilderAmp setJournalDelay(long timeout)
  {
    _journalFactory.setDelay(timeout);

    return this;
  }

  @Override
  public long getJournalDelay()
  {
    return _journalFactory.getDelay();
  }
  
  @Override
  public ServiceManagerBuilderAmp queueFullHandler(QueueFullHandler handler)
  {
    _queueFullHandler = handler;
    
    return this;
  }
  
  @Override
  public QueueFullHandler getQueueFullHandler()
  {
    return _queueFullHandler;
  }
  
  @Override
  public ServiceManagerBuilderAmp contextManager(boolean isContextManager)
  {
    _isContextManager = isContextManager;
    
    return this;
  }
  
  @Override
  public boolean isContextManager()
  {
    return _isContextManager;
  }
  
  @Override
  public ServiceManagerBuilderImpl autoStart(boolean isAutoStart)
  {
    _isAutoStart = isAutoStart;
    
    return this;
  }
  
  @Override
  public boolean isAutoStart()
  {
    return _isAutoStart;
  }
  
  /**
   * Auto services scans META-INF/services for built-in services.
   */
  @Override
  public ServiceManagerBuilderImpl autoServices(boolean isAutoServices)
  {
    _isAutoServices = isAutoServices;
    
    return this;
  }
  
  @Override
  public boolean isAutoServices()
  {
    return _isAutoServices;
  }
  
  @Override
  public boolean isDebug()
  {
    return _isDebug;
  }
  
  @Override
  public ServiceManagerBuilderImpl debug(boolean isDebug)
  {
    _isDebug = isDebug;
    
    return this;
  }
  
  @Override
  public long getDebugQueryTimeout()
  {
    return _debugQueryTimeout;
  }
  
  @Override
  public ServiceManagerBuilderImpl debugQueryTimeout(long timeout)
  {
    _debugQueryTimeout = timeout;
    
    return this;
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
  public ServiceManagerBuilderImpl stubGenerator(StubGenerator factory)
  {
    Objects.requireNonNull(factory);
    _stubGenerators.add(factory);
        
    return this;
  }
  
  @Override
  public StubGenerator []stubGenerators()
  {
    StubGenerator []factories = _stubGenerators.toArray();
    Arrays.sort(factories, Priorities::compareHighFirst);

    return factories;
  }
  
  @Override
  public ServiceManagerAmp start()
  {
    ServiceManagerAmp manager = get();

    manager.start();
    
    return manager;
  }
  
  @Override
  public ServiceManagerAmp getRaw()
  {
    ServiceManagerAmp manager = _manager;
    
    if (manager == null) {
      manager = _manager = newManager();
    
      /*
      if (_buildManager != null) {
        _buildManager.delegate(manager);
      }
      */
    
      if (isContextManager()) {
        initAutoServices(manager);
      }
    }
    
    return manager;
  }
  
  @Override
  public ServiceManagerAmp get()
  {
    ServiceManagerAmp manager = getRaw();
    
    ArrayList<ServiceBuilderStart> services = new ArrayList<>(_services);
    _services.clear();
    
    try {
      for (ServiceBuilderStart service : services) {
        ServiceRef ref = service.ref();
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw e;
    }
    
    return manager;
  }

  protected ServiceManagerAmpImpl newManager()
  {
    return new ServiceManagerAmpImpl(this);
  }

  /*
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
  */

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
  
  protected void initAutoServices(ServiceManagerAmp manager)
  {
    if (! isAutoServices()) {
      return;
    }
    
    ArrayList<ServiceInitializer> providerList = new ArrayList<>();

    Iterator<ServiceInitializer> iter;

    iter = ServiceLoader.load(ServiceInitializer.class).iterator();

    while (iter.hasNext()) {
      try {
        providerList.add(iter.next());
      } catch (Throwable e) {
        if (log.isLoggable(Level.FINER)) {
          log.log(Level.FINER, e.toString(), e);
        }
        else {
          log.fine(L.l("{0} while processing {1}",
                       e.toString(), ServiceInitializer.class.getName()));
        }
      }
    }

    Collections.sort(providerList, (a,b)->
        a.getClass().getSimpleName().compareTo(b.getClass().getSimpleName()));
    
    for (ServiceInitializer provider : providerList) {
      try {
        provider.init(manager);
      } catch (Throwable e) {
        if (log.isLoggable(Level.FINER)) {
          log.log(Level.FINER, e.toString(), e);
        }
        else {
          log.fine(L.l("'{0}' while processing {1}", e.toString(), provider));
        }
      }
    }
  }

  
  //
  // internal classes
  //
  
  /**
   * Service registered by the builder.
   */
  
  private class ServiceBuilderStart implements ServiceRef.ServiceBuilder
  {
    private Key<?> _key;
    private Class<?> _type;
    private Supplier<?> _supplier;
    private String _address = "";
    private int _workers = -1;
    private boolean _isAddressAuto = true;
    private ServiceRefAmp _ref;
    
    ServiceBuilderStart(Key<?> key, Class<?> type)
    {
      _key = key;
      _type = type;
    }
    
    ServiceBuilderStart(Supplier<?> supplier)
    {
      _supplier = supplier;
    }

    @Override
    public ServiceBuilder api(Class<?> api)
    {
      Objects.requireNonNull(api);
      
      _type = api;
      
      return this;
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
      if (_ref != null) {
        return _ref;
      }
      
      ServiceBuilderAmp builder;
      
      ServiceManagerAmp manager = _manager;

      if (_supplier != null) {
        builder = manager.newService(_supplier);
        
        if (_type != null) {
          builder.api(_type);
        }
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
      
      if (_workers >= 0) {
        builder.workers(_workers);
      }
      
      _ref = builder.ref();
      
      return _ref;
    }
  }
}
