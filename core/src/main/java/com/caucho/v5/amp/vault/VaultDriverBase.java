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
 * @author Alex Rojkov
 */

package com.caucho.v5.amp.vault;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.message.HeadersNull;
import com.caucho.v5.amp.message.QueryWithResultMessage_N;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.MethodAmpBase;
import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.amp.stub.TransferAsset;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.convert.bean.FieldBean;
import com.caucho.v5.convert.bean.FieldBeanFactory;
import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.util.L10N;

import io.baratine.service.Result;
import io.baratine.vault.Id;

public class VaultDriverBase<ID,T>
  implements VaultDriver<ID,T>
{
  private static final L10N L = new L10N(VaultDriverBase.class);
  private static final Logger log
    = Logger.getLogger(VaultDriverBase.class.getName());

  private ServicesAmp _ampManager;
  private Class<ID> _idClass;
  private Class<T> _assetClass;
  private String _address;
  
  private Supplier<String> _idGen;
  private String _prefix;
  
  private FieldBean<T> _idField;

  public VaultDriverBase(ServicesAmp ampManager,
                         Class<T> assetClass,
                         Class<ID> idClass,
                         String address)
  {
    Objects.requireNonNull(ampManager);
    Objects.requireNonNull(assetClass);
    Objects.requireNonNull(idClass);

    _ampManager = ampManager;
    _assetClass = assetClass;
    _idClass = idClass;
    
    if (address == null) {
      address = "/" + assetClass.getSimpleName();
    }
    
    _address = address;
    _prefix = address + "/";
    
    _idField = introspectId(assetClass);
    
    if (_idField == null && ! Void.class.equals(idClass)) {
      throw new VaultException(L.l("Missing @Id for asset '{0}'",
                                   assetClass.getName()));
    }
  }
  
  private FieldBean<T> introspectId(Class<?> assetClass)
  {
    if (assetClass == null) {
      return null;
    }
    
    for (Field field : assetClass.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      
      if (field.isAnnotationPresent(Id.class)) {
        return FieldBeanFactory.get(field);
      }
      else if (field.getName().equals("id")) {
        return FieldBeanFactory.get(field);
      }
      else if (field.getName().equals("_id")) {
        return FieldBeanFactory.get(field);
      }
    }
    
    return introspectId(assetClass.getSuperclass());
  }

  public String getAddress()
  {
    return _address;
  }
  
  @Override
  public <S> MethodVault<S> newMethod(Method method)
  {
    if (! Modifier.isAbstract(method.getModifiers())) {
      throw new IllegalStateException(String.valueOf(method));
    }
    else if (method.getName().startsWith("create")) {
      Method target = entityMethod(method);

      if (target != null) {
        return newCreateMethod(target);
      }
      else {
        return newCreateMethodDTO(method);
      }
    }
    else {
      return new MethodVaultNull<>(method.getName() + " " + getClass().getName());
    }
  }
  
  private <S> MethodVault<S> newCreateMethod(Method targetMethod)
  {
    Supplier<String> idGen = idSupplier();
    
    try {
      //targetMethod.setAccessible(true);
      //MethodHandle targetHandle = MethodHandles.lookup().unreflect(targetMethod);
    
      return new MethodVaultCreate<S>(_ampManager, idGen, targetMethod);
    } catch (Exception e) {
      e.printStackTrace();;
      throw new IllegalStateException(e);
    }
  }
  
  private <S> MethodVault<S> newCreateMethodDTO(Method vaultMethod)
  {
    Supplier<String> idGen = idSupplier();
    
    try {
      Class<?> []params = vaultMethod.getParameterTypes();
      
      if (params.length != 2) {
        throw new ConfigException(L.l("'{0}' is an invalid vault create.",
                                      vaultMethod));
      }
      
      TransferAsset<T,?> transfer = new TransferAsset<>(_assetClass, params[0]);
      
      TypeRef resultRef = TypeRef.of(vaultMethod.getGenericParameterTypes()[1]);
      TypeRef valueRef = resultRef.to(Result.class).param(0);
      
      MethodAmp methodAmp;
   
      if (valueRef.rawClass().equals(_idField.field().getType())) {
        methodAmp = new MethodAmpCreateDTO<>(transfer, _idField);
      }
      else {
        methodAmp = new MethodAmpCreateDTO<>(transfer, null);
      }
    
      return new MethodVaultCreateDTO<S>(_ampManager, idGen, 
                                        vaultMethod,
                                        methodAmp);
    } catch (Exception e) {
      e.printStackTrace();;
      throw new IllegalStateException(e);
    }
  }
  
  private Supplier<String> idSupplier()
  {
    synchronized (this) {
      if (_idGen == null) {
        _idGen = VaultIdGenerator.create(_idClass);
      }
      
      return _idGen;
    }
  }
  
  private Method entityMethod(Method source)
  {
    try {
      return _assetClass.getMethod(source.getName(), source.getParameterTypes());
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
      
      return null;
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName()
           + "["
           + _assetClass
           + "]";
  }

  private static class MethodVaultNull<S> implements MethodVault<S>
  {
    private String _methodName;
    private RuntimeException _sourceExn;
    
    MethodVaultNull(String methodName)
    {
      _methodName = methodName;
      
      _sourceExn = new UnsupportedOperationException(_methodName);
      _sourceExn.fillInStackTrace();
    }
    
    @Override
    public void invoke(Result<S> result, Object[] args)
    {
      throw new UnsupportedOperationException(_sourceExn);
    }
  }

  private class MethodVaultCreate<S> implements MethodVault<S>
  {
    private ServicesAmp _ampManager;
    private Supplier<String> _idGen;
    private String _methodName;
    private MethodAmp _method;
    private Class<?>[] _paramTypes;
    
    MethodVaultCreate(ServicesAmp ampManager,
                      Supplier<String> idGen,
                      Method method)
    {
      Objects.requireNonNull(ampManager);
      Objects.requireNonNull(idGen);
      Objects.requireNonNull(method);
      
      _ampManager = ampManager;
      _idGen = idGen;
      _methodName = method.getName();
      _paramTypes = MethodAmp.paramTypes(method);
    }
    
    private MethodAmp method(ServiceRefAmp childRef)
    {
      if (_method == null) {
        // XXX:
        Class<?> returnType = void.class;
        
        _method = childRef.method(_methodName, returnType, _paramTypes).method();
      }
      
      return _method;
    }
    
    @Override
    public void invoke(Result<S> result, Object[] args)
    {
      String id = _idGen.get();

      ServiceRefAmp childRef = _ampManager.service(_prefix + id);

      long timeout = 10000L;
      
      try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_ampManager)) {
        HeadersAmp headers = HeadersNull.NULL;
      
        QueryWithResultMessage_N<S> msg
        = new QueryWithResultMessage_N<>(outbox,
                                         headers,
                                         result, 
                                         timeout, 
                                         childRef,
                                         method(childRef),
                                         args);

        msg.offer(timeout);
      }
    }
  }

  private class MethodVaultCreateDTO<S> implements MethodVault<S>
  {
    private ServicesAmp _ampManager;
    private Supplier<String> _idGen;
    //private String _methodName;
    private MethodAmp _method;
    
    MethodVaultCreateDTO(ServicesAmp ampManager,
                         Supplier<String> idGen,
                         Method method,
                         MethodAmp methodAmp)
    {
      Objects.requireNonNull(ampManager);
      Objects.requireNonNull(idGen);
      Objects.requireNonNull(method);
      Objects.requireNonNull(method);
      
      _ampManager = ampManager;
      _idGen = idGen;
      //_methodName = methodName;
      _method = methodAmp;
    }
    
    @Override
    public void invoke(Result<S> result, Object[] args)
    {
      String id = _idGen.get();

      ServiceRefAmp childRef = _ampManager.service(_prefix + id);

      long timeout = 10000L;
      
      try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_ampManager)) {
        HeadersAmp headers = HeadersNull.NULL;
      
        QueryWithResultMessage_N<S> msg
        = new QueryWithResultMessage_N<>(outbox,
                                         headers,
                                         result, 
                                         timeout, 
                                         childRef,
                                         _method,
                                         args);

        msg.offer(timeout);
      }
    }
  }

  private class MethodAmpCreateDTO<S> extends MethodAmpBase
  {
    private TransferAsset<T,S> _transfer;
    private FieldBean<T> _idField;
    
    MethodAmpCreateDTO(TransferAsset<T,S> transfer,
                       FieldBean<T> idField)
    {
      Objects.requireNonNull(transfer);
      
      _transfer = transfer;
      _idField = idField;
    }
    
    @Override
    public void query(HeadersAmp headers,
                      Result<?> result,
                      StubAmp stub,
                      Object []args)
    {
      T asset = (T) stub.bean();
      S transfer = (S) args[0];
      
      Objects.requireNonNull(asset);
      Objects.requireNonNull(transfer);
      
      _transfer.toAsset(asset, transfer);
      
      stub.onModify();
      
      if (_idField != null) {
        ((Result) result).ok(_idField.getObject(asset));
      }
      else {
        result.ok(null);
      }
    }
  }
}
