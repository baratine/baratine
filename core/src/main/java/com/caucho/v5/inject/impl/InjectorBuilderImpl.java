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
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.inject.Scope;
import javax.inject.Singleton;

import com.caucho.v5.config.Configs;
import com.caucho.v5.convert.ConvertAutoBind;
import com.caucho.v5.inject.BindingAmp;
import com.caucho.v5.inject.InjectorAmp;
import com.caucho.v5.inject.InjectorAmp.InjectBuilderAmp;
import com.caucho.v5.inject.impl.InjectorImpl.BindingSet;
import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.util.L10N;

import io.baratine.config.Config;
import io.baratine.inject.New;
import io.baratine.inject.InjectionPoint;
import io.baratine.inject.Injector;
import io.baratine.inject.Injector.BindingBuilder;
import io.baratine.inject.Injector.InjectAutoBind;
import io.baratine.inject.Injector.InjectorBuilder;
import io.baratine.inject.Key;
import io.baratine.inject.ParamInject;

/**
 * The injection manager for a given environment.
 */
public class InjectorBuilderImpl implements InjectBuilderAmp
{
  private static final L10N L = new L10N(InjectorBuilderImpl.class);
  private static final Logger log = Logger.getLogger(InjectorBuilderImpl.class.getName());
  private static final Logger initLog = Logger.getLogger("com.baratine.init-log");

  private final ClassLoader _loader;
  
  private HashMap<Class<?>,Supplier<InjectScope<?>>> _scopeMap = new HashMap<>();
  
  //private InjectScope _scopeDefault = new InjectScopeFactory();
  
  private ValidatorInject _validator = new ValidatorInject();
  
  private HashSet<Class<?>> _qualifierSet = new HashSet<>();
  
  private Config.ConfigBuilder _config = Configs.config();
  
  private ConcurrentHashMap<Class<?>,BindingSet> _producerMap
    = new ConcurrentHashMap<>();
  
  private ArrayList<BindingBuilderImpl> _bindings = new ArrayList<>();
  private ArrayList<InjectAutoBind> _autoBindList = new ArrayList<>();
  
  private ArrayList<IncludeBuilder> _includeList = new ArrayList<>();
  
  private InjectorImpl _injectManager;
  private boolean _isContext;
  
