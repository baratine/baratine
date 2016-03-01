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

import io.baratine.service.Cancel;
import io.baratine.service.Result;
import io.baratine.service.ResultStream;
import io.baratine.service.ServiceExceptionClosed;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.QueryRefAmp;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;

/**
 * Handles the context for an actor, primarily including its
 * query map.
 */
public final class QueryMap
{
  private static final Logger log = Logger.getLogger(QueryMap.class.getName());
  private static final L10N L = new L10N(QueryMap.class);
  
  private AtomicLong _sequence;
  
  private final int _capacity;
  private final int _mask;
  private final QueryBucket []_buckets;
  
  private final InboxAmp _inbox;
  
  public QueryMap(InboxAmp inbox)
  {
    _inbox = inbox;
    
    //_capacity = 64;
    _capacity = 256;
    _mask = _capacity - 1;
    
    _buckets = new QueryBucket[_capacity];
    
    for (int i = 0; i < _buckets.length; i++) {
      _buckets[i] = new QueryBucket();
    }
    
    long now = CurrentTime.getCurrentTime();
    long seed = (now / 1000) % (365 * 7 * 24 * 3600);
    
    // _head.set((CurrentTime.getCurrentTime() << 16) & 0x0fff_ffff_ffff_ffffL);
    _sequence = new AtomicLong(seed << 32);
  }
  
  public QueryRefAmp addQuery(String address, 
                              Result<?> result, 
                              ClassLoader loader)
  {
    String from = address; // _inbox.getAddress();
    long id = _sequence.incrementAndGet();
    // ClassLoader loader = _inbox.getManager().getClassLoader();
    
    QueryBucket bucket = getBucket(id);
    
    QueryRefBase queryRef = new QueryRefResult(from, id, loader, result);
    
    bucket.add(queryRef);
    
    return queryRef;
  }

  public QueryRefAmp addQuery(String address, 
                              ResultStream<?> result,
                              ServiceRefAmp targetRef,
                              ClassLoader loader)
  {
    String from = address; // _inbox.getAddress();
    long id = _sequence.incrementAndGet();
    
    // ClassLoader loader = _inbox.getManager().getClassLoader();
    
    QueryBucket bucket = getBucket(id);
    
    QueryRefBase queryRef = new QueryRefStream(from, id, loader, result, 
                                               targetRef);
    
    bucket.add(queryRef);
    
    return queryRef;
  }

  public QueryRefAmp addQuery(long id,
                              ResultStream<?> result,
                              Cancel cancel)
  {
    ClassLoader loader = _inbox.manager().classLoader();
    
    QueryBucket bucket = getBucket(id);
    
    QueryRefBase queryRef = new QueryRefStreamCall(id, result, cancel);
    
    bucket.add(queryRef);
    
    return queryRef;
  }
  
  private QueryBucket getBucket(long id)
  {
    int bucketId = (int) (id & _mask);
    
    return _buckets[bucketId];
  }
  
  public QueryRefAmp extractQuery(long id)
  {
    QueryBucket bucket = getBucket(id);
    
    QueryRefAmp queryRef = bucket.remove(id);
    
    if (queryRef == null) {
      log.finer("No matching query for qid=" + id + " " + _inbox);
    }
    
    return queryRef;
  }
  
  public QueryRefAmp getQuery(long id)
  {
    QueryBucket bucket = getBucket(id);
    
    QueryRefAmp queryRef = bucket.get(id);
    
    if (queryRef == null) {
      log.finer("No matching query for qid=" + id + " " + _inbox);
    }
    
    return queryRef;
  }

  public void close()
  {
    for (int i = 0; i < _capacity; i++) {
      QueryBucket bucket = _buckets[i];
      
      bucket.close();
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _inbox + "]";
  }
  
  private class QueryBucket {
    private QueryRefAmp []_values;
    private int _tail;
    
    QueryBucket()
    {
      _values = new QueryRefAmp[32];
    }
    
    synchronized void add(QueryRefAmp queryRef)
    {
      if (_values.length <= _tail) {
        QueryRefAmp []newValues = new QueryRefAmp[2 * _values.length];
        System.arraycopy(_values, 0, newValues, 0, _values.length);
        _values = newValues;
      }
      
      _values[_tail++] = queryRef;
    }
    
    synchronized QueryRefAmp remove(long id)
    {
      QueryRefAmp[] values = _values;
      int tail = _tail;
      
      for (int i = 0; i < tail; i++) {
        QueryRefAmp queryRef = values[i];
        
        if (queryRef.getId() == id) {
          System.arraycopy(values, i + 1, values, i, tail - i - 1);
          
          _tail = tail - 1;
          
          return queryRef;
        }
      }
      
      return null;
    }
    
    synchronized QueryRefAmp get(long id)
    {
      QueryRefAmp[] values = _values;
      int tail = _tail;
      
      for (int i = 0; i < tail; i++) {
        QueryRefAmp queryRef = values[i];
        
        if (queryRef.getId() == id) {
          return queryRef;
        }
      }
      
      return null;
    }
    
    void close()
    {
      QueryRefAmp item;
      
      HeadersAmp headers = HeadersNull.NULL;
      
      while ((item = popTail()) != null) {
        item.fail(headers, new ServiceExceptionClosed(L.l("{0} closed", _inbox)));
      }
    }
    
    private QueryRefAmp popTail()
    {
      synchronized (this) {
        if (_tail > 0) {
          QueryRefAmp queryRef = _values[--_tail];
          _values[_tail] = null;
          
          return queryRef;
        }
        else {
          return null;
        }
      }
    }
  }
}
