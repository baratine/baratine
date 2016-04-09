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

import java.util.concurrent.atomic.AtomicReferenceArray;

import com.caucho.v5.util.L10N;

final class ArrayRingAtomic<T> implements ArrayRing<T>
{
  private static final L10N L = new L10N(ArrayRingAtomic.class);
  
  private final AtomicReferenceArray<T> _ring;
  private final int _length;
  private final int _mask;
  
  ArrayRingAtomic(int length)
  {
    _length = length;
    
    if (Integer.bitCount(_length) != 1) {
      throw new IllegalArgumentException(L.l("Invalid ring capacity {0}",
                                             Long.toHexString(_length)));
    }
    
    _ring = new AtomicReferenceArray<>(_length);
    _mask = _length - 1;
  }

  @Override
  public final int getLength()
  {
    return _length;
  }
  
  @Override
  public final T get(long index)
  {
    return _ring.get(getIndex(index));
  }
  
  @Override
  public final void set(long ptr, T value)
  {
    int index = getIndex(ptr);
    
     _ring.set(index, value);
  }
  
  @Override
  public final void setLazy(long index, T value)
  {
    _ring.lazySet(getIndex(index), value);
  }
  
  @Override
  public final T takeAndClear(long ptr)
  {
    final AtomicReferenceArray<T> ring = _ring;
    final int index = getIndex(ptr);
    
    T value;

    while ((value = ring.getAndSet(index, null)) == null) {
    }
    
    return value;
  }
  
  @Override
  public final void clear(long start, long end)
  {
    final AtomicReferenceArray<T> ring = _ring;
    
    for (; start < end; start++) {
      ring.lazySet(getIndex(start), null);
    }
  }
  
  @Override
  public final T pollAndClear(long ptr)
  {
    final AtomicReferenceArray<T> ring = _ring;
    final int index = getIndex(ptr);
    
    T value = ring.get(index);
    
    if (value != null && ring.compareAndSet(index, value, null)) {
      return value;
    }
    else {
      return null;
    }
  }

  @Override
  public final int getIndex(long ptr)
  {
    return (int) (ptr & _mask);
  }
 
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + _ring.getClass().getSimpleName()
            + "," + getLength() + "]");
  }
}
