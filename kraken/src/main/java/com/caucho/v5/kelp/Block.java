/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
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

package com.caucho.v5.kelp;

import com.caucho.v5.io.TempBuffer;

/**
 * btree-based node block
 */
class Block
{
  static final int BLOCK_SIZE = 8192;
  
  static final int INSERT = 1;
  static final int REMOVE = 2;
  static final int REMOVE_TIMEOUT = 3;
  
  private final long _id;
  private TempBuffer _tBuf;
  private byte []_buffer;
  
  private int _head = 0;
  private int _index;
  
  Block(long id)
  {
    _id = id;
    
    _tBuf = TempBuffer.createLarge();
    _buffer = _tBuf.buffer();
    
    assert(BLOCK_SIZE == _buffer.length);
  }
  
  Block(long id, TempBuffer tBuf, int length)
  {
    _id = id;
    
    _tBuf = tBuf;
    _buffer = _tBuf.buffer();
    _index = length;
    
    assert(BLOCK_SIZE == _buffer.length);
  }
  
  final long getId()
  {
    return _id;
  }
  
  final int getIndex()
  {
    return _index;
  }
  
  protected final void setIndex(int index)
  {
    _index = index;
  }

  final byte[] getBuffer()
  {
    return _buffer;
  }
  
  final int getAvailable()
  {
    return _buffer.length - _index;
  }
  
  void close()
  {
    TempBuffer tBuf = TempBuffer.create();
    _tBuf = null;
    _buffer = null;
    
    if (tBuf != null) {
      tBuf.free();
    }
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + Long.toHexString(_id) + "]";
  }
}
