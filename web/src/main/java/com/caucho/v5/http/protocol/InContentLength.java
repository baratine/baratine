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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.http.protocol;

import java.io.IOException;
import java.net.SocketTimeoutException;

import com.caucho.v5.io.ClientDisconnectException;
import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.StreamImpl;

/**
 * Filter so POST readers can only read data up to the content length
 */
public class InContentLength extends StreamImpl
{
  // the underlying stream
  private ReadStream _next;

  // bytes available in the post contents
  private long _length;

  void init(ReadStream next, long length)
  {
    _next = next;
    _length = length;
  }

  public boolean canRead()
  {
    return true;
  }

  @Override
  public int getAvailable()
    throws IOException
  {
    if (_length <= 0) {
      return -1;
    }
    
    int available = _next.available();

    return (int) Math.min(_length, available);
  }
  
  @Override
  public boolean isReady()
  {
    if (_length <= 0) {
      return false;
    }
    
    return _next.isReady();
  }

  /**
   * Reads from the buffer, limiting to the content length.
   *
   * @param buffer the buffer containing the results.
   * @param offset the offset into the result buffer
   * @param length the length of the buffer.
   *
   * @return the number of bytes read or -1 for the end of the file.
   */
  @Override
  public int read(byte []buffer, int offset, int length)
    throws IOException
  {
    try {
    if (_length < length)
      length = (int) _length;

    if (length <= 0)
      return -1;

    int len = _next.read(buffer, offset, length);
      
    if (len > 0) {
      _length -= len;
    }
    else {
      _length = -1;
    }

    return len;
    } catch (SocketTimeoutException e) {
      throw new ClientDisconnectException(e);
    }
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _next + "]";
  }
}
