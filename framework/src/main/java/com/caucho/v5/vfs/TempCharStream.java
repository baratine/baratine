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

package com.caucho.v5.vfs;

import java.io.IOException;

import com.caucho.v5.io.StreamImpl;

public class TempCharStream extends StreamImpl {
  private TempCharBuffer _head;
  private TempCharBuffer _tail;
  private char []_cBuf = new char[256];

  public TempCharStream()
  {
  }

  /**
   * Initializes the temp stream for writing.
   */
  public void openWrite()
  {
    TempCharBuffer ptr = _head;

    _head = null;
    _tail = null;

    TempCharBuffer.freeAll(ptr);
  }

  /**
   * Returns the tail buffer.
   */
  public char []getTail()
  {
    return _tail.buffer();
  }

  /**
   * Returns the head.
   */
  public TempCharBuffer getHead()
  {
    return _head;
  }
  
  public String readString()
  {
    StringBuilder sb = new StringBuilder();
    
    TempCharBuffer head = _head;
    
    for (TempCharBuffer buf = head; buf != null; buf = buf.getNext()) {
      sb.append(buf.buffer(), 0, buf.getLength());
    }
    
    _head = null;
    _tail = null;
    
    TempCharBuffer.freeAll(head);
    
    return sb.toString();
  }

  /**
   * Returns true since the temp stream can write.
   */
  public boolean canWrite()
  {
    return true;
  }

  public void write(char []buf, int offset, int length)
    throws IOException
  {
    int index = 0;
    while (index < length) {
      if (_tail == null) {
        addBuffer(TempCharBuffer.allocate());
      }
      else if (_tail._buf.length <= _tail._length) {
        addBuffer(TempCharBuffer.allocate());

        // XXX: see TempStream for backing files
      }

      int sublen = _tail._buf.length - _tail._length;
      sublen = Math.min(length - index, sublen);

      System.arraycopy(buf, index + offset, _tail._buf, _tail._length, sublen);

      index += sublen;
      _tail._length += sublen;
    }
  }

  @Override
  public void write(byte []buf, int offset, int length, boolean isEnd)
    throws IOException
  {
    char []cBuf = _cBuf;
    int cBufLength = cBuf.length;
    
    while (length > 0) {
      int sublen = Math.min(length, cBufLength);
      
      for (int i = 0; i < sublen; i++) {
        cBuf[i] = (char) buf[offset + i];
      }
      
      write(cBuf, 0, sublen);
      
      offset += sublen;
      length -= sublen;
    }
  }

  /**
   * Writes part of a string.
   */
  public void write(String s, int offset, int length)
    throws IOException
  {
    while (length > 0) {
      if (_tail == null)
        addBuffer(TempCharBuffer.allocate());
      else if (_tail._buf.length <= _tail._length) {
        addBuffer(TempCharBuffer.allocate());

        // XXX: see TempStream for backing files
      }

      int sublen = _tail._buf.length - _tail._length;
      if (length < sublen)
        sublen = length;

      s.getChars(offset, offset + sublen, _tail._buf, _tail._length);

      offset += sublen;
      length -= sublen;
      _tail._length += sublen;
    }
  }

  public void write(int ch)
    throws IOException
  {
    if (_tail == null) {
      addBuffer(TempCharBuffer.allocate());
    }
    else if (_tail._buf.length <= _tail._length) {
      addBuffer(TempCharBuffer.allocate());

      // XXX: see TempStream for backing files
    }

    _tail._buf[_tail._length++] = (char) ch;
  }

  private void addBuffer(TempCharBuffer buf)
  {
    buf._next = null;
    if (_tail != null) {
      _tail._next = buf;
      _tail = buf;
    } else {
      _tail = buf;
      _head = buf;
    }

    _head._bufferCount++;
  }

  public void flush()
    throws IOException
  {
  }

  public void close()
    throws IOException
  {
    super.close();
  }

  /**
   * Returns the total length of the buffer's bytes
   */
  public int getLength()
  {
    int length = 0;
    
    for (TempCharBuffer ptr = _head; ptr != null; ptr = ptr._next) {
      length += ptr.getLength();
    }

    return length;
  }

  public void clearWrite()
  {
    TempCharBuffer ptr = _head;

    _head = null;
    _tail = null;

    TempCharBuffer.freeAll(ptr);
  }

  public void discard()
  {
    _head = null;
    _tail = null;
  }

  /**
   * Clean up the temp stream.
   */
  public void destroy()
  {
    try {
      close();
    } catch (IOException e) {
    }

    TempCharBuffer ptr = _head;
    
    _head = null;
    _tail = null;

    TempCharBuffer.freeAll(ptr);
  }
}
