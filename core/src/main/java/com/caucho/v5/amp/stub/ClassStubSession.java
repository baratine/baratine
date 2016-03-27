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

package com.caucho.v5.amp.stub;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.service.ServiceConfig;

/**
 * Baratine actor skeleton for a @ServiceChannel
 */
public class ClassStubSession extends ClassStub
{
  private LoadField[] _loadFields;
  //private String _createMethod;

  public ClassStubSession(ServicesAmp rampManager,
                              Class<?> type,
                              ServiceConfig config)
  {
    super(rampManager, type, config);
    
    ArrayList<LoadField> loadFields = new ArrayList<>();
    
    try {
      introspectLoad(loadFields, type);
    } catch (Exception e) {
      throw new IllegalStateException(type.getName() + ": " + e, e);
    }
    
    _loadFields = new LoadField[loadFields.size()];
    loadFields.toArray(_loadFields);
    
    /*
    try {
      introspectCreate(type);
    } catch (Exception e) {
      throw new IllegalStateException(type.getName() + ": " + e, e);
    }
    */
  }
  
  @Override
  public boolean isLifecycleAware()
  {
    return true;
  }

  /*
  public String findCreateMethod()
  {
    return _createMethod;
  }
  */
  
  public void load(Object source, Object target)
  {
    for (LoadField load : _loadFields) {
      load.load(source, target);
    }
  }
  
  private void introspectLoad(ArrayList<LoadField> list,
                              Class<?> type) 
     throws IllegalAccessException
  {
    if (type == null) {
      return;
    }
    
    introspectLoad(list, type.getSuperclass());
    /*
    for (Field field : type.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      
      if (Modifier.isTransient(field.getModifiers())) {
        continue;
      }
      
      field.setAccessible(true);
      
      MethodHandle getter = MethodHandles.lookup().unreflectGetter(field);
      getter = getter.asType(MethodType.genericMethodType(1));
      
      MethodHandle setter = MethodHandles.lookup().unreflectSetter(field);
      setter = setter.asType(MethodType.methodType(void.class, Object.class, Object.class));
      
      list.add(new LoadFieldObject(getter, setter));
    }
      */  
  }

  /*
  private void introspectCreate(Class<?> type) 
     throws IllegalAccessException
  {
    if (type == null) {
      return;
    }
    
    for (Method m : type.getDeclaredMethods()) {
      if (m.isAnnotationPresent(BeforeCreate.class)) {
        _createMethod = m.getName();
        return;
      }
    }
    
    introspectCreate(type.getSuperclass());
  }
  */
  
  private static class LoadField
  {
    void load(Object source, Object target)
    {
    }
  }
  
  private static class LoadFieldObject extends LoadField
  {
    private final MethodHandle _getter;
    private final MethodHandle _setter;
    
    LoadFieldObject(MethodHandle getter,
                    MethodHandle setter)
    {
      _getter = getter;
      _setter = setter;
    }
    
    @Override
    void load(Object source, Object target)
    {
      try {
        Object value = _getter.invokeExact(source);
      
        _setter.invokeExact(target, value);
      } catch (Throwable e) {
        throw new RuntimeException(_setter + ": " + e, e);
      }
    }
  }
}
