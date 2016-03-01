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

import java.util.Map;
import java.util.Objects;

/**
 * Marshals arguments and results from a module import. 
 */
class MarshalMap implements ModuleMarshal
{
  private final Class<?> _sourceClass;
  private final Class<?> _targetClass;
  
  private RampImport _moduleImport;

  MarshalMap(RampImport moduleImport,
             Class<?> sourceClass,
             Class<?> targetClass)
  {
    Objects.requireNonNull(sourceClass);
    Objects.requireNonNull(targetClass);
    
    if (! Map.class.isAssignableFrom(sourceClass)) {
      throw new IllegalStateException(String.valueOf(sourceClass));
    }
    
    if (! Map.class.isAssignableFrom(targetClass)) {
      throw new IllegalStateException(String.valueOf(targetClass));
    }
    
    _sourceClass = sourceClass;
    _targetClass = targetClass;
    
    _moduleImport = moduleImport;
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
    
    if (sourceValue.getClass() != _sourceClass) {
      return _moduleImport.convert(sourceValue);
    }

    Map<?,?> source = (Map) sourceValue;
    Map target = newInstance();
    
    for (Map.Entry<?,?> entry : source.entrySet()) {
      Object sourceEntryKey = entry.getKey();
      Object sourceEntryValue = entry.getValue();
      
      Object targetEntryKey = _moduleImport.convert(sourceEntryKey);
      Object targetEntryValue = _moduleImport.convert(sourceEntryValue);
      
      target.put(targetEntryKey, targetEntryValue);
    }
    
    return target;
  }
  
  private Map newInstance()
  {
    try {
      return (Map) _targetClass.newInstance();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _targetClass + "]";
  }
}
