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

package com.caucho.v5.amp.marshal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;

import sun.misc.Unsafe;

import com.caucho.v5.config.util.UnsafeUtil;

/**
 * Marshals arguments and results from a module import. 
 */
class MarshalBean implements ModuleMarshal
{
  private static final Logger log
    = Logger.getLogger(MarshalBean.class.getName());
  
  private static final Unsafe _unsafe = UnsafeUtil.getUnsafe();
  
  private final Class<?> _sourceClass;
  private final Class<?> _targetClass;
  
  private final Supplier<Object> _ctorSupplier;
  
  private final MarshalField []_fields;
  private final boolean _isValue;
  private final boolean _isFinal;
  private RampImport _moduleImport;

  private MethodHandle _readResolve;

  MarshalBean(RampImport moduleImport,
              Class<?> sourceClass,
              Class<?> targetClass)
  {
    Objects.requireNonNull(sourceClass);
    Objects.requireNonNull(targetClass);
    
    if (targetClass.isPrimitive() || targetClass.isInterface() || targetClass.isArray()) {
      throw new IllegalArgumentException(String.valueOf(targetClass));
    }
    
    _sourceClass = sourceClass;
    _targetClass = targetClass;
    
    _moduleImport = moduleImport;
    
    try {
      // Lookup lookup = MethodHandles.lookup();
      
      /*
      Constructor<?> ctor = findConstructor(targetClass);
      
      if (ctor == null) {
        throw new IllegalStateException(L.l("Can't find constructor for {0} in {1}",
                                            targetClass, sourceClass));
      }
      ctor.setAccessible(true);

      if (ctor.getParameterTypes().length == 0) {
        MethodHandle mh = lookup.unreflectConstructor(ctor);
      
        mh = mh.asType(MethodType.genericMethodType(0));

        _ctor = mh;
      }
      else {
        _ctor = null;
      }
      */
    
      if (! targetClass.isInterface()) {
        _ctorSupplier = new UnsafeConstructor(targetClass);
      }
      else {
        _ctorSupplier = new UnsafeConstructor(sourceClass);
      }
      
      _readResolve = introspectReadResolve(targetClass);
      
      if (_readResolve == null) {
        _readResolve = introspectReadResolve(sourceClass);
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    
    _moduleImport.addMarshalRef(sourceClass, targetClass, this);
    
    ArrayList<MarshalField> fieldList = new ArrayList<>();
    
    if (targetClass.isInterface()) {
      targetClass = sourceClass;
    }
    
    boolean isValue = introspectFields(fieldList,
                                       moduleImport,
                                       _sourceClass, 
                                       _sourceClass, 
                                       targetClass);

    if (! _sourceClass.equals(_targetClass)) {
      isValue = false;
    }
    
    if (_readResolve != null) {
      isValue = false;
    }
    
    _isValue = isValue;
    
    _fields = new MarshalField[fieldList.size()];
    fieldList.toArray(_fields);
    
    _isFinal = Modifier.isFinal(_sourceClass.getModifiers());
  }
  
  @Override
  public boolean isValue()
  {
    return _isValue && _isFinal;
  }
  
  @Override
  public Object convert(Object sourceValue)
  {
    if (sourceValue == null) {
      return null;
    }
    
    Class<?> sourceClass = sourceValue.getClass();

    if (! sourceClass.equals(_sourceClass)) {
      ModuleMarshal marshal;
      
      marshal = _moduleImport.marshal(sourceClass, _targetClass);
      // marshal = _moduleImport.marshal(sourceClass);
          
      Object targetValue = marshal.convert(sourceValue);
      
      return targetValue;
    }
    else if (_isValue) {
      return sourceValue;
    }
    
    try {
      Object targetValue = newInstance();

      for (MarshalField field : _fields) {
        field.convert(sourceValue, targetValue);
      }

      if (_readResolve != null) {
        targetValue = _readResolve.invoke(targetValue);
      }
    
      return targetValue;
    } catch (StackOverflowError e) {
      log.warning("Recursive object serialization: " + sourceClass.getName());
      
      throw e;
    } catch (Throwable e) {
      e.printStackTrace();
      
      return sourceValue;
    }
  }
  
  private Object newInstance()
    throws Throwable
  {
    // return _ctor.invokeExact();
    return _ctorSupplier.get();
  }
  
  private boolean introspectFields(ArrayList<MarshalField> fieldList,
                                   RampImport moduleImport,
                                   Class<?> sourceClass,
                                   Class<?> sourceTopClass,
                                   Class<?> targetClass)
  {
    if (targetClass == null || Object.class.equals(targetClass)) {
      return true;
    }
    
    boolean isValue;
    
    if (sourceClass != null) {
      isValue = introspectFields(fieldList, 
                                 moduleImport,
                                 sourceClass.getSuperclass(),
                                 sourceTopClass,
                                 targetClass.getSuperclass());
    }
    else {
      isValue = introspectFields(fieldList, 
                                 moduleImport,
                                 null,
                                 sourceTopClass,
                                 targetClass.getSuperclass());
    }
    
    Lookup lookup = MethodHandles.lookup();
    
    for (Field targetField : targetClass.getDeclaredFields()) {
      if (Modifier.isStatic(targetField.getModifiers())) {
        continue;
      }
      
      Field sourceField = findSourceField(targetField, 
                                          sourceClass, 
                                          sourceTopClass);
      
      if (sourceField == null) {
        continue;
      }
      
      try {
        sourceField.setAccessible(true);
        targetField.setAccessible(true);
        
        ModuleMarshal marshal;
        marshal = moduleImport.marshal(sourceField.getType(),
                                       targetField.getType());
        
        if (! marshal.isValue()) {
          isValue = false;
        }

        MethodHandle sourceHandle = lookup.unreflectGetter(sourceField);
        sourceHandle = sourceHandle.asType(MethodType.methodType(Object.class, Object.class));
        
        MethodHandle targetHandle = lookup.unreflectSetter(targetField);
        targetHandle = targetHandle.asType(MethodType.methodType(void.class, Object.class, Object.class));
        
        MarshalField fieldMarshal
          = new MarshalFieldObject(marshal, sourceHandle, targetHandle, targetField);
        
        fieldList.add(fieldMarshal);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    
    return isValue;
  }
  
  private MethodHandle introspectReadResolve(Class<?> cl)
  {
    if (cl == null) {
      return null;
    }
    
    for (Method method : cl.getDeclaredMethods()) {
      if (method.getName().equals("readResolve")
          && method.getParameterTypes().length == 0
          && ! void.class.equals(method.getReturnType())) {
        try {
          method.setAccessible(true);

          Lookup lookup = MethodHandles.lookup();
        
          return lookup.unreflect(method);
        } catch (RuntimeException e) {
          throw e;
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
    
    return introspectReadResolve(cl.getSuperclass());
  }

  private Field findSourceField(Field targetField,
                                Class<?> sourceClass,
                                Class<?> sourceTopClass)
  {
    if (sourceClass != null) {
      for (Field sourceField : sourceClass.getDeclaredFields()) {
        if (Modifier.isStatic(sourceField.getModifiers())) {
          continue;
        }
        
        if (targetField.getName().equals(sourceField.getName())) {
          return sourceField;
        }
      }
    }
    
    return null;
  }
  
  private interface MarshalField
  {
    void convert(Object targetBean, Object sourceBean)
      throws Throwable;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _sourceClass.getName() + "]";
  }
  
  private class MarshalFieldObject implements MarshalField
  {
    private final ModuleMarshal _marshal;
    private final MethodHandle _sourceHandle;
    private final MethodHandle _targetHandle;
    private Field _targetField;
    
    MarshalFieldObject(ModuleMarshal marshal,
                       MethodHandle sourceHandle,
                       MethodHandle targetHandle,
                       Field targetField)
    {
      _marshal = marshal;
      _sourceHandle = sourceHandle;
      _targetHandle = targetHandle;
      _targetField = targetField;
    }
    
    @Override
    public void convert(Object sourceBean, Object targetBean)
      throws Throwable
    {
      Object sourceValue = _sourceHandle.invokeExact(sourceBean);
      
      Object targetValue = _marshal.convert(sourceValue);

      if (targetValue != null) {
        try {
          _targetHandle.invokeExact(targetBean, targetValue);
        } catch (StackOverflowError e) {
          log.warning("Recursive object serialization at " + _targetField);
          
          throw e;
        }
      }
    }
  }
  
  private class UnsafeConstructor implements Supplier<Object> {
    private final Class<?> _cl;
    
    UnsafeConstructor(Class<?> cl)
    {
      _cl = cl;
      
      if (cl.isPrimitive() || cl.isInterface() || cl.isArray()) {
        throw new IllegalArgumentException(String.valueOf(cl));
      }
      
      Objects.requireNonNull(_unsafe);
    }
    
    @Override
    public Object get()
    {
      try {
        return _unsafe.allocateInstance(_cl);
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
