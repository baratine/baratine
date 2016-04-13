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
import java.util.logging.Level;
import java.util.logging.Logger;

import io.baratine.io.Bytes;

/**
 * Data buffer
 */
class BytesImpl implements Bytes
{
  private static final Logger log
    = Logger.getLogger(BytesImpl.class.getName());
  
  private byte []_data;
  
  private int _head;
  private int _tail;
  
  BytesImpl(byte []buffer)
  {
    _data = new byte[buffer.length];
    
    System.arraycopy(buffer, 0, _data, 0, buffer.length);

    _head = buffer.length;
  }
  
  BytesImpl()
  {
    _data = new byte[256];

    _tail = 0;
  }
  
  /**
   * Returns the current size of the buffer.
   */
  @Override
  public int length()
  {
    return _head - _tail;
  }

  
  /**
   * adds bytes from the buffer
   */
  @Override
  public BytesImpl set(int pos, byte []buffer, int offset, int length)
  {
    System.arraycopy(buffer, offset, _data, pos, length);
    
    return this;
  }
    
  /**
   * adds bytes from the buffer
   */
  @Override
  public BytesImpl write(byte []buffer, int offset, int length)
  {
    while (length > 0) {
      int sublen = Math.min(_data.length - _tail, length);
      
      System.arraycopy(buffer, offset, _data, _tail, sublen);
      
      if (sublen <= 0) {
        throw new UnsupportedOperationException();
      }
      
      length -= sublen;
      offset += sublen;
      _head += sublen;
    }
    
    return this;
  }
  
  @Override
  public BytesImpl write(InputStream is)
  {
    try {
      while (true) {
        int sublen = _data.length - _tail;
        
        if (sublen == 0) {
          throw new UnsupportedOperationException();
        }

        sublen = is.read(_data, _tail, sublen);
        
        if (sublen < 0) {
          return this;
        }
        
        _head += sublen;
      }
    } catch (IOException e) {
      log.log(Level.WARNING, e.toString(), e);
    }
    
    return this;
  }
  
  /**
   * gets bytes from the buffer
   */
  @Override
  public BytesImpl get(int pos, byte []buffer, int offset, int length)
  {
    System.arraycopy(_data, pos, buffer, offset, length);
    
    return this;
  }

  @Override
  public String toString()
  {
    try {
      return new String(_data, 0, _tail, "utf-8");
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
      
      throw new RuntimeException(e);
    }
  }

  @Override
  public int read(byte[] buffer, int offset, int length)
  {
    // TODO Auto-generated method stub
    return 0;
  }
}
