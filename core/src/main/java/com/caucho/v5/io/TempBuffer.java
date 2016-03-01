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

import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.util.FreeRing;

/**
 * Pooled temporary byte buffer.
 */
public class TempBuffer implements java.io.Serializable
{
  private static Logger _log;

  private static final boolean _isSmallmem;
  
  public static final int SMALL_SIZE;
  public static final int LARGE_SIZE;
  public static final int SIZE;
  
  private static boolean _isFreeException;
  
  private TempBuffer _next;
  private final byte []_buf;
  private int _offset;
  private int _length;
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
  public static TempBuffer allocate()
  {
    return TempBufferStandard.allocate();
  }

  /**
   * Allocate a TempBuffer, reusing one if available.
   */
  public static TempBuffer allocateSmall()
  {
    return TempBufferSmall.allocate();
  }

  /**
   * Allocate a TempBuffer, reusing one if available.
   */
  public static TempBuffer allocateLarge()
  {
    return TempBufferLarge.allocate();
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

    _offset = 0;
    _length = 0;
    _bufferCount = 0;
    
  }

  /**
   * Clears the buffer.
   */
  public final void clear()
  {
    _next = null;

    _offset = 0;
    _length = 0;
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
    return _length;
  }

  /**
   * Sets the number of bytes used in the buffer.
   */
  public final void length(int length)
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

  public final TempBuffer getNext()
  {
    return _next;
  }

  public final void setNext(TempBuffer next)
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

  public int write(byte []buf, int offset, int length)
  {
    byte []thisBuf = _buf;
    int thisLength = _length;

    if (thisBuf.length - thisLength < length)
      length = thisBuf.length - thisLength;

    System.arraycopy(buf, offset, thisBuf, thisLength, length);

    _length = thisLength + length;

    return length;
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
