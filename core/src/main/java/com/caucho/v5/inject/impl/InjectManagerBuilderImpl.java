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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.inject.Scope;
import javax.inject.Singleton;

import com.caucho.v5.config.Configs;
import com.caucho.v5.convert.ConvertAutoBind;
import com.caucho.v5.inject.BindingAmp;
import com.caucho.v5.inject.InjectManagerAmp;
import com.caucho.v5.inject.InjectManagerAmp.InjectBuilderAmp;
import com.caucho.v5.inject.impl.InjectManagerImpl.BindingSet;
import com.caucho.v5.util.L10N;

import io.baratine.config.Config;
import io.baratine.inject.Factory;
import io.baratine.inject.InjectManager;
import io.baratine.inject.InjectManager.BindingBuilder;
import io.baratine.inject.InjectManager.InjectAutoBind;
import io.baratine.inject.InjectManager.InjectBuilder;
import io.baratine.inject.InjectionPoint;
import io.baratine.inject.Key;
import io.baratine.service.Lookup;

/**
 * The injection manager for a given environment.
 */
public class InjectManagerBuilderImpl implements InjectBuilderAmp
{
  private static final L10N L = new L10N(InjectManagerBuilderImpl.class);
  private static final Logger log = Logger.getLogger(InjectManagerBuilderImpl.class.getName());
  
  private final ClassLoader _loader;
  
  private HashMap<Class<?>,Supplier<InjectScope<?>>> _scopeMap = new HashMap<>();
  
  //private InjectScope _scopeDefault = new InjectScopeFactory();
  
  private ValidatorInject _validator = new ValidatorInject();
  
  private HashSet<Class<?>> _qualifierSet = new HashSet<>();
  
  private Config.ConfigBuilder _env = Configs.config();
  
  private ConcurrentHashMap<Class<?>,BindingSet> _producerMap
    = new ConcurrentHashMap<>();
  
  private ArrayList<BindingBuilderImpl> _bindings = new ArrayList<>();
  private ArrayList<InjectAutoBind> _autoBindList = new ArrayList<>();
  private ArrayList<Class<?>> _includeList = new ArrayList<>();
  
  private InjectManagerImpl _injectManager;
  private boolean _isContext;
  
