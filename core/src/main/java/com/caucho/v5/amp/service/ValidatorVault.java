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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.stream.Stream;

import com.caucho.v5.amp.stub.TransferAsset;
import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.util.L10N;

import io.baratine.service.Result;
import io.baratine.vault.Id;
import io.baratine.vault.Vault;

/**
 * Validation of the configuration
 */
class ValidatorVault
{
  private static final L10N L = new L10N(ValidatorVault.class);
  
  private static final HashMap<Class<?>,Class<?>> _unboxMap = new HashMap<>();
  
  private ValidatorService _validatorService;
  
  ValidatorVault(ValidatorService serviceValidator)
  {
    _validatorService = serviceValidator;
  }
  
  /**
   * {@code Web.service(Class)} validation
   */
  public <T> void vaultClass(Class<T> vaultClass)
  {
    if (! Vault.class.isAssignableFrom(vaultClass)) {
      throw new IllegalStateException();
    }
    
    if (Modifier.isAbstract(vaultClass.getModifiers())
        && ! abstractMethods(vaultClass)) {
      throw error("vault class '{0}' is invalid because the abstract methods can't be generated.",
                  vaultClass.getName());
    }
    
    TypeRef vaultRef = TypeRef.of(vaultClass);
    TypeRef keyRef = vaultRef.to(Vault.class).param(0);
    TypeRef assetRef = vaultRef.to(Vault.class).param(1);
    
    _validatorService.serviceClass(assetRef.rawClass());
    
    idValidation(assetRef.rawClass(), keyRef.rawClass());
    
    createValidation(vaultClass, assetRef.rawClass(), keyRef.rawClass());
    findValidation(vaultClass, assetRef.rawClass(), keyRef.rawClass());
  }
  
  private void idValidation(Class<?> assetClass, Class<?> idClass)
  {
    Field field = findId(assetClass);
    
    if (field == null) {
      throw error("asset class '{0}' does not have an @Id field.",
                  assetClass.getSimpleName());
    }
    
    if (! unbox(idClass).equals(unbox(field.getType()))) {
      throw error("asset id '{0}' does not match Vault id '{1}'.",
                  idClass.getSimpleName(),
                  field.getType().getSimpleName());
    }
  }
  
  /**
   * Validate the create methods.
   */
  private void createValidation(Class<?> vaultClass,
                                Class<?> assetClass, 
                                Class<?> idClass)
  {
    for (Method method : vaultClass.getMethods()) {
      if (! method.getName().startsWith("create")) {
        continue;
      }
      
      if (! Modifier.isAbstract(method.getModifiers())) {
        continue;
      }

      TypeRef resultRef = findResult(method.getParameters());
      
      if (resultRef == null) {
        continue;
      }
      
      TypeRef typeRef = resultRef.to(Result.class).param(0);
      Class<?> typeClass = typeRef.rawClass();
      
      // id return type
      if (unbox(idClass).equals(unbox(typeClass))) {
        continue;
      }
      
      if (void.class.equals(unbox(typeClass))) {
        continue;
      }

      try {
        new TransferAsset<>(assetClass, typeClass);
      } catch (Exception e) {
        throw error(e,
                    "{0}.{1}: {2}",
                    vaultClass.getSimpleName(),
                    method.getName(),
                    e.getMessage());
      }
    }
  }
  
  /**
   * Validate the find methods.
   */
  private void findValidation(Class<?> vaultClass,
                                Class<?> assetClass, 
                                Class<?> idClass)
  {
    for (Method method : vaultClass.getMethods()) {
      if (! method.getName().startsWith("find")) {
        continue;
      }
      
      if (! Modifier.isAbstract(method.getModifiers())) {
        continue;
      }

      TypeRef resultRef = findResult(method.getParameters());
      
      if (resultRef == null) {
        continue;
      }
      
      TypeRef typeRef = resultRef.to(Result.class).param(0);
      Class<?> typeClass = typeRef.rawClass();
      
      // id return type
      if (unbox(idClass).equals(unbox(typeClass))) {
        continue;
      }
      
      if (Collection.class.isAssignableFrom(typeClass)) {
        continue;
      }
      else if (Stream.class.isAssignableFrom(typeClass)) {
        continue;
      }
      else if (Modifier.isAbstract(typeClass.getModifiers())) {
        // assumed to be proxy
        continue;
      }

      new TransferAsset<>(assetClass, typeClass);
    }
  }
  
  private TypeRef findResult(Parameter []params)
  {
    for (Parameter param : params) {
      if (param.getType().equals(Result.class)) {
        return TypeRef.of(param.getParameterizedType());
      }
    }
    
    return null;
  }
  
  private Class<?> unbox(Class<?> type)
  {
    Class<?> unboxType = _unboxMap.get(type);
    
    if (unboxType != null) {
      return unboxType;
    }
    else {
      return type;
    }
  }
  
  private Field findId(Class<?> assetClass)
  {
    if (assetClass == null) {
      return null;
    }
    
    for (Field field : assetClass.getDeclaredFields()) {
      if (field.getAnnotation(Id.class) != null) {
        return field;
      }
      
      if (field.getName().equals("id") || field.getName().equals("_id")) {
        return field;
      }
    }
    
    return findId(assetClass.getSuperclass());
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
    if (method.getName().startsWith("get")) {
      return abstractGetTransfer(serviceClass, method);
    }
    else if (Vault.class.isAssignableFrom(serviceClass)) {
      if (method.getName().startsWith("create")) {
        return true;
      }
      else if (method.getName().startsWith("find")) {
        return true;
      }
    }
    
    return false;
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
  
  private RuntimeException error(Throwable cause,
                                 String msg, Object ...args)
  {
    throw new IllegalArgumentException(L.l(msg, args), cause);
  }
  
  static {
    _unboxMap.put(Boolean.class, boolean.class);
    _unboxMap.put(Character.class, char.class);
    _unboxMap.put(Byte.class, byte.class);
    _unboxMap.put(Short.class, short.class);
    _unboxMap.put(Integer.class, int.class);
    _unboxMap.put(Long.class, long.class);
    _unboxMap.put(Float.class, float.class);
    _unboxMap.put(Double.class, double.class);
    _unboxMap.put(Void.class, void.class);
  }
}
