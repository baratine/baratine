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
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.util.FreeRing;

import io.baratine.io.Buffer;

/**
 * Pooled temporary byte buffer.
 */
public class TempBuffer implements java.io.Serializable, Buffer
{
  private static Logger _log;

  private static final boolean _isSmallmem;
  
  public static final int SMALL_SIZE;
  public static final int LARGE_SIZE;
  public static final int SIZE;
  
  private static boolean _isFreeException;
  
  private TempBuffer _next;
  private final byte []_buf;
  private int _tail;
  private int _head;
  private int _bufferCount;

  // validation of allocate/free
  private transient volatile boolean _isFree;
  private transient RuntimeException _freeException;

  /**
   * Create a new TempBuffer.
   */
  public TempBuffer(int size)
  {
    _buf = new byte[size];
  }

  /**
   * Returns true for a smallmem configuration
   */
  public static boolean isSmallmem()
  {
    return _isSmallmem;
  }

  /**
   * Allocate a TempBuffer, reusing one if available.
   */
  public static TempBuffer create()
  {
    return TempBufferStandard.create();
  }

  /**
   * Allocate a TempBuffer, reusing one if available.
   */
  public static TempBuffer createSmall()
  {
    return TempBufferSmall.create();
  }

  /**
   * Allocate a TempBuffer, reusing one if available.
   */
  public static TempBuffer createLarge()
  {
    return TempBufferLarge.create();
  }

  /**
   * Clears the buffer.
   */
  public final void clearAllocate()
  {
    if (! _isFree) { // XXX:
      throw new IllegalStateException();
    }

    _isFree = false;
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
  
  public void freeSelf()
  {
  }

  public static void free(TempBuffer tempBuffer)
  {
    tempBuffer.freeSelf();
  }
  
  /**
   * Frees a single buffer.
   */
  public static <X extends TempBuffer> 
  void free(FreeRing<X> freeList, X tempBuffer)
  {
    TempBuffer buf = tempBuffer;
    
    buf._next = null;
    
    if (buf._isFree) {
      _isFreeException = true;
      RuntimeException freeException = buf._freeException;
      RuntimeException secondException = new IllegalStateException("duplicate free");
      secondException.fillInStackTrace();
      
      log().log(Level.WARNING, "initial free location", freeException);
      log().log(Level.WARNING, "secondary free location", secondException);
      
      throw new IllegalStateException();
    }
    
    buf._isFree = true;

    if (_isFreeException) {
      buf._freeException = new IllegalStateException("initial free");
      buf._freeException.fillInStackTrace();
    }
      
    freeList.free(tempBuffer);
  }
  
  public void printFreeException()
  {
    if (_freeException != null) {
      _freeException.printStackTrace();
    }
  }

  public static void freeAll(TempBuffer buf)
  {
    while (buf != null) {
      TempBuffer next = buf._next;
      buf._next = null;
      
      buf.freeSelf();
      
      buf = next;
    }
  }
  
  /**
   * Called on OOM to free buffers.
   */
  public static void clearFreeLists()
  {
    TempBufferStandard.clearFreeList();
    TempBufferSmall.clearFreeList();
    TempBufferLarge.clearFreeList();
  }

  private static Logger log()
  {
    if (_log == null)
      _log = Logger.getLogger(TempBuffer.class.getName());

    return _log;
  }

  static {
    // the max size needs to be less than JNI code, currently max 16k
    // the min size is 8k because of the JSP spec
    int size = 8 * 1024;
    boolean isSmallmem = false;

    String smallmem = System.getProperty("caucho.smallmem");
    
    if (smallmem != null && ! "false".equals(smallmem)) {
      isSmallmem = true;
      size = 512;
    }

    _isSmallmem = isSmallmem;
    SIZE = size;
    LARGE_SIZE = 8 * 1024;
    SMALL_SIZE = 512;
  }
}
