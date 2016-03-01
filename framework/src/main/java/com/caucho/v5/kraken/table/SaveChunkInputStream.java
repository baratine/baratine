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

package com.caucho.v5.kraken.table;

import java.io.IOException;
import java.io.InputStream;

import com.caucho.v5.io.TempBuffer;

/**
 * Input stream for a row.
 */
public class SaveChunkInputStream extends InputStream
{
  private InputStream _is;
  
  private TempBuffer _tBuf;
  
  private int _offset;
  private int _length;
  
  public SaveChunkInputStream()
  {
  }
  
  public void init(InputStream is)
  {
    _is = is;
    _tBuf = TempBuffer.allocate();
  }
  
  @Override
  public int read()
    throws IOException
  {
    if (! fillBuffer()) {
      return -1;
    }

    byte []buffer = _tBuf.buffer();
    
    return buffer[_offset++] & 0xff;
  }
  
  @Override
  public int read(byte []buffer, int offset, int length)
    throws IOException
  {
    if (! fillBuffer()) {
      return -1;
    }

    byte []tBuffer = _tBuf.buffer();
    
    int sublen = Math.min(_length - _offset, length);
    
    System.arraycopy(tBuffer, _offset, buffer, offset, sublen);
    
    _offset += sublen;
    
    return sublen;
  }
  
  private boolean fillBuffer()
    throws IOException
  {
    if (_offset < _length) {
      return true;
    }
    
    TempBuffer tBuf = _tBuf;
    
    if (tBuf == null) {
      return false;
    }
    
    if (_is == null) {
      _tBuf = null;
      
      tBuf.freeSelf();
      
      return false;
    }
    
    byte []tBuffer = tBuf.buffer();
    
    int sublen = _is.read(tBuffer, 2, tBuffer.length - 2);
    
    if (sublen < 0) {
      _is = null;
      tBuffer[0] = 0;
      // tBuffer[1] = 0;
      
      _offset = 0;
      _length = 1;
      
      return true;
    }
    else if (sublen == 0) {
      throw new IllegalStateException();
    }
    else if (sublen <= 0x7f) {
      tBuffer[0] = (byte) (sublen >> 8);
      tBuffer[1] = (byte) sublen;
      
      _offset = 1;
      _length = sublen + 2;
    }
    else {
      tBuffer[0] = (byte) ((sublen >> 8) | 0x80);
      tBuffer[1] = (byte) sublen;
      
      _offset = 0;
      _length = sublen + 2;
    }
    
    return true;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
