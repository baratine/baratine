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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import javax.inject.Provider;

import io.baratine.inject.InjectionPoint;
import io.baratine.inject.Key;
import io.baratine.inject.Priority;

import com.caucho.v5.inject.BindingAmp;

/**
 * A method producer that returns a new injection bean for the given type and
 * qualifiers.
 */
class ProviderMethodAtPoint<T,X> implements BindingAmp<T>
{
  //private InjectManagerAmp _manager;
  private Method _method;
  private Key<T> _key;
  private int _priority;
  
  private BindingAmp<X> _ownerBinding;
  private Provider<X> _ownerProvider;

  ProviderMethodAtPoint(InjectorBuilderImpl manager,
                        BindingAmp<X> ownerBinding,
                              Method method)
  {
    //_manager = manager;
    _ownerBinding = ownerBinding;
    _method = method;
    
    _method.setAccessible(true);
    
    Priority priority = _method.getAnnotation(Priority.class);
    
    if (priority != null) {
      _priority = priority.value();
    }
    
    Class<?> []param = _method.getParameterTypes();
    
    if (param.length != 1
        || ! param[0].equals(InjectionPoint.class)) {
      throw new IllegalArgumentException(String.valueOf(_method));
    }
    
    /*
    if (! Type.class.equals(param[0])) {
      throw new IllegalArgumentException(String.valueOf(_method));
    }
    
    if (! Annotation[].class.equals(param[1])) {
      throw new IllegalArgumentException(String.valueOf(_method));
    }
    */
    
    ArrayList<Annotation> qualifiers = new ArrayList<>();
    
    for (Annotation ann : method.getAnnotations()) {
      if (manager.isQualifier(ann)) {
        qualifiers.add(ann);
      }
    }
    
    if (Object.class.equals(_method.getReturnType()) && qualifiers.size() == 0) {
      throw new IllegalArgumentException(String.valueOf(_method));
    }
    
    _key = Key.of(method);
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
  public void bind()
  {
    if (_ownerProvider == null) {
      _ownerBinding.bind();
      _ownerProvider = _ownerBinding.provider();
    }
  }
  
  @Override
  public Provider<T> provider()
  {
    return new ProviderInjectionPoint(InjectionPoint.of(_method));
  }
  
  @Override
  public Provider<T> provider(InjectionPoint<T> ip)
  {
    return new ProviderInjectionPoint(ip);
  }
  
  private class ProviderInjectionPoint implements Provider<T>
  {
    private InjectionPoint<T> _ip;
    
    ProviderInjectionPoint(InjectionPoint<T> ip)
    {
      _ip = ip;
    }

    @Override
    public T get()
    {
      X bean = _ownerProvider.get();
    
      try {
        T value = (T) _method.invoke(bean, _ip);
      
        return value;
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
      
        if (cause instanceof RuntimeException) {
          throw (RuntimeException) cause;
        }
        else {
          throw new RuntimeException(e);
        }
      } catch (Exception e) {
        throw new RuntimeException(e); 
      }
    }
  }
}

