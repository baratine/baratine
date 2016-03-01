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

package com.caucho.v5.inject.impl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.inject.Provider;
import javax.inject.Singleton;

import com.caucho.v5.config.Configs;
import com.caucho.v5.convert.ConvertAutoBind;
import com.caucho.v5.inject.BindingAmp;
import com.caucho.v5.inject.InjectManagerAmp;
import com.caucho.v5.inject.InjectManagerAmp.InjectBuilderRootAmp;
import com.caucho.v5.inject.impl.InjectManagerImpl.BindingSet;

import io.baratine.config.Config;
import io.baratine.inject.InjectManager;
import io.baratine.inject.InjectManager.BindingBuilder;
import io.baratine.inject.InjectManager.InjectAutoBind;
import io.baratine.inject.InjectManager.InjectBuilder;
import io.baratine.inject.InjectManager.InjectBuilderRoot;
import io.baratine.inject.InjectManager.ScopeBuilder;
import io.baratine.inject.Key;
import io.baratine.service.Lookup;

/**
 * The injection manager for a given environment.
 */
public class InjectManagerBuilderImpl implements InjectBuilderRootAmp
{
  private static final Logger log = Logger.getLogger(InjectManagerBuilderImpl.class.getName());
  
  private final ClassLoader _loader;
  
  private HashMap<Class<?>,InjectScope> _scopeMap = new HashMap<>();
  
  private InjectScope _scopeDefault = new InjectScopeDefault();
  
  private HashSet<Class<?>> _qualifierSet = new HashSet<>();
  
  private Config.ConfigBuilder _env = Configs.config();
  
  private ConcurrentHashMap<Class<?>,BindingSet> _producerMap
    = new ConcurrentHashMap<>();
  
  private ArrayList<BindingBuilderImpl> _bindings = new ArrayList<>();
  private ArrayList<InjectAutoBind> _autoBindList = new ArrayList<>();
  private ArrayList<Class<?>> _beanList = new ArrayList<>();
  
  private InjectManagerImpl _injectManager;
  private boolean _isContext;
  
  InjectManagerBuilderImpl(ClassLoader loader)
  {
    _loader = loader;
      
    _scopeMap.put(Singleton.class, new InjectScopeSingleton());
      
    _qualifierSet.add(Lookup.class);
    
    _autoBindList.add(new ConvertAutoBind());
  }

  ClassLoader getClassLoader()
  {
    return _loader;
  }
  
  Map<Class<?>,BindingSet> getProducerMap()
  {
    return _producerMap;
  }
  
  @Override
  public InjectBuilderRootAmp context(boolean isContext)
  {
    _isContext = isContext;
    
    return this;
  }

  public boolean isContext()
  {
    return _isContext;
  }
  
  @Override
  public <T> BindingBuilder<T> bind(Class<T> type)
  {
    clearManager();
    
    Objects.requireNonNull(type);
    
    BindingBuilderImpl<T> binding = new BindingBuilderImpl<>(this, Key.of(type));
    
    _bindings.add(binding);
    
    return binding;
  }
  
  @Override
  public <T> BindingBuilder<T> bind(Key<T> key)
  {
    clearManager();
    
    Objects.requireNonNull(key);
    
    BindingBuilderImpl<T> binding = new BindingBuilderImpl<>(this, key);
    
    _bindings.add(binding);
    
    return binding;
  }
  
  @Override
  public InjectBuilderRoot include(Class<?> beanType)
  {
    clearManager();
    
    _beanList.add(beanType);
    
    return this;
  }
  
  ArrayList<Class<?>> beans()
  {
    return _beanList;
  }
  
  Config config()
  {
    return _env.get();
  }
  
  @Override
  public InjectManagerAmp get()
  {
    if (_injectManager == null) {
      _injectManager = new InjectManagerImpl(this); 
    }
    
    return _injectManager;
  }
  
  private void clearManager()
  {
    _injectManager = null;
  }

  @Override
  public InjectBuilder autoBind(InjectAutoBind autoBind)
  {
    clearManager();
    
    Objects.requireNonNull(autoBind);
    
    _autoBindList.add(autoBind);
    
    return this;
  }
  
  List<InjectAutoBind> autoBind()
  {
    return _autoBindList;
  }
  
  void bind(InjectManagerImpl manager)
  {
    addBean(manager, Key.of(InjectManager.class), ()->manager);
    
    for (BindingBuilderImpl<?> binding : _bindings) {
      binding.build(manager);
    }
  }
  
  private <T> void addBean(InjectManagerImpl manager,
                           Key<T> key, 
                           Provider<? extends T> supplier)
  {
    int priority = 0;
    
    ProviderDelegate<T> producer
      = new ProviderDelegate<>(manager, key, priority, supplier); 
  
    manager.addProvider(producer);
  }
  
  private static class InjectBuilderChild implements InjectBuilder
  {
    private final InjectBuilderRoot _builder;
    
    InjectBuilderChild(InjectBuilderRoot builder)
    {
      Objects.requireNonNull(builder);
      
      _builder = builder;
    }

    @Override
    public <T> BindingBuilder<T> bind(Class<T> api)
    {
      return _builder.bind(api);
    }

    @Override
    public <T> BindingBuilder<T> bind(Key<T> key)
    {
      return _builder.bind(key);
    }

    @Override
    public InjectBuilder autoBind(InjectAutoBind autoBind)
    {
      return _builder.autoBind(autoBind);
    }

    /*
    @Override
    public InjectManager get()
    {
      return _builder.get();
    }
    */
  }
  
  private static class BindingBuilderImpl<T> extends InjectBuilderChild
    implements BindingBuilder<T>, ScopeBuilder
  {
    private Key<T> _key;
    
    private Class<? extends T> _impl;
    private Provider<? extends T> _supplier;
    private int _priority;
    
    BindingBuilderImpl(InjectBuilderRoot builder, 
                       Key<T> key)
    {
      super(builder);
      
      Objects.requireNonNull(key);
      
      _key = key;
    }
    
    @Override
    public <U extends T> ScopeBuilder to(Class<U> impl)
    {
      _impl = impl;
      
      return this;
    }
    
    @Override
    public BindingBuilderImpl<T> priority(int priority)
    {
      _priority = priority;
      
      return this;
    }
    
    @Override
    public <U extends T> ScopeBuilder toProvider(Provider<U> supplier)
    {
      Objects.requireNonNull(supplier);
      
      _supplier = supplier;
      
      return this;
    }
    
    void build(InjectManagerImpl manager)
    {
      BindingAmp<T> producer;
      
      Provider<? extends T> supplier;
      
      if (_impl != null) {
        ProviderConstructor<T> provider
          = new ProviderConstructor(manager, _key, _priority, _impl);
        
        manager.addProvider(provider);
        
        return;
      }
      else if (_supplier != null) {
        supplier = _supplier;
      }
      else {
        supplier = ()->manager.instance(_key);
      }
      
      producer = new ProviderDelegate<>(manager, _key, _priority, supplier); 
      
      manager.addProvider(producer);
    }

    @Override
    public void toSupplier(Key<?> baseKey, Method m)
    {
      // TODO Auto-generated method stub
      
    }
  }
}

