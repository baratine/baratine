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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.io;

import java.io.InputStream;
import java.io.IOException;

public class TempInputStreamNoFree extends InputStream {
  private TempBuffer _head;

  private int _offset;

  public TempInputStreamNoFree(TempBuffer head)
  {
    _head = head;
  }

  @Override
  public int read()
    throws IOException
  {
    if (_head == null) {
      return -1;
    }

    int value = _head.buffer()[_offset++] & 0xff;

    if (_head.length() <= _offset) {
      TempBuffer next = _head.getNext();

      _head = next;
      _offset = 0;
    }

    return value;
  }

  @Override
  public int read(byte []buf, int offset, int length) throws IOException
  {
    if (_head == null) {
      return -1;
    }

    int sublen = _head.length() - _offset;

    if (length < sublen)
      sublen = length;

    System.arraycopy(_head.buffer(), _offset, buf, offset, sublen);

    if (_head.length() <= _offset + sublen) {
      TempBuffer next = _head.getNext();

      _head = next;
      _offset = 0;
    }
    else
      _offset += sublen;
    
    return sublen;
  }

  @Override
  public int available() throws IOException
  {
    if (_head != null) {
      return _head.length() - _offset;
    }
    else {
      return 0;
    }
  }

  @Override
  public void close()
    throws IOException
  {
    _head = null;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() +  "[]";
  }
}
