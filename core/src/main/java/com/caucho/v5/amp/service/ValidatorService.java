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

package com.caucho.v5.amp.service;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Objects;

import com.caucho.v5.amp.stub.TransferAsset;
import com.caucho.v5.amp.vault.VaultException;
import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.util.L10N;

import io.baratine.service.Result;
import io.baratine.service.ServiceException;
import io.baratine.service.Shim;
import io.baratine.service.Vault;

/**
 * Validation of the configuration
 */
public class ValidatorService
{
  private static final L10N L = new L10N(ValidatorService.class);
  
  //private ServiceManagerAmp _manager;

  private ValidatorVault _validatorVault;
  
  /*
  private static final HashSet<Class<?>> _includeMethodMetaAnnotations
    = new HashSet<>();
    */
  
  public ValidatorService()
  {
    //_manager = manager;
    
    _validatorVault = new ValidatorVault(this);
  }
  
  /**
   * {@code Web.service(Class)} validation
   */
  public <T> void serviceClass(Class<T> serviceClass)
  {
    if (Vault.class.isAssignableFrom(serviceClass)) {
      _validatorVault.vaultClass(serviceClass);
    }
    else {
      validateServiceClass(serviceClass);
    }
    
    if (Modifier.isAbstract(serviceClass.getModifiers())
        && ! abstractMethods(serviceClass)) {
      throw error(L.l("abstract service class '{0}' is invalid because the abstract methods can't be generated.",
                      serviceClass.getName()));
    }
    
    serviceMethods(serviceClass);
  }
  
  private <T> void validateServiceClass(Class<T> serviceClass)
  {
    Objects.requireNonNull(serviceClass);
    
    if (Vault.class.isAssignableFrom(serviceClass)) {
    }
    else if (serviceClass.isInterface()) {
      throw new IllegalArgumentException(L.l("service class '{0}' is invalid because it's an interface",
                                             serviceClass.getName()));
    }
    
    if (serviceClass.isMemberClass()
        && ! Modifier.isStatic(serviceClass.getModifiers())) {
      throw new IllegalArgumentException(L.l("service class '{0}' is invalid because it's a non-static inner class",
                                             serviceClass.getName()));
    }
    
    if (serviceClass.isPrimitive()) {
      throw new IllegalArgumentException(L.l("service class '{0}' is invalid because it's a primitive class",
                                             serviceClass.getName()));
    }
    
    if (serviceClass.isArray()) {
      throw new IllegalArgumentException(L.l("service class '{0}' is invalid because it's an array",
                                             serviceClass.getName()));
    }
    
    if (Class.class.equals(serviceClass)) {
      throw new IllegalArgumentException(L.l("service class '{0}' is invalid",
                                             serviceClass.getName()));
    }
  }
  
  private <T> boolean abstractMethods(Class<T> serviceClass)
  {
    for (Method method : serviceClass.getDeclaredMethods()) {
      if (Modifier.isAbstract(method.getModifiers())) {
        if (! abstractMethod(serviceClass, method)) {
          return false;
        }
      }
    }
    
    // check all public methods, because there might be a subclass or 
    // interface method that's not implemented
    for (Method method : serviceClass.getMethods()) {
      if (Modifier.isAbstract(method.getModifiers())) {
        if (! abstractMethod(serviceClass, method)) {
          return false;
        }
      }
    }
    
    return true;
  }
  
  private boolean abstractMethod(Class<?> serviceClass, Method method)
  {
    if (Vault.class.isAssignableFrom(serviceClass)) {
      if (method.getName().startsWith("create")) {
        return true;
      }
      else if (method.getName().startsWith("find")) {
        return true;
      }
    }
    
    return false;
  }
  
  private <T> void serviceMethods(Class<T> serviceClass)
  {
    for (Method method : serviceClass.getMethods()) {
      serviceMethod(serviceClass, method);
    }
  }
  
  private void serviceMethod(Class<?> serviceClass, Method method)
  {
    for (Parameter param : method.getParameters()) {
      if (param.getType().equals(Result.class)) {
        if (param.isAnnotationPresent(Shim.class)) {
          shimResult(serviceClass, method, param);
        }
      }
    }
  }
  
  private void shimResult(Class<?> serviceClass, Method method, Parameter param)
  {
    TypeRef valueRef = TypeRef.of(param.getParameterizedType())
                              .to(Result.class)
                              .param(0);
    
    Class<?> valueClass = valueRef.rawClass();
    
    try {
      new TransferAsset<>(serviceClass, valueClass);
    } catch (VaultException e) {
      throw error(e,
                  "{0}.{1}: {2}",
                  serviceClass.getSimpleName(),
                  method.getName(),
                  e.getMessage());
    } catch (Exception e) {
      throw error(e,
                  "{0}.{1}: {2}",
                  serviceClass.getSimpleName(),
                  method.getName(),
                  e.toString());
    }
  }
  
  private boolean abstractGetTransfer(Class<?> serviceClass,
                                      Method method)
  {
    Class<?> []paramTypes = method.getParameterTypes();
    
    if (! void.class.equals(method.getReturnType())) {
      throw error("'{0}.{1}' is an invalid get transfer because transfer getters must return void",
                  method.getDeclaringClass().getSimpleName(),
                  method.getName());
    }
    
  
    if (paramTypes.length != 1) {
      throw error("'{0}.{1}' is an invalid get transfer because transfer getters require a single Result parameter",
                  method.getDeclaringClass().getSimpleName(),
                  method.getName());
    }
    
    if (! Result.class.equals(paramTypes[0])) {
      throw error("'{0}.{1}' is an invalid get transfer because transfer getters require a single Result parameter at {2}",
                  method.getDeclaringClass().getSimpleName(),
                  method.getName(),
                  paramTypes[0].getName());
    }
    
    Type paramType = method.getParameters()[0].getParameterizedType();
    
    if (Result.class.equals(paramType)) {
      throw error("'{0}.{1}' is an invalid get transfer because the Result is not parameterized.\n" +
                  "Transfer getters use the param type to generate the transfer object.",
                  method.getDeclaringClass().getSimpleName(),
                  method.getName(),
                  paramTypes[0].getName());
    }
    
    TypeRef resultRef = TypeRef.of(paramType);
    
    TypeRef transferRef = resultRef.to(Result.class).param(0);
    Class<?> transferClass = transferRef.rawClass();
    
    // transfer asset does its own validation
    new TransferAsset<>(serviceClass, transferClass);
    
    return true;
  }
  
  private RuntimeException error(String msg, Object ...args)
  {
    throw new IllegalArgumentException(L.l(msg, args));
  }
  
  private RuntimeException error(Throwable cause, String msg, Object ...args)
  {
    throw new IllegalArgumentException(L.l(msg, args), cause);
  }
}
