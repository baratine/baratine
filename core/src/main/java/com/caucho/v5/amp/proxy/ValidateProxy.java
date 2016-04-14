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

package com.caucho.v5.amp.proxy;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.ParameterAmp;
import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.util.L10N;

import io.baratine.pipe.ResultPipeIn;
import io.baratine.pipe.ResultPipeOut;
import io.baratine.service.Result;
import io.baratine.service.ServiceException;
import io.baratine.stream.ResultStream;

/**
 * Runtime validator for a proxy
 */
class ValidateProxy
{
  private static final L10N L = new L10N(ValidateProxy.class);
  
  private static final HashSet<Class<?>> _resultClasses = new HashSet<>();
  
  private static final HashSet<String> _methodIgnore = new HashSet<>();
  
  private ServiceRefAmp _serviceRef;
  private Class<?> _proxyClass;

  private StubAmp _stub;

  private ValidateProxy(ServiceRefAmp serviceRef,
                        Class<?> proxyClass)
  {
    _serviceRef = serviceRef;
    _stub = _serviceRef.stub();
    _proxyClass = proxyClass;
  }
  
  static void validate(ServiceRefAmp serviceRef,
                       Class<?> proxyClass)
  {
    if (serviceRef.isClosed()) {
      return;
    }
    
    ValidateProxy validator = new ValidateProxy(serviceRef, proxyClass);
    
    validator.validate();
  }
  
  private void validate()
  {
    for (Method method : _proxyClass.getDeclaredMethods()) {
      if (method.isDefault()) {
        continue;
      }
      if (method.isBridge()) {
        continue;
      }
      
      validateMethod(method);
    }
  }
  
  private void validateMethod(Method method)
  {
    String methodName = method.getName();
    
    if (_methodIgnore.contains(methodName)) {
      return;
    }
    
    //ArrayList<Class<?>> proxyParams = parameters(method);
    Class<?> []paramTypes = MethodAmp.paramTypes(method);
    //proxyParams.toArray(paramTypes);
    
    MethodAmp methodAmp = _stub.method(method.getName(), paramTypes);
    
    if (methodAmp == null || ! methodAmp.isValid()) {
      throw error("{0}.{1}{2} is an unknown method in {3}",
                  proxyClassName(), 
                  method.getName(),
                  toString(paramTypes),
                  stubClassName(_stub));
    }
    
    ParameterAmp []params = methodAmp.parameters();
    
    // methods without parameters don't have validation
    if (params == null) {
      return;
    }
    
    
    if (params.length != paramTypes.length) {
      throw error("{0}.{1}{2} doesn't match {3}.{4}{5}",
                  proxyClassName(),
                  method.getName(),
                  toString(paramTypes),
                  stubClassName(_stub),
                  method.getName(),
                  toString(params));
    }
  }
  
  private String toString(Class<?> []paramTypes)
  {
    StringBuilder sb = new StringBuilder();
    
    sb.append("(");
    for (int i = 0; i < paramTypes.length; i++) {
      if (i != 0) {
        sb.append(",");
      }
      
      sb.append(paramTypes[i].getSimpleName());
    }
    sb.append(")");
    
    return sb.toString();
  }
  
  private String toString(ParameterAmp []params)
  {
    StringBuilder sb = new StringBuilder();
    
    sb.append("(");
    for (int i = 0; i < params.length; i++) {
      if (i != 0) {
        sb.append(",");
      }
      
      sb.append(params[i].rawClass().getSimpleName());
    }
    sb.append(")");
    
    return sb.toString();
  }
  
  
  private String stubClassName(StubAmp stub)
  {
    AnnotatedType api = stub.api();
    
    Type type = api.getType();

    TypeRef typeRef = TypeRef.of(type);
    
    return typeRef.rawClass().getSimpleName();
  }

  /*
  private ArrayList<Class<?>> parameters(Method method)
  {
    ArrayList<Class<?>> params = new ArrayList<>();
    
    for (Class<?> paramType : method.getParameterTypes()) {
      if (! _resultClasses.contains(paramType)) {
        params.add(paramType);
      }
    }
    
    return params;
  }
  */
  
  private String proxyClassName()
  {
    String simpleName = _proxyClass.getSimpleName();
    int p = simpleName.lastIndexOf("__");
    
    if (p > 0) {
      return simpleName.substring(0, p);
    }
    else {
      return simpleName;
    }
  }
  
  private RuntimeException error(String msg, Object ...args)
  {
    return new ServiceException(L.l(msg, args));
  }
  
  static {
    _resultClasses.add(Result.class);
    _resultClasses.add(ResultPipeIn.class);
    _resultClasses.add(ResultPipeOut.class);
    _resultClasses.add(ResultStream.class);
    
    _methodIgnore.add("equals");
    _methodIgnore.add("wait");
    _methodIgnore.add("toString");
    _methodIgnore.add("hashCode");
    _methodIgnore.add("writeReplace");
    _methodIgnore.add("__caucho_getServiceRef");
  }
}
