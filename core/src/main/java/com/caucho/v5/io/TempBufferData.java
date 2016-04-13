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

package com.caucho.v5.io;

import java.util.concurrent.atomic.AtomicInteger;

import com.caucho.v5.util.FreeRing;

/**
 * Pooled temporary byte buffer.
 */
public final class TempBufferData
{
  private final byte []_buffer;
  
  private final AtomicInteger _refCount = new AtomicInteger();
  private final FreeRing<TempBufferData> _freeList;
  
  private static final AtomicInteger _debugCount = new AtomicInteger();

  /**
   * Create a new TempBuffer.
   */
  TempBufferData(int size, FreeRing<TempBufferData> freeList)
  {
    if (_debugCount.incrementAndGet() > 1000) {
      Thread.dumpStack();
    }
    
    _buffer = new byte[size];
    _freeList = freeList;
  }
  
  public final byte []buffer()
  {
    return _buffer;
  }
  
  public final void allocate()
  {
    _refCount.incrementAndGet();
  }
  
  public final void free()
  {
    int count = _refCount.decrementAndGet();
    
    if (count == 0) {
      _freeList.free(this);
    }
    else if (count < 0) {
      Thread.dumpStack();
      throw new IllegalStateException(); 
    }
  }
}
