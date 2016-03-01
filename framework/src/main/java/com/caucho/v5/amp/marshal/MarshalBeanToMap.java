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
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Marshal from a bean to an object map.
 */
class MarshalBeanToMap implements ModuleMarshal
{
  private final Class<?> _sourceClass;
  private final Class<?> _targetClass;
  
  private final MarshalField []_fields;
  private PodImport _moduleImport;

  MarshalBeanToMap(PodImport moduleImport,
                 Class<?> sourceClass,
                 Class<?> targetClass)
  {
    Objects.requireNonNull(targetClass);
    Objects.requireNonNull(sourceClass);
    
    _targetClass = targetClass;
    _sourceClass = sourceClass;
    
    _moduleImport = moduleImport;
    
    ArrayList<MarshalField> fieldList = new ArrayList<>();
    
    introspectFields(fieldList, moduleImport, _sourceClass);
    
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
      return _moduleImport.marshalArg(sourceClass, _targetClass).convert(sourceValue);
    }
    
    try {
      Map<String,Object> targetMap = newInstance();
      
      for (MarshalField field : _fields) {
        field.convert(sourceValue, targetMap);
      }
    
      return targetMap;
    } catch (Throwable e) {
      e.printStackTrace();
      
      return sourceValue;
    }
  }
  
  protected Map<String,Object> newInstance()
    throws Throwable
  {
    return new HashMap<String,Object>();
  }
  
  private void introspectFields(ArrayList<MarshalField> fieldList,
                                PodImport moduleImport,
                                Class<?> sourceClass)
  {
    if (sourceClass == null || Object.class.equals(sourceClass)) {
      return;
    }
    
    introspectFields(fieldList, 
                     moduleImport,
                     sourceClass.getSuperclass());
    
    Lookup lookup = MethodHandles.lookup();
    
    for (Field sourceField : sourceClass.getDeclaredFields()) {
      if (Modifier.isStatic(sourceField.getModifiers())) {
        continue;
      }
      
      try {
        sourceField.setAccessible(true);
        
        ModuleMarshal marshal;
        marshal = moduleImport.marshalArg(sourceField.getType(),
                                          Object.class);
        
        MethodHandle sourceHandle = lookup.unreflectGetter(sourceField);
        sourceHandle = sourceHandle.asType(MethodType.methodType(Object.class, Object.class));
      
        MarshalField fieldMarshal = new MarshalFieldObject(sourceField.getName(),
                                                           marshal, 
                                                           sourceHandle);
        
        fieldList.add(fieldMarshal);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
  
  private interface MarshalField
  {
    void convert(Object sourceBean, Map<String,Object> targetMap)
      throws Throwable;
  }
  
  private class MarshalFieldObject implements MarshalField
  {
    private final String _fieldName;
    private final ModuleMarshal _marshal;
    private final MethodHandle _sourceHandle;
    
    MarshalFieldObject(String fieldName,
                       ModuleMarshal marshal,
                       MethodHandle sourceHandle)
    {
      _fieldName = fieldName;
      _marshal = marshal;
      _sourceHandle = sourceHandle;
    }
    
    @Override
    public void convert(Object sourceBean, Map<String,Object> targetMap)
      throws Throwable
    {
      Object sourceValue = _sourceHandle.invokeExact(sourceBean);
      
      Object targetValue = _marshal.convert(sourceValue);

      if (targetValue != null) {
        targetMap.put(_fieldName, targetValue);
      }
    }
  }
}
