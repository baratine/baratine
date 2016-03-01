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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Marshals arguments and results from a module import. 
 */
class MarshalBeanFromMap implements ModuleMarshal
{
  private final Class<?> _sourceClass;
  private final Class<?> _targetClass;
  private final MethodHandle _ctor;
  
  private final MarshalField []_fields;
  private RampImport _moduleImport;

  MarshalBeanFromMap(RampImport moduleImport,
                     Class<?> sourceClass,
                     Class<?> targetClass)
  {
    Objects.requireNonNull(targetClass);
    Objects.requireNonNull(sourceClass);
    
    if (targetClass.isInterface()) {
      targetClass = HashMap.class;
    }
    
    _targetClass = targetClass;
    _sourceClass = sourceClass;
    
    _moduleImport = moduleImport;
    
    try {
      Lookup lookup = MethodHandles.lookup();
      
      Constructor<?> []ctors = targetClass.getDeclaredConstructors();
      
      Constructor<?> ctor = null;
      
      for (Constructor<?> ctorItem : ctors) {
        if (ctorItem.getParameterTypes().length == 0) {
          ctor = ctorItem;
        }
      }
      
      
      if (ctors.length == 0) {
        throw new IllegalStateException(targetClass.toString());
      }

      ctor.setAccessible(true);

      MethodHandle mh = lookup.unreflectConstructor(ctor);
      
      mh = mh.asType(MethodType.genericMethodType(0));

      _ctor = mh;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    
    ArrayList<MarshalField> fieldList = new ArrayList<>();
    
    introspectFields(fieldList,
                     moduleImport,
                     _targetClass);
    
    _fields = new MarshalField[fieldList.size()];
    fieldList.toArray(_fields);
  }
  
  @Override
  public boolean isValue()
  {
    return false;
  }
  
  @Override
  public Object convert(Object sourceValue)
  {
    if (sourceValue == null) {
      return null;
    }
    
    Class<?> sourceClass = sourceValue.getClass();
    
    if (sourceClass != _sourceClass) {
      return _moduleImport.marshal(sourceClass, _targetClass).convert(sourceValue);
    }
    
    try {
      Object targetValue = newInstance();
      
      Map<String,Object> sourceMap = (Map<String,Object>) sourceValue;

      for (MarshalField field : _fields) {
        field.convert(sourceMap, targetValue);
      }
    
      return targetValue;
    } catch (Throwable e) {
      e.printStackTrace();
      
      return sourceValue;
    }
  }
  
  protected Object newInstance()
    throws Throwable
  {
    return _ctor.invokeExact();
  }
  
  private void introspectFields(ArrayList<MarshalField> fieldList,
                                RampImport moduleImport,
                                Class<?> targetClass)
  {
    if (targetClass == null || Object.class.equals(targetClass)) {
      return;
    }
    
    introspectFields(fieldList,
                     moduleImport,
                     targetClass.getSuperclass());
    
    Lookup lookup = MethodHandles.lookup();
    
    for (Field targetField : targetClass.getDeclaredFields()) {
      if (Modifier.isStatic(targetField.getModifiers())) {
        continue;
      }
      
      try {
        targetField.setAccessible(true);
        
        ModuleMarshal marshal;
        marshal = moduleImport.marshal(Object.class,
                                       targetField.getType());
        
        MethodHandle targetHandle = lookup.unreflectSetter(targetField);
        targetHandle = targetHandle.asType(MethodType.methodType(void.class, Object.class, Object.class));
      
        MarshalField fieldMarshal = new MarshalFieldObject(targetField.getName(),
                                                           marshal, 
                                                           targetHandle);
        
        fieldList.add(fieldMarshal);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
  
  private interface MarshalField
  {
    void convert(Map<String,Object> sourceMap, Object targetBean)
      throws Throwable;
  }
  
  private class MarshalFieldObject implements MarshalField
  {
    private final String _fieldName;
    private final ModuleMarshal _marshal;
    private final MethodHandle _targetHandle;
    
    MarshalFieldObject(String fieldName,
                       ModuleMarshal marshal,
                       MethodHandle targetHandle)
    {
      _fieldName = fieldName;
      _marshal = marshal;
      _targetHandle = targetHandle;
    }
    
    @Override
    public void convert(Map<String,Object> sourceMap, Object targetBean)
      throws Throwable
    {
      Object sourceValue = sourceMap.get(_fieldName);
      
      Object targetValue = _marshal.convert(sourceValue);

      if (targetValue != null) {
        _targetHandle.invokeExact(targetBean, targetValue);
      }
    }
  }
}
