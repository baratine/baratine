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

package com.caucho.v5.amp.message;

import io.baratine.service.Result;
import io.baratine.service.ServiceExceptionClosed;
import io.baratine.service.ServiceRef;

import java.util.concurrent.atomic.AtomicLong;

import com.caucho.v5.amp.spi.MethodAmp;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.util.Alarm;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;

/**
 * Handles the context for an actor, primarily including its
 * query map.
 */
public final class DebugQueryMap
{
  private static final L10N L = new L10N(DebugQueryMap.class);
  
  private final AtomicLong _head = new AtomicLong(1);
  
  private final int _capacity;
  private final DebugResult<?> []_queryBuckets;
  private final Object []_queryLocks;
  
  private final Lifecycle _lifecycle = new Lifecycle();
  private final Alarm _alarm;
  private final long _timeout;
  
  public DebugQueryMap(int buckets,
                       long timeout)
  {
    if (timeout <= 0) {
      timeout = Integer.MAX_VALUE; 
    }
    
    //_capacity = 64;
    _capacity = buckets;
    _timeout = timeout;
    
    _queryBuckets = new DebugResult<?>[buckets];
    _queryLocks = new Object[buckets];
    
    for (int i = 0; i < buckets; i++) {
      _queryLocks[i] = new Object();
    }
    
    _lifecycle.toActive();
    _alarm = new Alarm(alarm->handleAlarm(alarm));
    _alarm.runAfter(_timeout);
  }
  
  public <V> Result<V> addQuery(Result<V> result,
                                ServiceRef serviceRef,
                                MethodAmp method)
  {
    long id = _head.incrementAndGet();
    
    DebugResult<V> item
      = new DebugResult<>(result, serviceRef, method, this, id);
    
    int bucket = getBucket(id);
    
    synchronized (_queryLocks[bucket]) {
      DebugResult<?> prevHead;
      
      prevHead = _queryBuckets[bucket];
      
      item.setNext(prevHead);
      
      if (prevHead != null) {
        prevHead.setPrev(item);
      }
      
      _queryBuckets[bucket] = item;
    }
    
    return item;
  }
  
  private int getBucket(long id)
  {
    return (int) (id & (_capacity - 1));
  }
  
  public void removeQuery(DebugResult<?> ptr)
  {
    int bucket = getBucket(ptr.getId());
    
    synchronized (_queryLocks[bucket]) {
      DebugResult<?> prev = ptr.getPrev();
      DebugResult<?> next = ptr.getNext();
      
      if (prev != null) {
        prev.setNext(next);
      }
      else {
        _queryBuckets[bucket] = next;
      }
    
      if (next != null) {
        next.setPrev(prev);
      }
    }
  }
  
  private void handleAlarm(Alarm alarm)
  {
    try {
      timeout();
    } finally {
      if (_lifecycle.isActive()) {
        alarm.runAfter(_timeout);
      }
    }
  }
  
  private void timeout()
  {
    long now = CurrentTime.getCurrentTime();
    
    for (int i = 0; i < _capacity; i++) {
      synchronized (_queryLocks[i]) {
        DebugResult<?> item = _queryBuckets[i];
        DebugResult<?> next;
      
        while (item != null) {
          next = item.getNext();
          
          item.timeout(now);
          
          item = next;
        }
      }
    }
  }

  public void close()
  {
    _lifecycle.toDestroy();
    _alarm.dequeue();
    
    for (int i = 0; i < _capacity; i++) {
      synchronized (_queryLocks[i]) {
        DebugResult<?> item = _queryBuckets[i];
        
        for (; item != null; item = item.getNext()) {
          item.fail(new ServiceExceptionClosed(L.l("manager closed for query: " + item)));
        }
        
        _queryBuckets[i] = null;
      }
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
