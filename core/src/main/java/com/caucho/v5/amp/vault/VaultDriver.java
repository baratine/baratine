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

package com.caucho.v5.amp.vault;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.stub.MethodAmp;

import io.baratine.db.Cursor;
import io.baratine.service.Result;
import io.baratine.stream.ResultStream;
import io.baratine.stream.ResultStreamBuilder;

/**
 * Configuration for the resource factory
 */
public interface VaultDriver<ID,T>
{
  default boolean isPersistent()
  {
    return false;
  }

  default void load(ID id, T entity, Result<Boolean> result)
  {
    result.fail(new UnsupportedOperationException(getClass().getName()));
  }

  default void save(ID id, T entity, Result<Void> result)
  {
    result.fail(new UnsupportedOperationException(getClass().getName()));
  }

  default void findOne(String[] fields, Object[] values, Result<ID> result)
  {
    result.fail(new UnsupportedOperationException(getClass().getName()));
  }

  default void findOne(String where, Object[] values, Result<ID> result)
  {
    result.fail(new UnsupportedOperationException(getClass().getName()));
  }

  default void findAllIds(String where, Object[] values, Result<List<ID>> result)
  {
    result.fail(new UnsupportedOperationException(getClass().getName()));
  }

  default void findOneCursor(String where, 
                             Object[] values, 
                             Result<Cursor> result)
  {
    result.fail(new UnsupportedOperationException(getClass().getName()));
  }

  default void findCursor(String sql,
                          Object[] args,
                          Result<Iterable<Cursor>> result)
  {
    result.fail(new UnsupportedOperationException(getClass().getName()));
  }

  default <X> void findValue(String sql, Object[] values, Result<X> result)
  {
    result.fail(new UnsupportedOperationException(getClass().getName()));
  }

  default <X> void findValueList(String sql,
                                 Object[] values,
                                 Result<List<X>> result)
  {
    result.fail(new UnsupportedOperationException(getClass().getName()));
  }

  default <V> MethodVault<V> newMethod(Class<?> type, 
                                       String methodName,
                                       Class<?> []paramTypes)
  {
    if (type == null) {
      return null;
    }
    
    for (Method method : type.getMethods()) {
      if (method.getName().equals(methodName)
          && Arrays.equals(MethodAmp.paramTypes(method),
                           paramTypes)) {
        return newMethod(method);
      }
    }
    
    MethodVault<V> methodVault
      = newMethod(type.getSuperclass(), methodName, paramTypes);
    
    if (methodVault != null) {
      return methodVault;
    }

    for (Class<?> typeIface : type.getInterfaces()) {
      methodVault = newMethod(typeIface, methodName, paramTypes);
      
      if (methodVault != null) {
        return methodVault;
      }
    }

    return null;
  }

  default <V> MethodVault<V> newMethod(Method method)
  {
    return null;
  }

  default ResultStreamBuilder<ID> findAllIds()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  default void findAllIds(ResultStream<ID> stream)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  default List<ID> findIdsList()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  default T toProxy(ID id)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  default ServiceRefAmp lookup(ID id)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  // mixing factor and driver
  default <U, J extends Serializable> VaultDriver<U,J>
  driver(ServicesAmp ampManager,
         Class<?> serviceType,
         Class<U> entityType,
         Class<J> idType,
         String address)
  {
    return (VaultDriver) this;
  }
}
