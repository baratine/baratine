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

package com.caucho.v5.amp.session;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import javax.inject.Provider;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.service.ServiceConfig;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.StubContainerAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.MethodAmpBase;
import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.amp.stub.StubAmpBean;
import com.caucho.v5.amp.stub.StubClass;
import com.caucho.v5.convert.bean.FieldBean;
import com.caucho.v5.convert.bean.FieldString;
import com.caucho.v5.inject.impl.ServiceImpl;
import com.caucho.v5.util.L10N;

import io.baratine.inject.Key;
import io.baratine.service.OnLookup;
import io.baratine.service.Result;
import io.baratine.service.ResultChain;
import io.baratine.vault.Id;

/**
 * Stub for a vault.
 */
public class StubClassSessionVault<T> extends StubClass
{
  private static final L10N L = new L10N(StubClassSessionVault.class);
  
  private StubClass _stubClassSession;

  private Class<T> _type;

  public StubClassSessionVault(ServicesAmp services,
                               Class<T> type)
  {
    super(services, 
          SessionVaultImpl.class, 
          SessionVaultImpl.class);

    _type = type;

    _stubClassSession = new StubClass(services,
                                      _type,
                                      _type);
    
    _stubClassSession.introspect();
  }

  @Override
  public void introspect()
  {
    super.introspect();

    introspectOnLookup();
  }

  private void introspectOnLookup()
  {
    if (isImplemented(OnLookup.class)) {
      return;
    }

    Key<T> key = Key.of(_type, ServiceImpl.class);

    // XXX: provider should be passed in? Non-injector session provider?
    Provider<T> provider = services().injector().provider(key);
    FieldBean<T> setter = findIdSetter();
    
    MethodAmp onLookup = new MethodOnLookup(_stubClassSession, provider, setter);

    onLookup(onLookup);
  }

  private FieldBean<T> findIdSetter()
  {
    Field idField = findIdField(_type);
    
    if (idField == null) {
      return null;
    }
    
    if (! String.class.equals(idField.getType())) {
      throw new IllegalStateException(L.l("{0} is an invalid session id.",
                                          idField));
    }
    

    return new FieldString<>(idField);
  }

  private Field findIdField(Class<?> entityClass)
  {
    if (entityClass == null) {
      return null;
    }

    Field idField = null;

    for (Field field : entityClass.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }

      if (field.isAnnotationPresent(Id.class)) {
        return field;
      }

      /*
      if (field.getName().equals("id") || field.getName().equals("_id")) {
        idField = field;
      }
      */
    }

    Field idParent = findIdField(entityClass.getSuperclass());

    if (idParent != null && idParent.isAnnotationPresent(Id.class)) {
      return idParent;
    }
    else if (idField != null) {
      return idField;
    }
    else {
      return idParent;
    }
  }
  
  /**
   * @OnLookup implementation
   */
  private class MethodOnLookup extends MethodAmpBase
  {
    private StubClass _stubClassSession;
    private Provider<T> _provider;
    private FieldBean<T>_fieldSetter;

    MethodOnLookup(StubClass skel,
                   Provider<T> provider,
                   FieldBean<T> fieldSetter)
    {
      _stubClassSession = skel;
      _provider = provider;
      _fieldSetter = fieldSetter;
    }

    @Override
    public void query(HeadersAmp headers,
                      ResultChain<?> result,
                      StubAmp stub,
                      Object arg1)
    {
      T session = _provider.get();

      String path = (String) arg1;

      if (path.startsWith("/")) {
        path = path.substring(1);
      }

      if (_fieldSetter != null) {
        _fieldSetter.setString(session, path);
      }

      StubAmpBean stubBean = (StubAmpBean) stub;
      StubContainerAmp container = stubBean.container();

      StubAmp stubChild = new StubAmpBean(_stubClassSession,
                                           session,
                                           null,
                                           container,
                                           ServiceConfig.NULL);

      ((Result) result).ok(stubChild);
    }
  }
}
