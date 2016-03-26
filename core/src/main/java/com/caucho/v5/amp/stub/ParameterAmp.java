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

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;

import com.caucho.v5.inject.type.TypeRef;


/**
 * method parameter
 */
public interface ParameterAmp
{
  String name();
  
  Type type();
  
  Class<?> rawClass();
  
  Annotation[] annotations();
  
  static ParameterAmp of(Parameter parameter)
  {
    return new ParameterAmpImpl(parameter);
  }
  
  static ParameterAmp of(Class<?> rawClass)
  {
    return new ParameterAmpClass(rawClass);
  }
  
  static ParameterAmp of(Type type)
  {
    return new ParameterAmpClass(TypeRef.of(type).rawClass());
  }
  
  static ParameterAmp []of(Parameter []parameters)
  {
    ParameterAmp []parametersAmp = new ParameterAmp[parameters.length];
    
    for (int i = 0; i < parameters.length; i++) {
      parametersAmp[i] = of(parameters[i]);
    }
    
    return parametersAmp;
  }
}
