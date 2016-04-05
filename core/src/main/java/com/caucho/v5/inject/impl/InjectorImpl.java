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
import java.lang.ref.SoftReference;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Scope;

import com.caucho.v5.inject.BindingAmp;
import com.caucho.v5.inject.BindingInject;
import com.caucho.v5.inject.InjectProgram;
import com.caucho.v5.inject.InjectProvider;
import com.caucho.v5.inject.InjectorAmp;
import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.loader.DynamicClassLoader;
import com.caucho.v5.loader.EnvLoader;
import com.caucho.v5.loader.EnvironmentLocal;
import com.caucho.v5.util.L10N;

import io.baratine.config.Config;
import io.baratine.convert.Convert;
import io.baratine.convert.ConvertManager;
import io.baratine.inject.Binding;
import io.baratine.inject.InjectionPoint;
import io.baratine.inject.Key;

/**
 * The injection manager for a given environment.
 */
public class InjectorImpl implements InjectorAmp
{
  private static final L10N L = new L10N(InjectorImpl.class);
  private static final Logger log = Logger.getLogger(InjectorImpl.class.getName());
  
  private static final EnvironmentLocal<InjectorImpl> _localManager
    = new EnvironmentLocal<>();
  
  private static final WeakHashMap<ClassLoader,SoftReference<InjectorImpl>> _loaderManagerMap
    = new WeakHashMap<>();
  
  private final HashMap<Class<?>,Supplier<InjectScope<?>>> _scopeMap;

  private final Config _config;
    
  private HashSet<Class<?>> _qualifierSet = new HashSet<>();
  
  private ConcurrentHashMap<Class<?>,BindingSet<?>> _bindingSetMap
    = new ConcurrentHashMap<>();
  
  private ClassLoader _loader;
  
  private ArrayList<InjectProvider> _providerList = new ArrayList<>();
  
  private ConcurrentHashMap<Key<?>,Provider<?>> _providerMap
    = new ConcurrentHashMap<Key<?>,Provider<?>>();
  
  private ConcurrentHashMap<Key<?>,Provider<?>> _providerDefaultMap
    = new ConcurrentHashMap<Key<?>,Provider<?>>();
  
  private Provider<ConvertManager> _convertManager;

  private InjectAutoBind[] _autoBind;
  
