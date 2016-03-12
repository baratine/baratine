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

import com.caucho.v5.amp.queue.QueueRingFixed;


/**
 * FreeList provides a simple class to manage free objects.  This is useful
 * for large data structures that otherwise would gobble up huge GC time.
 *
 * <p>The free list is bounded.  Freeing an object when the list is full will
 * do nothing.
 */
public final class FreeRing<T>
{
  private final QueueRingFixed<T> _ringQueue;

  /**
   * Create a new free list.
   *
   * @param initialSize maximum number of free objects to store.
   */
  public FreeRing(int capacity)
  {
    _ringQueue = new QueueRingFixed<T>(capacity);
  }

  public int getSize()
  {
    return _ringQueue.size();
  }
  
  public long getHead()
  {
    return _ringQueue.head();
  }
  
  public long getHeadAlloc()
  {
    return _ringQueue.getHeadAlloc();
  }
  
  public long getTail()
  {
    return _ringQueue.getTail();
  }
  
  public long getTailAlloc()
  {
    return _ringQueue.getTail();
  }
  
  /**
   * Try to get an object from the free list.  Returns null if the free list
   * is empty.
   *
   * @return the new object or null.
   */
  public T allocate()
  {
    return _ringQueue.poll();
  }
  
  
  /**
   * Frees the object.  If the free list is full, the object will be garbage
   * collected.
   *
   * @param obj the object to be freed.
   */
  public boolean free(T value)
  {
    return _ringQueue.offer(value);
  }

  /**
   * Frees the object.  If the free list is full, the object will be garbage
   * collected.
   *
   * @param obj the object to be freed.
   */
  public boolean freeCareful(T value)
  {
    if (checkDuplicate(value)) {
      throw new IllegalStateException("tried to free object twice: " + value);
    }

    return free(value);
  }

  /**
   * Debugging to see if the object has already been freed.
   */
  public boolean checkDuplicate(T obj)
  {
    /*
    for (int i = 0; i < _size; i++) {
      if (_ring.get(i) == obj) {
        return true;
      }
    }
    */

    return false;
  }
}
