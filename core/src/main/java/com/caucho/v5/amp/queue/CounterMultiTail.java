/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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

package com.caucho.v5.amp.queue;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

import com.caucho.v5.amp.outbox.WorkerOutbox;
import com.caucho.v5.amp.thread.WorkerAmp;

/**
 * Handles gateway/multi tail responses.
 */
public final class CounterMultiTail implements CounterRing
{
  private final AtomicLong _tailAlloc = new AtomicLong();
  private final AtomicLong _tail = new AtomicLong();
  
  private ArrayList<Long> _tailList = new ArrayList<>();
  
  CounterMultiTail()
  {
  }
  
  long allocate(long head)
  {
    AtomicLong tailAllocRef = _tailAlloc;
    long tail;
    
    do {
      tail = tailAllocRef.get();
      
      if (head <= tail) {
        return -1;
      }
    } while (! tailAllocRef.compareAndSet(tail, tail + 1));
    
    return tail;
  }
  
  void update(long tail, WorkerOutbox worker)
  {
    if (_tail.compareAndSet(tail, tail + 1)) {
      updateTail(tail + 1);
      
      worker.wake();
      return;
    }
    
    addTail(tail);
    
    if (tail <= _tail.get()) {
      updateTail(tail);
      
      worker.wake();
    }
  }
  
  private void updateTail(long tail)
  {
    for (; extract(tail); tail++) {
      if (! _tail.compareAndSet(tail, tail + 1)) {
        throw new IllegalStateException();
      }
    }
  }
  
  private void addTail(long tail)
  {
    synchronized (_tailList) {
      _tailList.add(tail);
    }
  }
  
  private boolean extract(long tail)
  {
    synchronized (_tailList) {
      for (int i = 0; i < _tailList.size(); i++) {
        long value = _tailList.get(i);
        
        if (value == tail) {
          _tailList.remove(i);
          
          return true;
        }
      }
    }
    
    return false;
  }

  @Override
  public long get()
  {
    return _tail.get();
  }

  @Override
  public void set(long value)
  {
    // XXX: update for init
    // throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public void setLazy(long value)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public boolean compareAndSet(long oldValue, long newValue)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public CounterRing getTail()
  {
    return this;
  }
}
