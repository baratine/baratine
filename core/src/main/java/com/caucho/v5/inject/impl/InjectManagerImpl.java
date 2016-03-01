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
import java.lang.reflect.Constructor;
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
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.inject.Scope;
import javax.inject.Singleton;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.inject.BindingAmp;
import com.caucho.v5.inject.BindingInject;
import com.caucho.v5.inject.InjectManagerAmp;
import com.caucho.v5.inject.InjectProgram;
import com.caucho.v5.inject.InjectProvider;
import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.loader.DynamicClassLoader;
import com.caucho.v5.loader.EnvLoader;
import com.caucho.v5.loader.EnvironmentLocal;

import io.baratine.config.Config;
import io.baratine.convert.Convert;
import io.baratine.convert.ConvertManager;
import io.baratine.inject.Binding;
import io.baratine.inject.InjectionPoint;
import io.baratine.inject.Key;

/**
 * The injection manager for a given environment.
 */
public class InjectManagerImpl implements InjectManagerAmp
{
  private static final Logger log = Logger.getLogger(InjectManagerImpl.class.getName());
  
  private static final EnvironmentLocal<InjectManagerImpl> _localManager
    = new EnvironmentLocal<>();
  
  private static final WeakHashMap<ClassLoader,SoftReference<InjectManagerImpl>> _loaderManagerMap
    = new WeakHashMap<>();
  
  private HashMap<Class<?>,InjectScope> _scopeMap = new HashMap<>();
  private InjectScope _scopeDefault = new InjectScopeDefault();

  private final Config _env;
    
  private HashSet<Class<?>> _qualifierSet = new HashSet<>();
  
  private ConcurrentHashMap<Class<?>,BindingSet<?>> _bindingMap
    = new ConcurrentHashMap<>();
  
  private ClassLoader _loader;
  
  private ArrayList<InjectProvider> _providerList = new ArrayList<>();
  
  private ConcurrentHashMap<Key<?>,Provider<?>> _providerMap
    = new ConcurrentHashMap<Key<?>,Provider<?>>();
  
  private Provider<ConvertManager> _convertManager;

  private InjectAutoBind[] _autoBind;
  
