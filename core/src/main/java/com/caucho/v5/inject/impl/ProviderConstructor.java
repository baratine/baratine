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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Provider;

import com.caucho.v5.inject.BindingAmp;
import com.caucho.v5.inject.InjectManagerAmp;
import com.caucho.v5.util.L10N;

import io.baratine.inject.InjectionPoint;
import io.baratine.inject.Key;

/**
 * Bean provider for a concrete class.
 */
public class ProviderConstructor<T> implements BindingAmp<T>, Provider<T>
{
  private static final L10N L = new L10N(ProviderConstructor.class);
  
  private Key<T> _key;
  private int _priority;
  
  private Class<T> _type;

  private InjectManagerAmp _injector;
  private Constructor<?> _ctor;
  private MethodHandle _ctorHandle;
  private Provider<?>[] _ctorParam;
  
  private Consumer<T> _inject;

  public ProviderConstructor(InjectManagerAmp injector,
                             Key<T> key,
                             int priority,
                             Class<T> type)
  {
    _injector = injector;
    _key = key;
    _priority = priority;
    
    _type = type;
    
    if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
      throw new IllegalStateException(type.getName());
    }
  }
  
  @Override  
  public Key<T> key()
  {
    return _key;
  }
  
  @Override
  public int priority()
  {
    return _priority;
  }

  @Override
  public Provider<T> provider()
  {
    return this;
  }
  
  @Override
  public T get()
  {
    try {
      int len = _ctorParam.length;
      
      T bean;
      
      if (len > 0) {
        Object []args = new Object[_ctorParam.length];
        for (int i = 0; i < args.length; i++) {
          args[i] = _ctorParam[i].get();
        }
      
        bean = (T) _ctorHandle.invokeWithArguments(args);
      }
      else {
        bean = (T) _ctorHandle.invoke();
      }
      
      _inject.accept(bean);
      //_injector.ini.init(this);
      
      return bean;
    } catch (Throwable e) {
      throw new InjectException(e);
    }
  }
  
  @Override
  public void bind()
  {
    if (_ctor != null) {
      return;
    }
    
    introspect();
  }
  
  private void introspect()
  {
    Constructor<?> ctor = findConstructor();
    
    if (ctor == null) {
      throw new IllegalStateException(L.l("{0} does not have a valid constructor",
                                          _type.getName()));
    }
    
    try {
      _ctor = ctor;
      _ctor.setAccessible(true);
      _ctorHandle = MethodHandles.lookup().unreflectConstructor(_ctor);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }

    /*
    if (_ctor.getParameters().length == 1) {
      InjectionPoint<?> ip = InjectionPoint.of(Key.of(_ctor),
                                               _type,
                                               _type.getSimpleName(),
                                               _ctor.getAnnotations(),
                                               _type);
                                            
      Provider<?> param = _injector.provider(ip);
      
      _ctorParam = new Provider<?>[] { param };
    }
    else {
    */
    _ctorParam = _injector.program(_ctor.getParameters());
    
    _inject = _injector.program(_type);
  }
  
  private Constructor<?> findConstructor()
  {
    Constructor<?> ctorZero = null;
    
    for (Constructor<?> ctor : _type.getDeclaredConstructors()) {
      if (ctor.isAnnotationPresent(Inject.class)) {
        return ctor;
      }
      
      if (ctor.getParameterTypes().length == 0) {
        ctorZero = ctor;
      }
    }

    return ctorZero;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _key + "]";
  }
}

