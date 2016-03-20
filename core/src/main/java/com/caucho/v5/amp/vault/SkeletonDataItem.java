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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.actor.MethodAmpBase;
import com.caucho.v5.amp.proxy.StubClass;
import com.caucho.v5.amp.service.ServiceConfig;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;

import io.baratine.service.Id;
import io.baratine.service.OnLoad;
import io.baratine.service.OnSave;
import io.baratine.service.Result;

/**
 * Actor skeleton for a resource entity.
 */
public class SkeletonDataItem extends StubClass
{
  private static final Logger log
    = Logger.getLogger(SkeletonDataItem.class.getName());
  
  private VaultConfig<?,?> _configResource;

  public SkeletonDataItem(ServiceManagerAmp ampManager, 
                        Class<?> api,
                        ServiceConfig configService,
                        VaultConfig<?,?> configResource)
  {
    super(ampManager, api, configService);
    
    _configResource = configResource;
  }
  
  @Override
  public void introspect()
  {
    super.introspect();
    
    introspectOnLoad();
  }
  
  private void introspectOnLoad()
  {
    VaultDriver<?,?> driver = _configResource.driver();
    
    if (driver == null || ! driver.isPersistent()) {
      return;
    }
    
    Field idField = findIdField(_configResource.entityType());
    if (idField == null) {
      return;
    }
    
    try {
      idField.setAccessible(true);
    
      MethodHandle idGetter = MethodHandles.lookup().unreflectGetter(idField);
      
      if (! isImplemented(OnLoad.class)) {
        onLoad(new MethodOnLoad(idGetter, driver));
      }
      
      if (! isImplemented(OnSave.class)) {
        onSave(new MethodOnSave(idGetter, driver));
      }
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
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
      
      if (field.getName().equals("id") || field.getName().equals("_id")) {
        idField = field;
      }
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
  
  private static class MethodOnLoad extends MethodAmpBase
  {
    private MethodHandle _idGetter;
    private VaultDriver<?,?> _driver;
    
    MethodOnLoad(MethodHandle idGetter,
                 VaultDriver<?,?> driver)
    {
      _idGetter = idGetter;
      _driver = driver;
    }
    
    @Override
    public String name()
    {
      return "@onLoad";
    }
    
    @Override
    public void query(HeadersAmp headers,
                      Result<?> result,
                      ActorAmp actor)
    {
      Object bean = actor.bean();
    
      try {
        Serializable id = (Serializable) _idGetter.invoke(bean);
        
        ((VaultDriver) _driver).load(id, bean, result);
      } catch (Throwable e) {
        result.fail(e);
      }
    }
  }
  
  private static class MethodOnSave extends MethodAmpBase
  {
    private MethodHandle _idGetter;
    private VaultDriver<?,?> _driver;
    
    MethodOnSave(MethodHandle idGetter,
                 VaultDriver<?,?> driver)
    {
      _idGetter = idGetter;
      _driver = driver;
    }
    
    @Override
    public String name()
    {
      return "@OnSave";
    }
    
    @Override
    public void query(HeadersAmp headers,
                      Result<?> result,
                      ActorAmp actor)
    {
      Object bean = actor.bean();
    
      try {
        Serializable id = (Serializable) _idGetter.invoke(bean);
        
        ((VaultDriver) _driver).save(id, bean, Result.ignore());
        
        result.ok(null);
      } catch (Throwable e) {
        result.fail(e);
      }
    }
  }
}
