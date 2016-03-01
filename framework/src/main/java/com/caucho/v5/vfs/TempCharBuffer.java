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

package com.caucho.v5.vfs;

import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.util.FreeList;

public class TempCharBuffer {
  private static FreeList<TempCharBuffer> _freeList =
    new FreeList<TempCharBuffer>(32);
  
  public static final int SIZE = TempBuffer.SIZE;

  TempCharBuffer _next;
  final char []_buf;
  private int _offset;
  int _length;
  int _bufferCount;

  /**
   * Create a new TempBuffer.
   */
  public TempCharBuffer(int size)
  {
    _buf = new char[size];
  }

  /**
   * Allocate a TempCharBuffer, reusing one if available.
   */
  public static TempCharBuffer allocate()
  {
    TempCharBuffer next = _freeList.allocate();

    if (next == null)
      return new TempCharBuffer(SIZE);

    next._next = null;

    next._offset = 0;
    next._length = 0;
    next._bufferCount = 0;

    return next;
  }

  /**
   * Clears the buffer.
   */
  public void clear()
  {
    _next = null;

    _offset = 0;
    _length = 0;
    _bufferCount = 0;
  }

  /**
   * Returns the buffer's underlying char array.
   */
  public final char []buffer()
  {
    return _buf;
  }

  /**
   * Returns the number of chars in the buffer.
   */
  public final int getLength()
  {
    return _length;
  }

  public final void setLength(int length)
  {
    _length = length;
  }

  public final int getCapacity()
  {
    return _buf.length;
  }

  public int getAvailable()
  {
    return _buf.length - _length;
  }

  public final TempCharBuffer getNext()
  {
    return _next;
  }

  public final void setNext(TempCharBuffer next)
  {
    _next = next;
  }

  public int write(char []buf, int offset, int length)
  {
    char []thisBuf = _buf;
    int thisLength = _length;
    
    if (thisBuf.length - thisLength < length)
      length = thisBuf.length - thisLength;

    System.arraycopy(buf, offset, thisBuf, thisLength, length);

    _length = thisLength + length;

    return length;
  }

  public void freeSelf()
  {
    free(this);
  }

  /**
   * Frees a single buffer.
   */
  public static void free(TempCharBuffer buf)
  {
    buf._next = null;

    if (buf._buf.length == SIZE)
      _freeList.free(buf);
  }

  public static void freeAll(TempCharBuffer buf)
  {
    while (buf != null) {
      TempCharBuffer next = buf._next;
      buf._next = null;
      if (buf._buf.length == SIZE)
        _freeList.free(buf);
      buf = next;
    }
  }
}