  InjectManagerImpl(InjectManagerBuilderImpl builder)
  {
    _loader = builder.getClassLoader();
    
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    try {
      thread.setContextClassLoader(_loader);
      
      _env = builder.config();
      
      _scopeMap.put(Singleton.class, new InjectScopeSingleton());
      
      //_qualifierSet.add(Lookup.class);
      
      ArrayList<InjectAutoBind> autoBindList = new ArrayList<>();
      
      for (InjectAutoBind autoBind : builder.autoBind()) {
        autoBindList.add(autoBind);
      }
      
      InjectAutoBind []autoBind = new InjectAutoBind[autoBindList.size()];
      autoBindList.toArray(autoBind);
      
      _autoBind = autoBind;
      
      for (Class<?> beanType : builder.beans()) {
        addBean(beanType);
      }
      
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
  public static InjectManagerImpl current(ClassLoader loader)
  {
    if (loader instanceof DynamicClassLoader) {
      return _localManager.getLevel(loader);
    }
    else {
      SoftReference<InjectManagerImpl> injectRef = _loaderManagerMap.get(loader);
      
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
  public static InjectManagerImpl create()
  {
    return create(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Creates a new inject manager.
   */
  public static InjectManagerImpl create(ClassLoader loader)
  {
    synchronized (loader) {
      if (loader instanceof DynamicClassLoader) {
        InjectManagerImpl inject = _localManager.getLevel(loader);
        
        if (inject == null) {
          inject = (InjectManagerImpl) InjectManagerAmp.manager(loader).get();
          _localManager.set(inject, loader);
        }
        
        return inject;
      }
      else {
        SoftReference<InjectManagerImpl> injectRef = _loaderManagerMap.get(loader);
      
        InjectManagerImpl inject = null;
      
        if (injectRef != null) {
          inject = injectRef.get();
        
          if (inject != null) {
            return inject;
          }
        }
        
        inject = (InjectManagerImpl) InjectManagerAmp.manager(loader).get();
        
        _loaderManagerMap.put(loader, new SoftReference<>(inject));
        
        return inject;
      }
    }
  }

  /**
   * Creates a new inject manager.
   */
  private static void current(ClassLoader loader, InjectManagerImpl manager)
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
  public static InjectManagerBuilderImpl manager(ClassLoader loader)
  {
    return new InjectManagerBuilderImpl(loader);
  }
  
  /**
   * A configuration property.
   */
  
  @Override
  public String property(String key)
  {
    return _env.get(key);
  }
  
  /**
   * The configuration object.
   */
  
  @Override
  public Config config()
  {
    return _env;
  }

  @Override
  public <T> T lookup(Class<T> type, Annotation ...qualifiers)
  {
    /*
    for (InjectProvider provider : _providerList) {
      T value = provider.lookup(type, qualifiers);
      
      if (value != null) {
        return value;
      }
    }
    
    return createInstance(type);
    */
    
    return (T) instance(new Key(type, qualifiers));
  }

  @Override
  public <T> T instance(Class<T> type)
  {
    Key<T> key = Key.of(type);

    return instance(key);
    /*
    T instance = lookupImpl(key);
    
    if (instance != null) {
      return instance;
    }
    
    return autoInstance(key);
    */
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
    /*
    //Class<T> type = (Class) key.rawClass();
    
    T instance = lookupImpl(key);
    
    if (instance != null) {
      return instance;
    }
    
    return autoInstance(key);
    */
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
    /*
    //Class<T> type = (Class) key.rawClass();
    
    T instance = lookupImpl(ip);
    
    if (instance != null) {
      return instance;
    }
    
    return (T) autoInstance(ip.key());
    */
  }

  @Override
  public <T> Provider<T> provider(InjectionPoint<T> ip)
  {
    Objects.requireNonNull(ip);
    
    Provider<T> provider = lookupProvider(ip);
    
    if (provider != null) {
      return provider;
    }
    
    return (Provider<T>) autoProvider(ip.key());
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
    }
    
    return provider;
  }

  @Override
  public <T> T instanceNonService(Class<T> type, Annotation...qualifiers)
  {
    throw new UnsupportedOperationException();
    /*
    T instance = lookupImpl(type);
    
    if (instance != null) {
      return instance;
    }
    
    return createInstance(type);
    */
  }

  /*
  private <T> T lookupImpl(Key<T> key)
  {
    BindingInject<T> bean = findBean(key);
    
    if (bean != null) {
      return bean.create();
    }
    
    BindingAmp<T> producer = findBinding(key);
    
    if (producer != null) {
      return producer.create(key.rawClass());
    }
    
    producer = findObjectBinding(key);
    
    if (producer != null) {
      return producer.create(key.rawClass());
    }
    
    
    return null;
  }
  */

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

  /*
  private <T> T lookupImpl(InjectionPoint<T> ip)
  {
    Key<T> key = (Key) ip.key();
    
    BindingInject<T> bean = findBean(key);
    
    if (bean != null) {
      return bean.create();
    }
    
    ProviderInject<T> producer = findBinding(key);
    
    if (producer != null) {
      return producer.create(key.rawClass());
    }
    
    producer = findObjectBinding(key);
    
    if (producer != null) {
      return producer.create(ip);
    }
    
    
    return null;
  }
  */

  private <T> Provider<T> lookupProvider(InjectionPoint<T> ip)
  {
    Key<T> key = ip.key();
    
    BindingInject<T> bean = findBean(key);
    
    if (bean != null) {
      return bean.provider(ip);
    }
    
    BindingAmp<T> provider = findBinding(key);
    
    if (provider != null) {
      return provider.provider(ip); //.create(key.rawClass());
    }
    
    provider = findObjectBinding(key);
    
    if (provider != null) {
      return provider.provider(ip); // .create(ip);
    }
    
    
    return null;
  }

  /*
  private <T> T autoInstance(Key<T> key)
  {
    Class<?> rawType = key.rawClass();
    
    if (Provider.class.equals(rawType)) {
      Type type = key.type();
      TypeRef typeRef = TypeRef.of(type);
      TypeRef providerRef = typeRef.to(Provider.class);
      TypeRef instanceRef = providerRef.param(0);
      
      Key<?> subKey = Key.of(instanceRef.type());
      return (T) provider(subKey);
    }
    for (InjectAutoBind autoBind : _autoBind) {
      Provider<T> provider = autoBind.provider(key);
      
      if (provider != null) {
        return provider.get();
      }
    }
    
    return createInstance(key);
  }
  */
  
  private <T> Provider<T> autoProvider(Key<T> key)
  {
    for (InjectAutoBind autoBind : _autoBind) {
      Provider<T> provider = autoBind.provider(this, key);
      
      if (provider != null) {
        return provider;
      }
    }
    
    return createProvider(key);
  }

  @Override
  public <T> Iterable<Binding<T>> bindings(Class<T> type)
  {
    BindingSet<T> set = (BindingSet) _bindingMap.get(type);
   
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
    BindingSet<T> set = (BindingSet) _bindingMap.get(key.rawClass());
   
    if (set != null) {
      return set.bindings(key);
    }
    else {
      return Collections.EMPTY_LIST;
    }
  }

  /*
  @Override
  public <T> T instanceCreate(Class<T> type)
  {
    return createInstance(type);
  }
  */

  @Override
  public Object createByName(String name)
  {
    return null;
  }
  
  private <T> T createInstance(Key<T> key)
  {
    try {
      Class<T> type = (Class) key.rawClass();
      InjectScope scope = findScope(type);
      
      T value = null;
      
      if (scope != null) {
        value = scope.get(type);
      
        if (value != null) {
          return value;
        }
      }
      
      value = newInstance(type);
      
      if (value == null) {
        return null;
      }
      
      if (scope != null) {
        scope.set(type, value);
      }

      InjectContext env = InjectContextImpl.CONTEXT;
      
      inject(env, value);
      
      init(env, value);
        
      return value;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw ConfigException.wrap(e);
    }
  }

  @Override
  public void inject(Object bean)
  {
    Objects.requireNonNull(bean);
    
    InjectContext env = InjectContextImpl.CONTEXT;
    
    inject(env, bean);
    
    init(env, bean);
  }
  
  private <T> Provider<T> createProvider(Key<T> key)
  {
    Class<T> type = (Class<T>) key.rawClass();
    
    if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
      return ()->null;
    }
    
    int priority = 0;
    
    BindingAmp<T> binding = new ProviderConstructor<>(this, key, priority, type);
    
    binding.bind();
    
    return binding.provider();

    /*
    return new Provider<T>() { 
      public T get() { return createInstance(key); }
      };
      */
  }
  
  private InjectScope findScope(Class<?> type)
  {
    for (Annotation ann : type.getAnnotations()) {
      Class<?> annType = ann.annotationType();
      
      if (annType.isAnnotationPresent(Scope.class)) {
        InjectScope scope = _scopeMap.get(annType);
        
        if (scope != null) {
          return scope;
        }
      }
    }
    
    return _scopeDefault;
  }
  
  //@Override
  public void inject(InjectContext env, Object value)
  {
    if (value == null) {
      return;
    }
    
    ArrayList<InjectProgram> injectList = new ArrayList<>();
    
    introspectInject(injectList, value.getClass());
    
    for (InjectProgram init : injectList) {
      init.inject(value, env);
    }
  }
  
  @Override
  public <T> Consumer<T> program(Class<T> type)
  {
    ArrayList<InjectProgram> injectList = new ArrayList<>();
    
    introspectInject(injectList, type);
    
    introspectInit(injectList, type);
    
    return new InjectProgramImpl<T>(injectList);
  }
  
  //@Override
  public void init(InjectContext env, Object value)
  {
    if (value == null) {
      return;
    }
    ArrayList<InjectProgram> initList = new ArrayList<>();
    
    introspectInit(initList, value.getClass());
    
    for (InjectProgram init : initList) {
      init.inject(value, env);
    }
  }
  
  private <T> T newInstance(Class<T> type)
  {
    try {
      if (type.isInterface()) {
        return null;
      }

      for (Constructor <?>ctor : type.getDeclaredConstructors()) {
        if (ctor.getParameterTypes().length == 0) {
          ctor.setAccessible(true);
          
          return (T) ctor.newInstance();
        }
      }
      
      return type.newInstance();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw ConfigException.wrap(e);
    }
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
  
  public <X> void addBean(Class<X> beanClass)
  {
    BindingAmp<X> bindingOwner = newBinding(beanClass);
    
    introspectProduces(beanClass, bindingOwner);
  }
  
  private <X> void introspectProduces(Class<X> beanClass, 
                                      BindingAmp<X> bindingOwner)
  {
    for (Method method : beanClass.getMethods()) {
      if (! isProduces(method.getAnnotations())) {
        continue;
      }
      
      introspectMethod(method, bindingOwner);
    }
  }
  
  private <T,X> void introspectMethod(Method method,
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
      
      addProvider(binding);
    }
    else {
      ProviderMethod producer
        = new ProviderMethod(this, ownerBinding, method);
        
      addProvider(producer);
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

  @Override
  public <T> Supplier<T> supplierNew(Class<T> type, Annotation ...qualifiers)
  {
    return ()->newInstance(type);
  }
  
  @Override
  public <T> Provider<T> newProvider(Class<T> type, Annotation ...qualifiers)
  {
    //return ()->instance(Key.of(type));
    Key<T> key = Key.of(type);
    
    int priority = 0;
    
    return new ProviderConstructor<>(this, key, priority, type);
  }
  
  @Override
  public <T> BindingAmp<T> newBinding(Class<T> type)
  {
    //return ()->instance(Key.of(type));
    Key<T> key = Key.of(type);
    
    int priority = 0;
    
    return new ProviderConstructor<>(this, key, priority, type);
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
  public void introspectInject(List<InjectProgram> program, Class<?> type)
  {
    if (type == null) {
      return;
    }
    
    introspectInjectField(program, type);
    introspectInjectMethod(program, type);
  }
  
  public void introspectInjectField(List<InjectProgram> program, Class<?> type)
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
      
      
      /*
      ArrayList<Annotation> annList = new ArrayList<>();
      for (Annotation ann : field.getAnnotations()) {
        if (isQualifier(ann)) {
          annList.add(ann);
        }
      }
      
      Annotation []qualifiers = qualifiers(method.getAnnotations());
      */
      
      program.add(new InjectMethod(this, method));
    }
  }
  
  private Annotation []qualifiers(Annotation []anns)
  {
    ArrayList<Annotation> annList = new ArrayList<>();
    for (Annotation ann : anns) {
      if (isQualifier(ann)) {
        annList.add(ann);
      }
    }
    
    Annotation []qualifiers = new Annotation[annList.size()];
    annList.toArray(qualifiers);
    
    return qualifiers;
  }
  
  @Override
  public boolean isQualifier(Annotation ann)
  {
    Class<?> annType = ann.annotationType();
    
    if (annType.isAnnotationPresent(Qualifier.class)) {
      return true;
    }
    
    return _qualifierSet.contains(annType);
  }
  
  /**
   * Introspect for @PostConstruct methods.
   */
  //@Override
  public void introspectInit(List<InjectProgram> program, Class<?> type)
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

  @Override
  public void scanRoot(ClassLoader classLoader)
  {
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
   * Adds a new injection producer with all its children.
   */
  /*
  private void addProducerRec(Class<?> type, InjectProducer<?> producer)
  {
    if (type == null
        || Object.class.equals(type)
        || Serializable.class.equals(type)) {
      return;
    }

    addProducer(type, producer);
    
    addProducerRec(type.getSuperclass(), producer);
    
    for (Class<?> api : type.getInterfaces()) {
      addProducerRec(api, producer);
    }
  }
  */
  
  /**
   * Adds a new injection producer to the discovered producer list.
   */
  private <T> void addBinding(Class<T> type, BindingAmp<T> binding)
  {
    synchronized (_bindingMap) {
      BindingSet<T> set = (BindingSet) _bindingMap.get(type);

      if (set == null) {
        set = new BindingSet<>(type);
        _bindingMap.put(type, set);
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
    BindingSet<T> set = (BindingSet) _bindingMap.get(key.rawClass());
    
    if (set != null) {
      BindingAmp<T> binding = set.find(key);
      
      if (binding != null) {
        return binding;
      }
    }
    
    return null;
  }

  @Override
  public <T> T instance(Class<T> type, Annotation... qualifiers)
  {
    throw new UnsupportedOperationException();
  }
  
  private void bind()
  {
    for (BindingSet<?> bindingSet : _bindingMap.values()) {
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
}

