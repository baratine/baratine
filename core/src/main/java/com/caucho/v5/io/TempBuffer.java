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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import io.baratine.io.Buffer;

/**
 * Pooled temporary byte buffer.
 */
public final class TempBuffer implements java.io.Serializable, Buffer
{
  public static final int SIZE = TempBuffers.STANDARD_SIZE;
  public static final int SMALL_SIZE = TempBuffers.SMALL_SIZE;
  
  private TempBufferData _data;
  
  private TempBuffer _next;
  private final byte []_buf;
  private int _tail;
  private int _head;
  private int _bufferCount;

  /*
  // validation of allocate/free
  private transient volatile boolean _isFree;
  private transient RuntimeException _freeException;
  */

  /**
   * Create a new TempBuffer.
   */
  public TempBuffer(TempBufferData data)
  {
    _data = data;
    _buf = data.buffer();
    
    data.allocate();
  }
  
  public static boolean isSmallmem()
  {
    return TempBuffers.isSmallmem();
  }

  /**
   * Allocate a TempBuffer, reusing one if available.
   */
  public static TempBuffer create()
  {
    return new TempBuffer(TempBuffers.create());
  }

  /**
   * Allocate a TempBuffer, reusing one if available.
   */
  public static TempBuffer createSmall()
  {
    return new TempBuffer(TempBuffers.createSmall());
  }

  /**
   * Allocate a TempBuffer, reusing one if available.
   */
  public static TempBuffer createLarge()
  {
    return new TempBuffer(TempBuffers.createLarge());
  }

  /**
   * Clears the buffer.
   */
  public final void clearAllocate()
  {
    _next = null;

    _tail = 0;
    _head = 0;
    _bufferCount = 0;
    
  }

  /**
   * Clears the buffer.
   */
  public final void clear()
  {
    _next = null;

    _tail = 0;
    _head = 0;
    _bufferCount = 0;
  }

  /**
   * Returns the buffer's underlying byte array.
   */
  public final byte []buffer()
  {
    return _buf;
  }

  /**
   * Returns the number of bytes in the buffer.
   */
  public final int length()
  {
    return _head - _tail;
  }

  /**
   * Sets the number of bytes used in the buffer.
   */
  public final void length(int length)
  {
    _head = length;
  }

  public final int capacity()
  {
    return _buf.length;
  }

  public int available()
  {
    return _buf.length - _head;
  }

  public final TempBuffer next()
  {
    return _next;
  }

  public final void next(TempBuffer next)
  {
    _next = next;
  }
  
  public final int getBufferCount()
  {
    return _bufferCount;
  }
  
  public final void setBufferCount(int count)
  {
    _bufferCount = count;
  }
  
  public Buffer write(byte[] buffer)
  {
    return write(buffer, 0, buffer.length);
  }

  @Override
  public Buffer write(byte[] buffer, int offset, int length)
  {
    byte []thisBuf = _buf;
    int thisLength = _head;

    /*
    if (thisBuf.length - thisLength < length) {
      throw new IllegalArgumentException();
    }
    */

    System.arraycopy(buffer, offset, thisBuf, thisLength, length);

    _head = thisLength + length;

    return this;
  }

  @Override
  public Buffer set(int pos, byte[] buffer, int offset, int length)
  {
    System.arraycopy(buffer, offset, _buf, pos, length);
    
    return this;
  }

  @Override
  public Buffer write(InputStream is)
    throws IOException
  {
    while (true) {
      int length = _head;
      int sublen = _buf.length - length;
    
      if (sublen <= 0) {
        throw new IllegalStateException();
      }
    
      sublen = is.read(_buf, length, sublen);
    
      if (sublen < 0) {
        return this;
      }
    
      _head = length + sublen;
    }
  }

  @Override
  public Buffer get(int pos, byte[] buffer, int offset, int length)
  {
    if (length < _head - pos) {
      throw new IllegalArgumentException();
    }
    
    System.arraycopy(_buf, pos, buffer, offset, length);
    
    return this;
  }

  @Override
  public int read(byte[] buffer, int offset, int length)
  {
    int tail = _tail;
    
    int sublen = Math.min(_head - tail, length);
    
    System.arraycopy(_buf, tail, buffer, offset, sublen);
    
    _tail += sublen;
    
    return sublen > 0 ? sublen : -1;

  }

  @Override
  public void read(ByteBuffer buffer)
  {
    int tail = _tail;
    
    int sublen = Math.min(_head - tail, buffer.remaining());

    buffer.put(_buf, _tail, sublen);
    
    _tail = tail + sublen;
  }

  @Override
  public void read(OutputStream os)
    throws IOException
  {
    int tail = _tail;
    
    os.write(_buf, tail, _head - tail);
    
    _tail = _head;
  }
  
  @Override
  public void free()
  {
    TempBufferData data = _data;
    _data = null;
    
    if (data != null) {
      data.free();
    }
  }

  public static void free(TempBuffer tempBuffer)
  {
    tempBuffer.free();
  }

  public static void freeAll(TempBuffer buffer)
  {
    for (; buffer != null; buffer = buffer.next()) {
      buffer.free();
    }
  }
}
