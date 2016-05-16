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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import com.caucho.v5.amp.Direct;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.util.Hex;

import io.baratine.service.Modify;
import io.baratine.service.Pin;
import io.baratine.service.Result;

/**
 * Creates AMP skeleton method.
 */
abstract class MethodStubBase extends MethodAmpBase
{
  private static final MethodHandle _proxyFilterHandle;
  private static final MethodHandle _proxyReturnFilterHandle;
  
  private final Method _method;
  private final boolean _isDirect;
  private final boolean _isModify;
  private ParameterAmp[] _parameters;
  private int _hash;
  
  protected MethodStubBase(Method method)
  {
    _method = method;
    
    _isDirect = method.isAnnotationPresent(Direct.class);
    _isModify = method.isAnnotationPresent(Modify.class);
  }
  
  @Override
  public String name()
  {
    return _method.getName();
  }
  
  @Override
  public boolean isDirect()
  {
    return _isDirect;
  }
  
  @Override
  public boolean isModify()
  {
    return _isModify;
  }
  
  @Override
  public Class<?> declaringClass()
  {
    return _method.getDeclaringClass();
  }
  
  @Override
  public Annotation []getAnnotations()
  {
    return _method.getAnnotations();
  }
  
  @Override
  public Class<?> getReturnType()
  {
    return _method.getReturnType();
  }
  
  @Override
  public ParameterAmp []parameters()
  {
    if (_parameters == null) {
      _parameters = ParameterAmp.of(_method.getParameters());
    }
    
    return _parameters;
  }
  
  @Override
  public boolean isVarArgs()
  {
    return _method.isVarArgs();
  }
  
  protected MethodHandle filterMethod(ServicesAmp rampManager,
                                      MethodHandle mh,
                                      Method method)
  {
    Parameter []params = method.getParameters();
    //Annotation []methodAnns = method.getAnnotations();

    for (int i = 0; i < params.length; i++) {
      Class<?> paramType = params[i].getType();
      Annotation []paramAnn = params[i].getAnnotations();
      
      if (paramAnn == null) {
        continue;
      }
      
      if (Result.class.equals(paramType)) {
        continue;
      }
      
      Pin pin = getAnnotation(Pin.class, paramAnn);
      
      if (pin == null) {
        continue;
      }

      FilterPinArg filter = new FilterPinArg(rampManager, paramType); 
      
      MethodHandle proxyFilter = _proxyFilterHandle.bindTo(filter);

      mh = MethodHandles.filterArguments(mh, i + 1, proxyFilter);
    }
    
    /*
    Pin pin = getAnnotation(Pin.class, methodAnns);
    
    if (pin != null) {
      FilterPinReturn retFilter
        = new FilterPinReturn(rampManager, method.getReturnType());

      MethodHandle retFilterHandle = _proxyReturnFilterHandle.bindTo(retFilter);

      mh = MethodHandles.filterReturnValue(mh, retFilterHandle);
    }
    */
    
    return mh;
  }
  
  private <T> T getAnnotation(Class<T> annType, Annotation []anns)
  {
    for (Annotation ann : anns) {
      if (ann.annotationType().equals(annType)) {
        return (T) ann;
      }
    }
    
    return null;
  }
  
  protected CharSequence toList(Object []args)
  {
    if (args == null || args.length == 0) {
      return "()";
    }
    
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    
    for (int i = 0; i < args.length; i++) {
      if (i != 0) {
        sb.append(", ");
      }

      Object arg = args[i];
      
      if (arg instanceof byte[]) {
        sb.append(Hex.toShortHex((byte[]) args[i]));
      }
      else {
        sb.append(args[i]);
      }
    }
    
    sb.append(")");
    
    return sb;
  }
  
  @Override
  public int hashCode()
  {
    int hash = _hash;
    
    if (hash != 0) {
      return hash;
    }
    
    hash = _method.getName().hashCode();
    
    if (_parameters != null) {
      for (ParameterAmp param : _parameters) {
        hash = 65521 * hash + param.hashCode();
      }
    }
    
    if (hash == 0) {
      hash = 1;
    }
    
    _hash = hash;
    
    return hash;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (! (obj instanceof MethodAmp)) {
      return false;
    }
    else if (this == obj) {
      return true;
    }
    
    MethodAmp method = (MethodAmp) obj;
    
    if (! name().equals(method.name())) {
      return false;
    }
    
    ParameterAmp []paramA = parameters(); 
    ParameterAmp []paramB = method.parameters();
    
    if (paramA.length != paramB.length) {
      return false;
    }
    
    for (int i = 0; i < paramA.length; i++) {
      if (! paramA[i].rawClass().equals(paramB[i].rawClass())) {
        return false;
      }
    }
    
    return true;
  }

  static {
    MethodHandle argFilterHandle = null;
    MethodHandle retFilterHandle = null;
    
    try {
      Lookup lookup = MethodHandles.lookup();
      argFilterHandle = lookup.findVirtual(FilterPinArg.class, 
                                           "filter", 
                                           MethodType.genericMethodType(1));
      
      retFilterHandle = lookup.findVirtual(FilterPinReturn.class, 
                                           "filter", 
                                           MethodType.genericMethodType(1));
    } catch (Exception e) {
      e.printStackTrace();
      throw new IllegalStateException(e);
    }
    
    _proxyFilterHandle = argFilterHandle;
    _proxyReturnFilterHandle = retFilterHandle;
  }
}
