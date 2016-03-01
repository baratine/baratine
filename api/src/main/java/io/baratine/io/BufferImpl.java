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

package io.baratine.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data buffer
 */
class BufferImpl implements Buffer
{
  private static final Logger log
    = Logger.getLogger(BufferImpl.class.getName());
  
  private byte []_data;
  private int _length;
  
  BufferImpl(byte []buffer)
  {
    _data = new byte[buffer.length];
    
    System.arraycopy(buffer, 0, _data, 0, buffer.length);

    _length = buffer.length;
  }
  
  BufferImpl()
  {
    _data = new byte[256];

    _length = 0;
  }
  
  /**
   * Returns the current size of the buffer.
   */
  @Override
  public int length()
  {
    return _length;
  }
  
  /**
   * adds bytes from the buffer
   */
  @Override
  public BufferImpl addBytes(byte []buffer, int offset, int length)
  {
    while (length > 0) {
      int sublen = Math.min(_data.length - _length, length);
      
      System.arraycopy(buffer, offset, _data, _length, sublen);
      
      if (sublen <= 0) {
        throw new UnsupportedOperationException();
      }
      
      length -= sublen;
      offset += sublen;
      _length += sublen;
    }
    
    return this;
  }
  
  @Override
  public BufferImpl addBytes(InputStream is)
  {
    try {
      while (true) {
        int sublen = _data.length - _length;
        
        if (sublen == 0) {
          throw new UnsupportedOperationException();
        }

        sublen = is.read(_data, _length, sublen);
        
        if (sublen < 0) {
          return this;
        }
        
        _length += sublen;
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
  public BufferImpl getBytes(int pos, byte []buffer, int offset, int length)
  {
    System.arraycopy(_data, pos, buffer, offset, length);
    
    return this;
  }

  @Override
  public String toString()
  {
    try {
      return new String(_data, 0, _length, "utf-8");
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
      
      throw new RuntimeException(e);
    }
  }
}