  InjectManagerBuilderImpl(ClassLoader loader)
  {
    _loader = loader;
      
    _scopeMap.put(Singleton.class, InjectScopeSingleton::new);
    _scopeMap.put(Factory.class, InjectScopeFactory::new);
      
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
  public InjectBuilderAmp context(boolean isContext)
  {
    _isContext = isContext;
    
    return this;
  }

  public boolean isContext()
  {
    return _isContext;
  }
  
  @Override
  public <T> BindingBuilder<T> bean(Class<T> type)
  {
    clearManager();
    
    Objects.requireNonNull(type);
    
    _validator.beanClass(type);
    
    BindingBuilderImpl<T> binding = new BindingBuilderImpl<>(this, type);
    
    _bindings.add(binding);
    
    return binding;
  }
  
  @Override
  public <T> BindingBuilder<T> bean(T bean)
  {
    clearManager();
    
    Objects.requireNonNull(bean);
    
    BindingBuilderImpl<T> binding = new BindingBuilderImpl<>(this, bean);
    
    _bindings.add(binding);
    
    return binding;
  }

  @Override
  public <T> BindingBuilder<T> provider(Provider<T> provider)
  {
    clearManager();
    
    Objects.requireNonNull(provider);
    
    BindingBuilderImpl<T> binding = new BindingBuilderImpl<>(this, provider);
    
    _bindings.add(binding);
    
    return binding;
  }

  @Override
  public <T, U> BindingBuilder<T> provider(Key<U> parent, Method m)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public InjectBuilder include(Class<?> beanType)
  {
    clearManager();
    
    _includeList.add(beanType);
    
    return this;
  }
  
  private ArrayList<Class<?>> includes()
  {
    return _includeList;
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

  HashMap<Class<?>, Supplier<InjectScope<?>>> scopeMap()
  {
    return new HashMap<>(_scopeMap);
  }
  
  void bind(InjectManagerImpl manager)
  {
    addBean(manager, Key.of(InjectManager.class), ()->manager);
    
    for (Class<?> beanType : includes()) {
      addInclude(manager, beanType);
    }
    
    for (BindingBuilderImpl<?> binding : _bindings) {
      binding.build(manager);
    }
  }
  
  boolean isQualifier(Annotation ann)
  {
    Class<?> annType = ann.annotationType();
    
    if (annType.isAnnotationPresent(Qualifier.class)) {
      return true;
    }
    
    return _qualifierSet.contains(annType);
  }

  private <X> void addInclude(InjectManagerImpl manager,
                           Class<X> beanClass)
  {
    BindingAmp<X> bindingOwner = newBinding(manager, beanClass);
    
    introspectProduces(manager, beanClass, bindingOwner);
  }

  public <T> BindingAmp<T> newBinding(InjectManagerImpl manager,
                                      Class<T> type)
  {
    BindingBuilderImpl<T> builder = new BindingBuilderImpl<>(this, type);

    return builder.producer(manager);
  }
  
  private <X> void introspectProduces(InjectManagerImpl manager,
                                      Class<X> beanClass, 
                                      BindingAmp<X> bindingOwner)
  {
    for (Method method : beanClass.getMethods()) {
      if (! isProduces(method.getAnnotations())) {
        continue;
      }
      
      introspectMethod(manager, method, bindingOwner);
    }
  }
  
  private <T,X> void introspectMethod(InjectManagerImpl manager,
                                      Method method,
                                      BindingAmp<X> ownerBinding)
  {
    if (void.class.equals(method.getReturnType())) {
      throw new IllegalArgumentException(method.toString());
    }
      
    Class<?> []pTypes = method.getParameterTypes();
    
    int ipIndex = findInjectionPoint(pTypes);
      
    if (ipIndex >= 0) {
      BindingAmp<T> binding
        = new ProviderMethodAtPoint(this, ownerBinding, method);
      
      //addProvider(binding);
      manager.addProvider(binding);
    }
    else {
      ProviderMethod producer
        = new ProviderMethod(manager, ownerBinding, method);
        
      manager.addProvider(producer);
    }
  }
  
  private int findInjectionPoint(Class<?> []pTypes)
  {
    for (int i = 0; i < pTypes.length; i++) {
      if (InjectionPoint.class.equals(pTypes[i])) {
        return i;
      }
    }
    
    return -1;
  }

  private boolean isProduces(Annotation []anns)
  {
    if (anns == null) {
      return false;
    }
    
    for (Annotation ann : anns) {
      if (ann.annotationType().isAnnotationPresent(Qualifier.class)) {
        return true;
      }
    }
    
    return false;
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
  
  /*
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
  }
  */
  
  private static RuntimeException error(String msg, Object ...args)
  {
    return new InjectException(L.l(msg, args));
  }
  
  private class BindingBuilderImpl<T>
    implements BindingBuilder<T>
  {
    private InjectBuilder _builder;
    private Key<? super T> _key;
    
    private Class<? extends T> _impl;
    private Provider<T> _supplier;
    private int _priority;
    
    private Class<? extends Annotation> _scopeType = Singleton.class;
    
    BindingBuilderImpl(InjectBuilder builder, 
                       Class<T> type)
    {
      Objects.requireNonNull(builder);
      
      _builder = builder;
      
      Objects.requireNonNull(type);
      
      _key = Key.of(type);
      _impl = type;
    }
    
    BindingBuilderImpl(InjectBuilder builder, 
                       T bean)
    {
      Objects.requireNonNull(builder);
      
      _builder = builder;
      
      Objects.requireNonNull(bean);
      
      _key = (Key) Key.of(bean.getClass());
      _supplier = ()->bean;
    }
    
    BindingBuilderImpl(InjectBuilder builder, 
                       Provider<T> provider)
    {
      Objects.requireNonNull(builder);
      
      _builder = builder;
      
      Objects.requireNonNull(provider);
      
      _supplier = provider;
    }
    
    @Override
    public BindingBuilder<T> to(Class<? super T> type)
    {
      Objects.requireNonNull(type);
      
      _key = Key.of(type);
      
      return this;
    }
    
    @Override
    public BindingBuilder<T> to(Key<? super T> key)
    {
      Objects.requireNonNull(key);
      
      _key = key;
      
      return this;
    }
    
    @Override
    public BindingBuilderImpl<T> priority(int priority)
    {
      _priority = priority;
      
      return this;
    }
    
    @Override
    public BindingBuilderImpl<T> scope(Class<? extends Annotation> scopeType)
    {
      Objects.requireNonNull(scopeType);
      
      if (! scopeType.isAnnotationPresent(Scope.class)) {
        throw error("'@{0}' is an invalid scope type because it is not annotated with @Scope",
                    scopeType.getSimpleName());
      }
      
      if (_scopeMap.get(scopeType) == null) {
        throw error("'@{0}' is an unsupported scope. Only @Singleton and @Factory are supported.",
                    scopeType.getSimpleName());
        
      }
      
      _scopeType = scopeType;
      
      return this;
    }
    
    void build(InjectManagerImpl manager)
    {
      manager.addProvider(producer(manager));
    }
    
    BindingAmp<T> producer(InjectManagerImpl manager)
    {
      BindingAmp<T> producer;
      
      Provider<T> supplier;
      
      if (_impl != null) {
        InjectScope<T> scope = manager.scope(_scopeType);
        
        ProviderConstructor<T> provider
          = new ProviderConstructor(manager, _key, _priority, scope, _impl);
        
        return provider;
      }
      else if (_supplier != null) {
        supplier = _supplier;
      }
      else {
        //supplier = ()->manager.instance(_key);
        throw new UnsupportedOperationException();
      }
      
      producer = new ProviderDelegate<T>(manager, (Key) _key, _priority, (Provider) supplier); 
      
      return producer;
    }
  }
}

