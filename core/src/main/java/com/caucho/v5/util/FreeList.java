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

package com.caucho.v5.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * FreeList provides a simple class to manage free objects.  This is useful
 * for large data structures that otherwise would gobble up huge GC time.
 *
 * <p>The free list is bounded.  Freeing an object when the list is full will
 * do nothing.
 */
public final class FreeList<T> {
  private final int _capacity;
  private final AtomicReferenceArray<T> _freeStack;
  private final AtomicInteger _top = new AtomicInteger();

  /**
   * Create a new free list.
   *
   * @param initialSize maximum number of free objects to store.
   */
  public FreeList(int capacity)
  {
    _capacity = capacity;
    _freeStack = new AtomicReferenceArray<T>(capacity);
  }
  
  public int size()
  {
    return _top.get();
  }
  
  /**
   * Try to get an object from the free list.  Returns null if the free list
   * is empty.
   *
   * @return the new object or null.
   */
  public final T allocate()
  {
    AtomicInteger topRef = _top;
    
    while (true) {
      final int top = topRef.get();

      if (top <= 0) {
        return null;
      }
      else if (topRef.compareAndSet(top, top - 1)) {
        T value = _freeStack.getAndSet(top - 1, null);
        
        if (value != null) {
          return value;
        }
      }
    }
  }
  
  /**
   * Frees the object.  If the free list is full, the object will be garbage
   * collected.
   *
   * @param obj the object to be freed.
   */
  public boolean free(T obj)
  {
    AtomicInteger topRef = _top;

    while (true) {
      final int top = topRef.get();
      
      if (_capacity <= top) {
        return false;
      }

      boolean isFree = _freeStack.compareAndSet(top, null, obj);
      
      topRef.compareAndSet(top, top + 1);

      if (isFree) {
        return true;
      }
    }
  }

  /**
   * Frees the object.  If the free list is full, the object will be garbage
   * collected.
   *
   * @param obj the object to be freed.
   */
  public boolean freeCareful(T obj)
  {
    if (checkDuplicate(obj))
      throw new IllegalStateException("tried to free object twice: " + obj);

    return free(obj);
  }

  /**
   * Debugging to see if the object has already been freed.
   */
  public boolean checkDuplicate(T obj)
  {
    final int top = _top.get();

    for (int i = top - 1; i >= 0; i--) {
      if (_freeStack.get(i) == obj)
        return true;
    }

    return false;
  }
}
