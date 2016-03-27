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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.service.ServiceConfig;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.stub.ClassStubSession;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.config.ConfigExceptionLocation;
import com.caucho.v5.util.L10N;

import io.baratine.service.AfterBatch;
import io.baratine.service.OnActive;
import io.baratine.service.OnDestroy;
import io.baratine.service.OnInit;
import io.baratine.service.OnLookup;
import io.baratine.service.OnSave;
import io.baratine.service.ServiceRef;
import io.baratine.vault.Id;

/**
 * Manager for @ServiceResource.
 */
public class SessionServiceManagerImpl
{
  private static final L10N L = new L10N(SessionServiceManagerImpl.class);
  
  private static final Logger log
    = Logger.getLogger(SessionServiceManagerImpl.class.getName());
  
  
  private final Class<?> _classResource;
  private final Supplier<?> _supplierBean;

  private MethodHandle _setter;

  private ContextSession _context;
  
  private ServiceRef _serviceRefSelf;

  private ClassStubSession _skeleton;
  
  public SessionServiceManagerImpl(String path,
                                   ServiceManagerAmp ampManager,
                                   Class<?> classResource,
                                   Supplier<?> supplierBean,
                                   ServiceConfig config)
  {
    _classResource = classResource;
    _supplierBean = supplierBean;
    
    _skeleton = new ClassStubSession(ampManager, classResource, config);
    
    ContextSessionFactory factory = new ContextSessionFactory(ampManager);
    
    //_isJournal = classResource.isAnnotationPresent(Journal.class);
    boolean isJournal = false;
    
    _context = factory.create(path, _classResource, isJournal);
    _context.setSkeleton(_skeleton);
    
    introspect(_classResource);
  }

  public void setServiceRef(ServiceRefAmp serviceRef)
  {
    _context.setServiceRef(serviceRef);
  }
  
  //
  // startup
  //
  
  @OnActive
  public void onActive()
  {
    _serviceRefSelf = ServiceRef.current();
    
    _context.setServiceRef(_serviceRefSelf);
  }
  
  //
  // create
  //
  
  /*
  public void create(Result<ServiceRef> result, Object []args)
  {
    //_context.create(result, args);
  }
  */
  
  //
  // find
  //
  
  /*
  public void findOne(Result<ServiceRef> result, String query, Object []args)
  {
    _context.findOne(result, query, args);
  }
  
  public void findAll(Result<Iterable<ServiceRef>> result, String query, Object []args)
  {
    _context.findAll(result, query, args);
  }
  
  public void stream(Consumer<?> supplier, Result<Boolean> result)
  {
    _context.stream(supplier, result);
  }
  */
  
  //
  // lookup
  //

  @OnLookup
  public Object lookup(String subPath)
  {
    int p = subPath.indexOf('/');
    
    if (p < 0) {
      return null;
    }
    
    int q = subPath.indexOf('/', p + 1);
    
    String keyString;
    
    if (q > 0) {
      keyString = subPath.substring(p, q);
    }
    else {
      keyString = subPath.substring(p + 1);
    }
    
    try {
      Object bean = _supplierBean.get();
      
      if (_setter != null) {
        //Object key = _marshal.parse(keyString);
        Object key = keyString;
        
        _setter.invoke(bean, key);
      }

      return _context.createActorSession(bean, keyString);
    } catch (Throwable e) {
      log.log(Level.FINER, _supplierBean + ": " + subPath + ": " + e.toString(), e);

      return null;
    }
  }
  
  @OnInit
  public void onRestore()
  {
    _context.restore();
  }
  
  @AfterBatch
  public void afterBatch()
  {
    _context.flush();
  }
  
  @OnSave
  public void onCheckpoint()
  {
    _context.flushImpl();
  }
  
  @OnDestroy
  public void onShutdown(ShutdownModeAmp mode)
  {
    _context.shutdown(mode);
  }
  
  //
  // introspection
  //
  private boolean introspect(Class<?> cl)
  {
    if (cl == null) {
      return false;
    }
    
    for (Field field : cl.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      
      if (field.isAnnotationPresent(Id.class)) {
        introspectSetter(field);
        
        return true;
      }
    }
    
    return introspect(cl.getSuperclass());
  }
  
  private void introspectSetter(Field field)
  {
    try {
      field.setAccessible(true);
    
      MethodHandle setter = MethodHandles.lookup().unreflectSetter(field);
    
      setter = setter.asType(MethodType.methodType(void.class, Object.class, Object.class));
    
      _setter = setter;

      if (! String.class.equals(field.getType())) {
        throw new ConfigException(L.l("{0} is an unsupported primary field type",
                                      field.getType().getName()));
      }
    } catch (Exception e) {
      throw ConfigExceptionLocation.wrap(field, e);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _classResource.getSimpleName() + "]";
  }
}
