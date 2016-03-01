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

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.message.HeadersNull;
import com.caucho.v5.amp.message.QueryWithResultMessage_N;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.MethodAmp;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.util.L10N;

import io.baratine.service.Result;
import io.baratine.service.ServiceException;

public class VaultDriverBase<ID,T>
  implements VaultDriver<ID,T>
{
  private static final L10N L = new L10N(VaultDriverBase.class);
  private static final Logger log
    = Logger.getLogger(VaultDriverBase.class.getName());

  private ServiceManagerAmp _ampManager;
  private Class<ID> _idClass;
  private Class<T> _entityClass;
  private String _address;
  
  private Supplier<String> _idGen;
  private String _prefix;

  public VaultDriverBase(ServiceManagerAmp ampManager,
                            Class<T> entityClass,
                            Class<ID> idClass,
                            String address)
  {
    Objects.requireNonNull(ampManager);
    Objects.requireNonNull(entityClass);
    Objects.requireNonNull(idClass);

    _ampManager = ampManager;
    _entityClass = entityClass;
    _idClass = idClass;
    
    if (address == null) {
      address = "/" + entityClass.getSimpleName();
    }
    
    _address = address;
    _prefix = address + "/";
  }

  public String getAddress()
  {
    return _address;
  }
  
  @Override
  public <S> MethodVault<S> newMethod(Method method)
  {
    if (method.getName().startsWith("create")) {
      Method target = entityMethod(method);

      if (target != null) {
        return newCreateMethod(target);
      }
      else {
        return new ResourceMethodNull<>(method.getName() + " " + getClass().getName());
      }
    }
    else {
      return new ResourceMethodNull<>(method.getName() + " " + getClass().getName());
    }
  }
  
  private <S> MethodVault<S> newCreateMethod(Method targetMethod)
  {
    Supplier<String> idGen = idSupplier();
    
    try {
      //targetMethod.setAccessible(true);
      //MethodHandle targetHandle = MethodHandles.lookup().unreflect(targetMethod);
    
      return new ResourceMethodCreate<S>(_ampManager, idGen, targetMethod.getName());
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
      return _entityClass.getMethod(source.getName(), source.getParameterTypes());
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
           + _entityClass
           + "]";
  }

  private static class ResourceMethodNull<S> implements MethodVault<S>
  {
    private String _methodName;
    private RuntimeException _sourceExn;
    
    ResourceMethodNull(String methodName)
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

  private class ResourceMethodCreate<S> implements MethodVault<S>
  {
    private ServiceManagerAmp _ampManager;
    private Supplier<String> _idGen;
    private MethodAmp _method;
    
    ResourceMethodCreate(ServiceManagerAmp ampManager,
                         Supplier<String> idGen,
                         String methodName)
    {
      Objects.requireNonNull(ampManager);
      Objects.requireNonNull(idGen);
      
      _ampManager = ampManager;
      _idGen = idGen;
      
      ServiceRefAmp child = _ampManager.service(_prefix + "0");
      MethodRefAmp methodRef = child.getMethod(methodName);
      
      _method = methodRef.method();
    }
    
    @Override
    public void invoke(Result<S> result, Object[] args)
    {
      String id = _idGen.get();
      
      /*
      int resultIndex = _resultIndex;
      
      Object []fullArgs = new Object[args.length + 1];
      System.arraycopy(args, 0, fullArgs, 0, resultIndex);
      fullArgs[resultIndex] = result;
      System.arraycopy(args, resultIndex, fullArgs, resultIndex + 1, 
                       args.length - _resultIndex);
      */
      
      ServiceRefAmp serviceRef = _ampManager.service(_prefix + id);
      
      long timeout = 10000L;
      
      try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_ampManager)) {
        HeadersAmp headers = HeadersNull.NULL;
      
        QueryWithResultMessage_N<S> msg
        = new QueryWithResultMessage_N<>(outbox,
                                         headers,
                                         result, 
                                         timeout, 
                                         serviceRef,
                                         _method,
                                         args);

        msg.offer(timeout);
      }
    }
  }
}
