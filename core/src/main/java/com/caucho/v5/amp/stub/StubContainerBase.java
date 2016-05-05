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

package com.caucho.v5.amp.stub;

import io.baratine.service.Result;
import io.baratine.service.ServiceRef;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.StubContainerAmp;
import com.caucho.v5.util.LruCache;
import com.caucho.v5.util.LruCache.Entry;

/**
 * Baratine stub container for children.
 */
public class StubContainerBase implements StubContainerAmp
{
  private static final Logger log
    = Logger.getLogger(StubContainerBase.class.getName());
  
  private static final int SAVE_MAX = 8 * 1024;
  private static final int MAX = 64 * 1024;
  
  private String _path;
  
  private LruCache<String,ServiceRef> _lruCache;
  
  private final ArrayList<StubAmp> _modifiedList = new ArrayList<>();
  private final ArrayList<StubAmp> _modifiedWorkList = new ArrayList<>();
  
  private boolean _isActive;
  private AtomicBoolean _isSaveRequested = new AtomicBoolean();
  
  public StubContainerBase(String path)
  {
    _path = path;
    
    // XXX: needs to be larger and configurable
    _lruCache = new LruCache<>(64);
  }
  
  @Override
  public boolean isJournalReplay()
  {
    return false;
  }

  @Override
  public String getChildPath(String path)
  {
    if (_path != null) {
      return path.substring(_path.length());
    }
    else {
      return path;
    }
  }
  
  @Override
  public void onActive()
  {
    _isActive = true;
    
    ArrayList<StubAmp> children = new ArrayList<>(_modifiedList);
    //_modifiedList.clear();
    
    ServiceRefAmp serviceRef = (ServiceRefAmp) ServiceRef.current();
    
    for (StubAmp actor : children) {
      actor.state().onActive(actor, serviceRef.inbox());
    }
  }
  
  @Override
  public ServiceRef addService(String path, ServiceRef serviceRef)
  {
    synchronized (this) {
      LruCache<String,ServiceRef> lruCache = getLruCache();
      
      return lruCache.putIfNew(path, serviceRef);
    }
  }
  
  private LruCache<String,ServiceRef> getLruCache()
  {
    LruCache<String, ServiceRef> lruCache = _lruCache;
    
    if (lruCache.getCapacity() < MAX && _lruCache.size() >= 32) {
      LruCache<String, ServiceRef> lruCacheNew = new LruCache<>(MAX);
      
      Iterator<Entry<String, ServiceRef>> iter = lruCache.iterator();
      while (iter.hasNext()) {
        Entry<String,ServiceRef> entry = iter.next();
        
        lruCacheNew.put(entry.getKey(), entry.getValue());
      }

      lruCache = _lruCache = lruCacheNew;
    }
    
    return lruCache;
  }

  @Override
  public ServiceRef getService(String path)
  {
    synchronized (_lruCache) {
      return _lruCache.get(path);
    }
  }
  
  @Override
  public void addModifiedChild(StubAmp actor)
  {
    Objects.requireNonNull(actor);
    
    _modifiedList.add(actor);
    
    if (_isActive
        && _modifiedList.size() > SAVE_MAX
        && _isSaveRequested.compareAndSet(false, true)) {
      ServiceRef serviceRef = ServiceRef.current();
      
      serviceRef.save(Result.ignore());
    }
  }
  
  @Override
  public boolean isModifiedChild(StubAmp stub)
  {
    Objects.requireNonNull(stub);
    
    return _modifiedList.contains(stub);
  }
  
  @Override
  public void afterBatch(StubAmp stub)
  {
    onSave(Result.ignore());
  }
  
  protected boolean isSaveRequired()
  {
    return _modifiedList.size() > 0;
  }

  @Override
  public void onSave(Result<Void> result)
  {
    _isSaveRequested.compareAndSet(true, false);

    if (_modifiedList.size() == 0) {
      result.ok(null);
      return;
    }

    _modifiedWorkList.clear();
    _modifiedWorkList.addAll(_modifiedList);
    _modifiedList.clear();
    
    Result.Fork<Void,Void> fork = result.fork();
      
    for (StubAmp stub : _modifiedWorkList) {
      stub.onSaveChild(fork.branch());
    }
    
    fork.join(x->null);
  }

  @Override
  public void onLruModified(ServiceRefAmp serviceRef)
  {
    if (_isSaveRequested.compareAndSet(false, true)) {
      ServiceRefAmp parentRef = serviceRef.inbox().serviceRef();
      
      parentRef.save(Result.ignore());
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
