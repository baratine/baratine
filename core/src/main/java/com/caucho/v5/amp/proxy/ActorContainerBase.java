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

package com.caucho.v5.amp.proxy;

import io.baratine.service.Result;
import io.baratine.service.ServiceRef;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.actor.SaveResult;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.ActorContainerAmp;
import com.caucho.v5.util.LruCache;
import com.caucho.v5.util.LruCache.Entry;

/**
 * Baratine actor container for children.
 */
public class ActorContainerBase implements ActorContainerAmp
{
  private static final Logger log
    = Logger.getLogger(ActorContainerBase.class.getName());
  
  private static final int SAVE_MAX = 8 * 1024;
  private static final int MAX = 64 * 1024;
  
  private String _path;
  
  private LruCache<String,ServiceRef> _lruCache;
  
  private final ArrayList<ActorAmp> _modifiedList = new ArrayList<>();
  private final ArrayList<ActorAmp> _modifiedWorkList = new ArrayList<>();
  
  private boolean _isActive;
  private AtomicBoolean _isSaveRequested = new AtomicBoolean();
  
  public ActorContainerBase(String path)
  {
    _path = path;
    
    _lruCache = new LruCache<String,ServiceRef>(64);
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
    
    ArrayList<ActorAmp> children = new ArrayList<>(_modifiedList);
    //_modifiedList.clear();
    
    ServiceRefAmp serviceRef = (ServiceRefAmp) ServiceRef.current();
    
    for (ActorAmp actor : children) {
      actor.loadState().onActive(actor, serviceRef.inbox());
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
  public void addModifiedChild(ActorAmp actor)
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
  public boolean isModifiedChild(ActorAmp actor)
  {
    Objects.requireNonNull(actor);
    
    return _modifiedList.contains(actor);
  }
  
  @Override
  public void afterBatch(ActorAmp actor)
  {
    onSave(null);
  }
  
  protected boolean isSaveRequired()
  {
    return _modifiedList.size() > 0;
  }

  @Override
  public void onSave(SaveResult saveResult)
  {
    _isSaveRequested.compareAndSet(true, false);
    
    if (_modifiedList.size() == 0) {
      return;
    }

    _modifiedWorkList.clear();
    _modifiedWorkList.addAll(_modifiedList);
    _modifiedList.clear();
      
    for (ActorAmp actor : _modifiedWorkList) {
      if (saveResult != null) {
        actor.onSave(saveResult.addBean());
      }
      else {
        actor.onSave(Result.ignore());
      }
    }
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
