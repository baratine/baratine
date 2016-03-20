/*
- * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
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

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.Consumer;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.proxy.StubClassSession;
import com.caucho.v5.amp.service.ServiceConfig;
import com.caucho.v5.amp.service.ServiceRefSession;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.ProxyFactoryAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.util.Murmur32;

import io.baratine.service.Result;
import io.baratine.service.ServiceRef;

/**
 * Context for a service resource.
 */
public class ContextSession
{
  private String _pathRoot;
  private String _pathManager;
  
  private int _idStore;
  
  private HashSet<ActorSkeletonSession> _dirtySet = new HashSet<>();
  
  private HashMap<Object,ServiceRef> _serviceMap = new HashMap<>();

  private ServiceRefAmp _serviceRefSelf;

  private StubClassSession _skeleton;

  private ServiceManagerAmp _ampManager;

  private StateResourceManager _state = new StateResourceManager();

  private ServiceConfig _config = null;
  private boolean _isJournal;
  private boolean _isDirtyManager;

  private boolean _isKey;
  
  public ContextSession(ServiceManagerAmp ampManager,
                         String path,
                         boolean isJournal)
  {
    // Objects.requireNonNull(table);
    
    _ampManager = ampManager;
    
    int p = path.indexOf("/{");
    
    if (p >= 0) {
      _pathManager = path.substring(0, p);
      _pathRoot = _pathManager + "/";
    }
    else {
      _pathRoot = path;
    }
    
    
    _isKey = true;

    _idStore = Murmur32.generate(Murmur32.SEED, _pathRoot);
    
    _isJournal = isJournal;
  }

  public void setServiceRef(ServiceRef serviceRefSelf)
  {
    _serviceRefSelf = (ServiceRefAmp) serviceRefSelf;
  }

  public String getPathTail(String path)
  {
    if (_serviceRefSelf == null) {
      return path;
    }
    
    //String address = _serviceRefSelf.address();
    String address = _pathRoot;
    
    if (path.startsWith(address)) {
      return path.substring(address.length());
    }
    else {
      return path;
    }
  }

  public ServiceRef createActorSession(Object bean, String key)
  {
    ServiceRef serviceRef = _serviceMap.get(key);
    
    if (serviceRef == null) {
      //String address = _serviceRefSelf.address() + "/" + key;
      String address = _pathRoot + "/" + key;

      //ActorAmp actor = _context.createActorResource(bean, key);
      
      ProxyFactoryAmp proxyFactory = _ampManager.getProxyFactory();
      
      ActorAmp actor = proxyFactory.createSkeletonSession(bean, key, this, _config);
    
      //actor.onInit(Result.ignore());
      //actor.onActive(Result.ignore());
      
      InboxAmp inbox = _serviceRefSelf.inbox();
    
      serviceRef = new ServiceRefSession(address, actor, inbox);
      
      _serviceMap.put(key, serviceRef);
    }
    
    return serviceRef;
  }

  public void setSkeleton(StubClassSession skeleton)
  {
    _skeleton = skeleton;
  }
  
  private ServiceRef createResourceRef(Object key)
  {
    String address = String.valueOf(key); // _serviceRefSelf.getAddress() + "/" + key;
    
    return _serviceRefSelf.manager().service(address);
  }

  /*
  public void create(Result<ServiceRef> result, Object[] args)
  {
    if (! _isKey) {
      throw new IllegalStateException();
    }
    
    _isDirtyManager = true;
    
    long key = _state.nextCounter();
    
    String address = _serviceRefSelf.getAddress() + "/" + key;

    ServiceRef serviceRef = _serviceRefSelf.getManager().lookup(address);
    
    String createMethodName = _skeleton.findCreateMethod();
    
    if (createMethodName != null) {
      MethodRef methodRef = serviceRef.getMethod(createMethodName);

      methodRef.query(null, new ResultCreate(result, serviceRef), args);
    }
    else {
      result.complete(serviceRef);
    }
  }
  */

  public void onRestore(ActorSkeletonSession resource)
  {
  }

  public void findOne(Result<ServiceRef> result,
                      String query, 
                      Object[] args)
  {
    result.fail(new UnsupportedOperationException(getClass().getName()));
  }
  
  public void findAll(Result<Iterable<ServiceRef>> result,
                      String query,
                      Object[] args)
  {
    result.fail(new UnsupportedOperationException(getClass().getName()));
  }

  /**
   * Stream operations
   */
  public void stream(Consumer consumer, Result<Boolean> result)
  {
    for (ServiceRef service : _serviceMap.values()) {
      ServiceRefAmp serviceAmp = (ServiceRefAmp) service;
      
      ActorSkeletonSession actor = (ActorSkeletonSession) serviceAmp.getActor();
      
      consumer.accept(actor.bean());
    }

    result.ok(true);
  }
  
  void addDirty(ActorSkeletonSession resource)
  {
    Objects.requireNonNull(resource);
    
    _dirtySet.add(resource);
  }
  
  void restore()
  {
    /*
    TableResourceService table = _table;
    
    if (table == null || ! _isKey) {
      return;
    }
    
    ServiceFuture<Cursor> future = new ServiceFuture<>();
    
    table.get(_idStore, _pathManager, future);
    
    Cursor cursor = future.get();
    
    if (cursor != null) {
      StateResourceManager state = (StateResourceManager) cursor.getObject(1);
      
      if (state != null) {
        _state = state;
      }
    }
    */
  }
  
  void delete(ActorSkeletonSession resource)
  {
    _dirtySet.remove(resource);
    
    //_table.delete(_idStore, resource.getId());
  }
  
  void flush()
  {
    if (! _isJournal) {
      flushImpl();
    }
  }
  
  void flushImpl()
  {
    /*
    TableResourceService table = _table;

    if (table == null) {
      _dirtySet.clear();
      return;
    }

    for (ActorSkeletonChannel resource : _dirtySet) {
      table.put(_idStore, resource.getId(), resource.getBean());

      resource.afterFlush();
    }
    
    _dirtySet.clear();

    // save changes to the manager state
    if (_isDirtyManager) {
      _isDirtyManager = false;
      
      table.put(_idStore, _pathManager, _state);
    }
    */
  }

  public void checkpoint()
  {
    flushImpl();
  }

  public void shutdown(ShutdownModeAmp mode)
  {
  }
  
  private static class StateResourceManager implements Serializable {
    private long _counter;
    
    public long getCounter()
    {
      return _counter;
    }
    
    public long nextCounter()
    {
      return ++_counter;
    }
    
    public void setCounter(long counter)
    {
      _counter = counter;
    }
  }
}
