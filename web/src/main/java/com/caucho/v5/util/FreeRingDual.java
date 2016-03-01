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



/**
 * A free list with soft references.
 */
public final class FreeRingDual<T> {
  private final FreeRing<T> _freeRing;
  private final FreeRingSoft<T> _freeRingSoft;

  /**
   * Create a new free list.
   *
   * @param initialSize maximum number of free objects to store.
   */
  public FreeRingDual(int capacity, int softCapacity)
  {
    _freeRing = new FreeRing<T>(capacity);
    _freeRingSoft = new FreeRingSoft<T>(capacity);
  }

  public int getSize()
  {
    return _freeRing.getSize() + _freeRingSoft.getSize();
  }
  
  public long getHead()
  {
    return _freeRing.getHead();
  }
  
  public long getHeadAlloc()
  {
    return _freeRing.getHeadAlloc();
  }
  
  public long getTail()
  {
    return _freeRing.getTail();
  }
  
  public long getTailAlloc()
  {
    return _freeRing.getTailAlloc();
  }
  
  /**
   * Try to get an object from the free list.  Returns null if the free list
   * is empty.
   *
   * @return the new object or null.
   */
  public T allocate()
  {
    T value = _freeRing.allocate();
    
    if (value != null) {
      return value;
    }
    
    return _freeRingSoft.allocate();
  }
  
  
  /**
   * Frees the object.  If the free list is full, the object will be garbage
   * collected.
   *
   * @param obj the object to be freed.
   */
  public boolean free(T value)
  {
    if (_freeRing.free(value))
      return true;
    else
      return _freeRingSoft.free(value);
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
