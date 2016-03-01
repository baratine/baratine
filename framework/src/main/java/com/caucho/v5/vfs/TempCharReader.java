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

import java.io.Reader;

/**
 * Char reader based on an underlying buffer.
 */
public class TempCharReader extends Reader {
  private TempCharBuffer _top;
  private TempCharBuffer _head;
  private char []_buffer;
  private int _offset;
  private int _length;
  private boolean _isFree;

  /**
   * Create a new TempCharReader.
   */
  public TempCharReader()
  {
  }

  /**
   * Create a new TempBuffer.
   */
  public TempCharReader(TempCharBuffer head)
  {
    init(head);
  }

  /**
   * Set the reader to free the buffer.
   */
  public void setFree(boolean isFree)
  {
    _isFree = isFree;
  }

  /**
   * Initialize the reader.
   */
  public void init(TempCharBuffer head)
  {
    _top = head;
    _head = head;

    if (head != null) {
      _buffer = head.buffer();
      _length = head.getLength();
    }
    else
      _length = 0;

    _offset = 0;
  }

  /**
   * Resets the reader
   */
  public void reset()
  {
    init(_top);
  }

  /**
   * Reads the next character.
   */
  public int read()
  {
    if (_length <= _offset) {
      if (_head == null)
        return -1;

      TempCharBuffer next = _head.getNext();
      if (_isFree)
        TempCharBuffer.free(_head);
      _head = next;

      if (_head == null)
        return -1;
      
      _buffer = _head.buffer();
      _length = _head.getLength();
      _offset = 0;
    }

    return _buffer[_offset++];
  }

  /**
   * Reads the next character.
   */
  public int read(char []buffer, int offset, int length)
  {
    int readLength = 0;
    
    while (length > 0) {
      if (_length <= _offset) {
        if (_head == null)
          return readLength == 0 ? -1 : readLength;

        TempCharBuffer next = _head.getNext();
        if (_isFree)
          TempCharBuffer.free(_head);
        _head = next;

        if (_head == null)
          return readLength == 0 ? -1 : readLength;
      
        _buffer = _head.buffer();
        _length = _head.getLength();
        _offset = 0;
      }

      int sublen = _length - _offset;

      if (length < sublen)
        sublen = length;

      System.arraycopy(_buffer, _offset, buffer, offset, sublen);

      _offset += sublen;
      offset += sublen;
      length -= sublen;
      readLength += sublen;
    }

    return readLength;
  }

  public void unread()
  {
    if (_offset > 0)
      _offset--;
  }

  /**
   * Returns true if it's empty.
   */
  public boolean isEmpty()
  {
    return _head == null;
  }

  /**
   * Closes the reader.
   */
  public void close()
  {
    if (_isFree)
      TempCharBuffer.freeAll(_head);

    _head = null;
    _buffer = null;
    _offset = 0;
    _length = 0;
  }
}
