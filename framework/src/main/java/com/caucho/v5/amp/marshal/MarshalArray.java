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

import java.lang.reflect.Array;
import java.util.Objects;

import com.caucho.v5.amp.marshal.PodImport.MarshalType;

/**
 * Marshals arguments and results from a module import. 
 */
class MarshalArray implements ModuleMarshal
{
  private final Class<?> _sourceClass;
  private final Class<?> _targetClass;
  
  private final ModuleMarshal _valueMarshal;
  private final boolean _isValue;
  private final boolean _isFinal;
  private RampImport _moduleImport;

  MarshalArray(RampImport moduleImport,
               Class<?> sourceClass,
               Class<?> targetClass)
  {
    Objects.requireNonNull(sourceClass);
    Objects.requireNonNull(targetClass);
    
    if (! sourceClass.isArray()) {
      throw new IllegalStateException(String.valueOf(sourceClass));
    }
    
    if (! targetClass.isArray()) {
      throw new IllegalStateException(String.valueOf(targetClass));
    }
    
    _sourceClass = sourceClass;
    _targetClass = targetClass;
    
    _moduleImport = moduleImport;
    
    _isValue = false;
    _isFinal = false;
    
    _valueMarshal = moduleImport.marshal(sourceClass.getComponentType(),
                                         targetClass.getComponentType());
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
    
    /*
    if (_isValue) {
      System.out.println("ISV: " + sourceValue);
      return sourceValue;
    }
    */
    
    Object []sourceArray = (Object []) sourceValue;
    int len = sourceArray.length;
    
    Class<?> eltClass = _targetClass.getComponentType();
    
    Object []targetArray = (Object []) Array.newInstance(eltClass, len); 
    
    for (int i = 0; i < len; i++) {
      targetArray[i] = _valueMarshal.convert(sourceArray[i]);
    }
    
    return targetArray;
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _targetClass + "]";
  }
}
