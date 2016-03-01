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

import java.lang.reflect.Method;
import java.util.Objects;

import com.caucho.v5.util.L10N;

/**
 * Marshals arguments and results from a module import. 
 */
class MarshalBeanReplace implements ModuleMarshal
{
  private static final L10N L = new L10N(MarshalBeanReplace.class);
  
  private final Class<?> _sourceClass;
  private final Class<?> _targetClass;
  
  private final Method _writeReplace;
  
  private RampImport _moduleImport;

  MarshalBeanReplace(RampImport moduleImport,
                     Class<?> sourceClass,
                     Class<?> targetClass,
                     Method writeReplace)
  {
    Objects.requireNonNull(sourceClass);
    Objects.requireNonNull(targetClass);
    Objects.requireNonNull(writeReplace);
    
    _sourceClass = sourceClass;
    _targetClass = targetClass;
    
    _moduleImport = moduleImport;
    
    _writeReplace = writeReplace;
    
    Objects.requireNonNull(_writeReplace);
    
    _writeReplace.setAccessible(true);
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

    if (! sourceClass.equals(_sourceClass)) {
      ModuleMarshal marshal;
      
      marshal = _moduleImport.marshal(sourceClass, _targetClass);
          
      Object targetValue = marshal.convert(sourceValue);
      
      return targetValue;
    }
    
    try {
      Object replaceValue = _writeReplace.invoke(sourceValue);
      
      ModuleMarshal marshal;
      
      //marshal = _moduleImport.marshal(replaceValue.getClass(), _targetClass);
      marshal = _moduleImport.marshal(replaceValue.getClass());
          
      Object targetValue = marshal.convert(replaceValue);
      
      return targetValue;
    } catch (Throwable e) {
      e.printStackTrace();
      
      return sourceValue;
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _sourceClass.getName() + "]";
  }
}