  InjectorImpl(InjectorBuilderImpl builder)
  {
    _loader = builder.getClassLoader();
    
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    try {
      thread.setContextClassLoader(_loader);
      
      _config = builder.config();
      
      _scopeMap = builder.scopeMap();
      
      //_qualifierSet.add(Lookup.class);
      
      ArrayList<InjectAutoBind> autoBindList = new ArrayList<>();
      
      for (InjectAutoBind autoBind : builder.autoBind()) {
        autoBindList.add(autoBind);
      }
      
      InjectAutoBind []autoBind = new InjectAutoBind[autoBindList.size()];
      autoBindList.toArray(autoBind);
      
      _autoBind = autoBind;
      
      /*
      for (Class<?> beanType : builder.beans()) {
        addBean(beanType);
      }
      */
      
      builder.bind(this);
      
      if (builder.isContext()) {
        current(_loader, this);
      }
      
      bind();
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }

  /**
   * Returns the current inject manager.
   */
  public static InjectorImpl current(ClassLoader loader)
  {
    if (loader instanceof DynamicClassLoader) {
      return _localManager.getLevel(loader);
    }
    else {
      SoftReference<InjectorImpl> injectRef = _loaderManagerMap.get(loader);
      
      if (injectRef != null) {
        return injectRef.get();
      }
      else {
        return null;
      }
    }
  }

  /**
   * Creates a new inject manager.
   */
  public static InjectorImpl create()
  {
    return create(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Creates a new inject manager.
   */
  public static InjectorImpl create(ClassLoader loader)
  {
    synchronized (loader) {
      if (loader instanceof DynamicClassLoader) {
        InjectorImpl inject = _localManager.getLevel(loader);
        
        if (inject == null) {
          inject = (InjectorImpl) InjectorAmp.manager(loader).get();
          _localManager.set(inject, loader);
        }
        
        return inject;
      }
      else {
        SoftReference<InjectorImpl> injectRef = _loaderManagerMap.get(loader);
      
        InjectorImpl inject = null;
      
        if (injectRef != null) {
          inject = injectRef.get();
        
          if (inject != null) {
            return inject;
          }
        }
        
        inject = (InjectorImpl) InjectorAmp.manager(loader).get();
        
        _loaderManagerMap.put(loader, new SoftReference<>(inject));
        
        return inject;
      }
    }
  }

  /**
   * Creates a new inject manager.
   */
  private static void current(ClassLoader loader, InjectorImpl manager)
  {
    Objects.requireNonNull(manager);
    
    synchronized (loader) {
      if (loader instanceof DynamicClassLoader) {
        _localManager.set(manager, loader);
      }
      else {
        _loaderManagerMap.put(loader, new SoftReference<>(manager));
      }
    }
  }

  /**
   * Creates a new inject manager.
   */
  public static InjectorBuilderImpl manager(ClassLoader loader)
  {
    return new InjectorBuilderImpl(loader);
  }
  
  /**
   * A configuration property.
   */
  
  @Override
  public String property(String key)
  {
    return _config.get(key);
  }
  
  /**
   * The configuration object.
   */
  
  @Override
  public Config config()
  {
    return _config;
  }

  @Override
  public <T> T instance(Class<T> type)
  {
    Key<T> key = Key.of(type);

    return instance(key);
  }

  @Override
  public <T> T instance(Key<T> key)
  {
    Objects.requireNonNull(key);
    
    Class<T> type = (Class) key.rawClass();
    if (type.equals(Provider.class)) {
      TypeRef typeRef = TypeRef.of(key.type());
      TypeRef param = typeRef.param(0);
      
      return (T) provider(Key.of(param.type()));
    }
    
    
    Provider<T> provider = provider(key);
    
    if (provider != null) {
      return provider.get();
    }
    else {
      return null;
    }
  }

  @Override
  public <T> T instance(InjectionPoint<T> ip)
  {
    Objects.requireNonNull(ip);
    
    Provider<T> provider = provider(ip);
    
    if (provider != null) {
      return provider.get();
    }
    else {
      return null;
    }
  }

  /*
  @Override
  public <T,X> T instance(Class<T> type, X param)
  {
    Key<T> key = Key.of(type);

    return instance(key, param);
  }

  public <T,X> T instance(Key<T> type, X param)
  {
    Class<X> paramType = (Class<X>) param.getClass();

    Function<X,T> fun = function(type, paramType);

    if (fun != null) {
      return fun.apply(param);
    }
    else {
      return null;
    }
  }
  */

  @Override
  public <T> Provider<T> provider(InjectionPoint<T> ip)
  {
    Objects.requireNonNull(ip);
    
    Provider<T> provider = lookupProvider(ip);
    
    if (provider != null) {
      return provider;
    }
    
    return autoProvider(ip);
  }

  @Override
  public <T> Provider<T> provider(Key<T> key)
  {
    Objects.requireNonNull(key);
    
    Provider<T> provider = (Provider) _providerMap.get(key);

    if (provider == null) {
      provider = lookupProvider(key);

      if (provider == null) {
        provider = autoProvider(key);
      }
      
      _providerMap.putIfAbsent(key, provider);
      
      provider = (Provider) _providerMap.get(key);
    }
    
    return provider;
  }

  private <T> Provider<T> lookupProvider(Key<T> key)
  {
    BindingInject<T> bean = findBean(key);
    
    if (bean != null) {
      return bean.provider();
    }
    
    BindingAmp<T> binding = findBinding(key);
    
    if (binding != null) {
      return binding.provider();
    }
    
    binding = findObjectBinding(key);
    
    if (binding != null) {
      return binding.provider(InjectionPoint.of(key));
    }
    
    
    return null;
  }

  private <T> Provider<T> lookupProvider(InjectionPoint<T> ip)
  {
    Key<T> key = ip.key();
    
    BindingInject<T> bean = findBean(key);
    
    if (bean != null) {
      return bean.provider(ip);
    }
    
    BindingAmp<T> provider = findBinding(key);
    
    if (provider != null) {
      return provider.provider(ip);
    }
    
    provider = findObjectBinding(key);
    
    if (provider != null) {
      return provider.provider(ip);
    }
    
    
    return null;
  }
  
  /**
   * Create a provider based on registered auto-binders.
   */
  private <T> Provider<T> autoProvider(Key<T> key)
  {
    for (InjectAutoBind autoBind : _autoBind) {
      Provider<T> provider = autoBind.provider(this, key);
      
      if (provider != null) {
        return provider;
      }
    }
    
    return providerDefault(key);
  }
  
  
  /**
   * Create a provider based on registered auto-binders.
   */
  private <T> Provider<T> autoProvider(InjectionPoint<T> ip)
  {
    for (InjectAutoBind autoBind : _autoBind) {
      Provider<T> provider = autoBind.provider(this, ip);
      
      if (provider != null) {
        return provider;
      }
    }
    
    return providerDefault(ip.key());
  }

  private <T> Provider<T> providerDefault(Key<T> key)
  {
    Objects.requireNonNull(key);
    
    Provider<T> provider = (Provider) _providerDefaultMap.get(key);

    if (provider == null) {
      provider = createProvider(key);
      
      _providerDefaultMap.putIfAbsent(key, provider);
      
      provider = (Provider) _providerDefaultMap.get(key);
    }
    
    return provider;
  }

  /**
   * default provider
   */
  private <T> Provider<T> createProvider(Key<T> key)
  {
    Class<T> type = (Class<T>) key.rawClass();

    if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
      return ()->null;
    }
    
    int priority = 0;
    
    // auto-provider is factory
    InjectScope<T> scope = findScope(type);

    BindingAmp<T> binding = new ProviderConstructor<>(this, key, priority, scope, type);
    
    binding.bind();
    
    return binding.provider();
  }
  
  private <T> InjectScope<T> findScope(AnnotatedElement annElement)
  {
    for (Annotation ann : annElement.getAnnotations()) {
      Class<? extends Annotation> annType = ann.annotationType();
      
      if (annType.isAnnotationPresent(Scope.class)) {
        Supplier<InjectScope<T>> scopeGen = (Supplier) _scopeMap.get(annType);
        
        if (scopeGen != null) {
          return scopeGen.get();
        }
        else {
          log.fine(L.l("@{0} is an unknown scope", annType.getSimpleName()));
        }
      }
    }
    
    return new InjectScopeFactory<>();
  }
  
  @Override
  public <T> Iterable<Binding<T>> bindings(Class<T> type)
  {
    BindingSet<T> set = (BindingSet) _bindingSetMap.get(type);
   
    if (set != null) {
      return (Iterable) set;
    }
    else {
      return Collections.EMPTY_LIST;
    }
  }

  @Override
  public <T> List<Binding<T>> bindings(Key<T> key)
  {
    BindingSet<T> set = (BindingSet) _bindingSetMap.get(key.rawClass());
   
    if (set != null) {
      return set.bindings(key);
    }
    else {
      return Collections.EMPTY_LIST;
    }
  }

  <T> InjectScope<T> scope(Class<? extends Annotation> scopeType)
  {
    Supplier<InjectScope<T>> scopeGen = (Supplier) _scopeMap.get(scopeType);
    
    if (scopeGen == null) {
      throw error("{0} is an unknown scope",
                  scopeType.getSimpleName());
    }

    return scopeGen.get();
  }
  
  @Override
  public <T> Consumer<T> injector(Class<T> type)
  {
    ArrayList<InjectProgram> injectList = new ArrayList<>();
    
    introspectInject(injectList, type);
    
    introspectInit(injectList, type);
    
    return new InjectProgramImpl<T>(injectList);
  }
  
  @Override
  public Provider<?> []program(Parameter []params)
  {
    Provider<?> []program = new Provider<?>[params.length];
    
    for (int i = 0; i < program.length; i++) {
      //Key<?> key = Key.of(params[i]);
      
      program[i] = provider(InjectionPoint.of(params[i]));
    }
    
    return program;
  }
  
  private <T> BindingInject<T> findBean(Key<T> key)
  {
    for (InjectProvider provider : _providerList) {
      BindingInject<T> bean = (BindingInject) provider.lookup(key.rawClass());

      if (bean != null) {
        return bean;
      }
    }
    
    return null;
  }
  
  /**
   * Introspect for @Inject fields.
   */
  //@Override
  private void introspectInject(List<InjectProgram> program, Class<?> type)
  {
    if (type == null) {
      return;
    }
    
    introspectInjectField(program, type);
    introspectInjectMethod(program, type);
  }
  
  private void introspectInjectField(List<InjectProgram> program, Class<?> type)
  {
    if (type == null) {
      return;
    }
    
    introspectInjectField(program, type.getSuperclass());
    
    for (Field field : type.getDeclaredFields()) {
      if (! field.isAnnotationPresent(Inject.class)) {
        continue;
      }

      /*
      ArrayList<Annotation> annList = new ArrayList<>();
      for (Annotation ann : field.getAnnotations()) {
        if (isQualifier(ann)) {
          annList.add(ann);
        }
      }
      
      Annotation []qualifiers = new Annotation[annList.size()];
      annList.toArray(qualifiers);
      */
      
      program.add(new InjectField(this, field));
    }
  }
  
  public void introspectInjectMethod(List<InjectProgram> program, Class<?> type)
  {
    if (type == null) {
      return;
    }
    
    introspectInjectMethod(program, type.getSuperclass());
    
    for (Method method : type.getDeclaredMethods()) {
      if (! method.isAnnotationPresent(Inject.class)) {
        continue;
      }
      
      program.add(new InjectMethod(this, method));
    }
  }
  
  /**
   * Introspect for @PostConstruct methods.
   */
  //@Override
  private void introspectInit(List<InjectProgram> program, Class<?> type)
  {
    if (type == null) {
      return;
    }
    
    introspectInit(program, type.getSuperclass());
    
    try {
      for (Method method : type.getDeclaredMethods()) {
        if (method.isAnnotationPresent(PostConstruct.class)) {
          // XXX: program.add(new PostConstructProgram(Config.getCurrent(), method));
        }
      }
    } catch (Throwable e) {
      log.log(Level.FINEST, e.toString(), e);
    }
  }
  
  /**
   * Adds a new injection producer to the discovered producer list.
   */
  <T> void addProvider(BindingAmp<T> binding)
  {
    // TypeRef typeRef = TypeRef.of(producer.key().type());
    
    Class<T> type = (Class) binding.key().rawClass();
    
    addBinding(type, binding);
  }
  
  /**
   * Adds a new injection producer to the discovered producer list.
   */
  <T> void addFunction(BindingAmp<T> binding)
  {
    // TypeRef typeRef = TypeRef.of(producer.key().type());
    
    Class<T> type = (Class) binding.key().rawClass();
    
    addBinding(type, binding);
  }
  
  /**
   * Adds a new injection producer to the discovered producer list.
   */
  private <T> void addBinding(Class<T> type, BindingAmp<T> binding)
  {
    synchronized (_bindingSetMap) {
      BindingSet<T> set = (BindingSet) _bindingSetMap.get(type);

      if (set == null) {
        set = new BindingSet<>(type);
        _bindingSetMap.put(type, set);
      }
      
      set.addBinding(binding);
    }
  }
  
  /**
   * Returns an object producer.
   */
  private <T> BindingAmp<T> findObjectBinding(Key<T> key)
  {
    Objects.requireNonNull(key);
    
    if (key.qualifiers().length != 1) {
      throw new IllegalArgumentException();
      
    }
    return (BindingAmp) findBinding(Key.of(Object.class, 
                                           key.qualifiers()[0]));
  }
  
  /**
   * Finds a producer for the given target type.
   */
  private <T> BindingAmp<T> findBinding(Key<T> key)
  {
    BindingSet<T> set = (BindingSet) _bindingSetMap.get(key.rawClass());
    
    if (set != null) {
      BindingAmp<T> binding = set.find(key);
      
      if (binding != null) {
        return binding;
      }
    }
    
    return null;
  }
  
  private void bind()
  {
    for (BindingSet<?> bindingSet : _bindingSetMap.values()) {
      bindingSet.bind();
    }
  }

  @Override
  public <S, T> Convert<S, T> converter(Class<S> source, Class<T> target)
  {
    if (_convertManager == null) {
      _convertManager = provider(Key.of(ConvertManager.class));
    }
    
    return _convertManager.get().converter(source, target);
  }
  
  private RuntimeException error(String msg, Object ...args)
  {
    return new InjectException(L.l(msg, args));
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + EnvLoader.getId(_loader) + "]";
  }
  
  private static class InjectProgramImpl<T> implements Consumer<T>
  {
    private InjectProgram []_program;
    
    InjectProgramImpl(ArrayList<InjectProgram> programList)
    {
      _program = new InjectProgram[programList.size()];
      programList.toArray(_program);
    }
    
    @Override
    public void accept(T bean)
    {
      Objects.requireNonNull(bean);
      
      InjectContext env = InjectContextImpl.CONTEXT;
      
      for (InjectProgram program : _program) {
        program.inject(bean, env);
      }
    }
  }
  
  static class BindingSet<T> implements Iterable<BindingAmp<T>>
  {
    private Class<T> _type;
    private ArrayList<BindingAmp<T>> _list = new ArrayList<>();
    
    BindingSet(Class<T> type)
    {
      _type = type;
    }

    void addBinding(BindingAmp<T> binding)
    {
      _list.add(binding);
      
      _list.sort((x,y)->compareBinding(x,y));
    }
    
    private int compareBinding(BindingAmp<T> x, BindingAmp<T> y)
    {
      int cmp = y.priority() - x.priority();
      
      return Integer.signum(cmp);
    }
    
    void bind()
    {
      for (BindingAmp<T> binding : _list) {
        binding.bind();
      }
    }
    
    BindingAmp<T> find(Key<T> key)
    {
      for (BindingAmp<T> binding : _list) {
        if (key.isAssignableFrom(binding.key())) {
          return binding;
        }
      }
      
      return null;
    }

    public <X> Function<X,T> findFunction(Key<T> key, Class<X> paramType)
    {
      for (BindingAmp<T> binding : _list) {
        if (key.isAssignableFrom(binding.key())) {
          Function<X,T> fun = binding.function(paramType);
          
          if (fun != null) {
            return fun;
          }
        }
      }
      
      return null;
    }

    public List<Binding<T>> bindings(Key<T> key)
    {
      List<Binding<T>> bindings = new ArrayList<>();
      
      for (BindingAmp<T> binding : _list) {
        if (key.isAssignableFrom(binding.key())) {
          bindings.add(binding);
        }
      }

      return bindings;
    }
    
    @Override
    public Iterator<BindingAmp<T>> iterator()
    {
      return _list.iterator();
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _type.getName() + "]";
    }
  }
  
  private static class KeyFunction
  {
    private Key<?> _key;
    private Class<?> _paramType;
    
    KeyFunction(Key<?> key, Class<?> paramType)
    {
      _key = key;
      _paramType = paramType;
    }
  }
}