  InjectorBuilderImpl(ClassLoader loader)
  {
    initLog.log(Level.FINE, () -> L.l("new InjectorBuilderImpl(${0})", loader));

    _loader = loader;
      
    _scopeMap.put(Singleton.class, InjectScopeSingleton::new);
    _scopeMap.put(New.class, InjectScopeFactory::new);
      
    // _qualifierSet.add(Lookup.class);
    
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

  /*
  @Override
  public <T,X> BindingBuilder<T> function(Function<X,T> function)
  {
    clearManager();
    
    Objects.requireNonNull(function);
    
    BindingBuilderImpl<T> binding = new BindingBuilderImpl<>(this, function);
    
    _bindings.add(binding);
    
    return binding;
  }
  */

  @Override
  public <T, U> BindingBuilder<T> provider(Key<U> parent, Method m)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public <U> void include(Key<U> parent, Method method)
  {
    _includeList.add(new IncludeBuilderMethod(parent, method));
  }
  
  @Override
  public InjectorBuilder include(Class<?> beanType)
  {
    clearManager();
    
    _includeList.add(new IncludeBuilderClass(beanType));
    
    return this;
  }
  
  private ArrayList<IncludeBuilder> includes()
  {
    return _includeList;
  }
  
  @Override
  public InjectorBuilderImpl property(String var, String value)
  {
    _config.add(var, value);
    
    return this;
  }
  
  Config config()
  {
    return _config.get();
  }
  
  @Override
  public InjectorAmp get()
  {
    if (_injectManager == null) {
      _injectManager = new InjectorImpl(this); 
    }
    
    return _injectManager;
  }
  
  private void clearManager()
  {
    _injectManager = null;
  }

  @Override
  public InjectorBuilder autoBind(InjectAutoBind autoBind)
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
  
  void bind(InjectorImpl injector)
  {
    addBean(injector, Key.of(Injector.class), ()->injector);
    
    for (IncludeBuilder include : includes()) {
      include.build(injector);
    }
    
    for (BindingBuilderImpl<?> binding : _bindings) {
      binding.build(injector);
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

  private <X> void addInclude(InjectorImpl injector,
                           Class<X> beanClass)
  {
    BindingAmp<X> bindingOwner = newBinding(injector, beanClass);
    
    introspectProduces(injector, beanClass, bindingOwner);
  }

  public <T> BindingAmp<T> newBinding(InjectorImpl manager,
                                      Class<T> type)
  {
    BindingBuilderImpl<T> builder = new BindingBuilderImpl<>(this, type);

    return builder.producer(manager);
  }
  
  private <X> void introspectProduces(InjectorImpl manager,
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
  
  private <T,X> void introspectMethod(InjectorImpl injector,
                                      Method method,
                                      BindingAmp<X> ownerBinding)
  {
    if (void.class.equals(method.getReturnType())) {
      throw new IllegalArgumentException(method.toString());
    }

    Class<?> []pTypes = method.getParameterTypes();
    Parameter []params = method.getParameters();
    
    int ipIndex = findInjectionPoint(pTypes);
      
    if (ipIndex >= 0) {
      BindingAmp<T> binding
        = new ProviderMethodAtPoint(this, ownerBinding, method);
      
      injector.addProvider(binding);
    }
    else if (findParamInject(params) >= 0) {
      FunctionMethod fun
      = new FunctionMethod(injector, ownerBinding, method,
                           findParamInject(params));

      injector.addFunction(fun);
    }
    else {
      ProviderMethod producer
      = new ProviderMethod(injector, ownerBinding, method);
      
    injector.addProvider(producer);
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
  
  private int findParamInject(Parameter []params)
  {
    for (int i = 0; i < params.length; i++) {
      if (params[i].isAnnotationPresent(ParamInject.class)) {
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

  private <T> void addBean(InjectorImpl manager,
                           Key<T> key, 
                           Provider<? extends T> supplier)
  {
    int priority = 0;
    
    ProviderDelegate<T> producer
      = new ProviderDelegate<>(manager, key, priority, supplier); 
  
    manager.addProvider(producer);
  }

  @Override
  public String toString()
  {
    return this.getClass().getSimpleName() + "["
           + _loader
           + ']';
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
    private InjectorBuilder _builder;
    private Key<? super T> _key;
    
    private Class<? extends T> _impl;
    private Provider<T> _provider;
    private Function<?,T> _function;
    private int _priority;
    
    private Class<? extends Annotation> _scopeType = Singleton.class;
    
    BindingBuilderImpl(InjectorBuilder builder, 
                       Class<T> type)
    {
      Objects.requireNonNull(builder);
      
      _builder = builder;
      
      Objects.requireNonNull(type);
      
      _key = Key.of(type);
      _impl = type;
    }
    
    BindingBuilderImpl(InjectorBuilder builder, 
                       T bean)
    {
      Objects.requireNonNull(builder);
      
      _builder = builder;
      
      Objects.requireNonNull(bean);
      
      Class<?> beanClass = bean.getClass();
      Type type = beanClass;
      
      if (beanClass.isAnonymousClass()) {
        if (! Object.class.equals(beanClass.getSuperclass())) {
          type = beanClass.getGenericSuperclass();
        }
        else {
          type = beanClass.getGenericInterfaces()[0];
        }
      }
      
      _key = (Key) Key.of(type);
      _provider = ()->bean;
    }
    
    BindingBuilderImpl(InjectorBuilder builder, 
                       Provider<T> provider)
    {
      Objects.requireNonNull(builder);
      
      _builder = builder;
      
      Objects.requireNonNull(provider);
      
      _provider = provider;
    }
    
    BindingBuilderImpl(InjectorBuilder builder, 
                       Function<?,T> function)
    {
      Objects.requireNonNull(builder);
      
      _builder = builder;
      
      Objects.requireNonNull(function);

      Class<T> keyType = (Class) TypeRef.of(function.getClass())
                                        .to(Function.class)
                                        .param(1)
                                        .rawClass();
      
      _key = (Key) Key.of(keyType);
      _function = function;
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
    
    void build(InjectorImpl injector)
    {
      injector.addProvider(producer(injector));
    }
    
    BindingAmp<T> producer(InjectorImpl manager)
    {
      BindingAmp<T> producer;
      
      Provider<T> supplier;
      
      if (_impl != null) {
        InjectScope<T> scope = manager.scope(_scopeType);
        
        ProviderConstructor<T> provider
          = new ProviderConstructor(manager, _key, _priority, scope, _impl);
        
        return provider;
      }
      else if (_function != null) {
        // XXX: InjectScope<T> scope = manager.scope(_scopeType);
        
        ProviderFunction<T,?> provider
          = new ProviderFunction(manager, _key, _priority, _function);
        
        return provider;
      }
      else if (_provider != null) {
        supplier = _provider;
      }
      else {
        //supplier = ()->manager.instance(_key);
        throw new UnsupportedOperationException();
      }
      
      producer = new ProviderDelegate<T>(manager, (Key) _key, _priority, (Provider) supplier); 
      
      return producer;
    }
  }
  
  interface IncludeBuilder
  {
    void build(InjectorImpl injector);
  }
  
  class IncludeBuilderClass implements IncludeBuilder
  {
    private Class<?> _type;
    
    IncludeBuilderClass(Class<?> type)
    {
      _type = type;
    }
    
    @Override
    public void build(InjectorImpl injector)
    {
      addInclude(injector, _type);
    }
  }
  
  class IncludeBuilderMethod<U> implements IncludeBuilder
  {
    private Key<U> _keyParent;
    private Method _method;
    
    IncludeBuilderMethod(Key<U> keyParent,
                         Method method)
    {
      _keyParent = keyParent;
      _method = method;
    }
    
    @Override
    public void build(InjectorImpl injector)
    {
      // XXX:
      BindingAmp<U> parentBinding = newBinding(injector, _keyParent.rawClass());
      
      introspectMethod(injector, _method, parentBinding);
    }
  }
}

