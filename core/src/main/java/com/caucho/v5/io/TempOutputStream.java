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

import com.caucho.v5.util.Hex;

public class TempOutputStream extends OutputStream
{
  private TempBuffer _head;
  private TempBuffer _tail;
  
  public TempOutputStream()
  {
  }
  
  public TempOutputStream(TempBuffer head)
  {
    _head = head;
    
    TempBuffer tail = null;
    
    for (; head != null; head = head.getNext()) {
      tail = head;
    }
    
    _tail = tail;
    
  }

  public byte []getTail()
  {
    return _tail.buffer();
  }

  @Override
  public void write(int ch)
  {
    if (_tail == null) {
      addBuffer(TempBuffer.allocate());
    }
    else if (_tail.buffer().length <= _tail.length()) {
      addBuffer(TempBuffer.allocate());
    }
    
    int tailLength = _tail.length();

    _tail.buffer()[tailLength] = (byte) ch;
    
    _tail.length(tailLength + 1);
  }

  @Override
  public void write(byte []buf, int offset, int length)
  {
    int index = 0;

    TempBuffer tail = _tail;
    int bufferSize = TempBuffer.SIZE;
    int tailLength;

    if (tail != null) {
      tailLength = tail.length();
    }
    else {
      tailLength = 0;
    }

    while (index < length) {
      if (tail == null) {
        addBuffer(TempBuffer.allocate());
        tail = _tail;
        tailLength = tail.length();
      }
      else if (bufferSize <= tailLength) {
        addBuffer(TempBuffer.allocate());
        tail = _tail;
        tailLength = tail.length();
      }

      int sublen = Math.min(bufferSize - tailLength, length - index);

      System.arraycopy(buf, index + offset, tail.buffer(), tailLength, sublen);

      index += sublen;
      tailLength += sublen;
      tail.length(tailLength);
    }
  }

  @Override
  public void write(byte []buffer)
  {
    write(buffer, 0, buffer.length);
  }

  private void addBuffer(TempBuffer buf)
  {
    buf.setNext(null);
    
    if (_tail != null) {
      _tail.setNext(buf);
      _tail = buf;
    } else {
      _tail = buf;
      _head = buf;
    }

    _head.setBufferCount(_head.getBufferCount() + 1);
  }

  @Override
  public void flush()
  {
  }

  @Override
  public void close()
  {
  }

  /**
   * Opens a read stream to the buffer.
   */
  /*
  public ReadStream openRead()
    throws IOException
  {
    close();

    TempBuffer head = _head;
    TempReadStream read = new TempReadStream(head);
    read.setFreeWhenDone(true);
    _head = null;
    _tail = null;

    return new ReadStream(read);
  }
  */

  /**
   * Opens a read stream to the buffer.
   */
  public InputStream getInputStream()
    throws IOException
  {
    close();

    TempBuffer head = _head;
    _head = null;
    _tail = null;

    return new TempInputStream(head);
  }

  /**
   * Opens a read stream to the buffer.
   */
  public InputStream openInputStream()
    throws IOException
  {
    close();

    TempBuffer head = _head;
    _head = null;
    _tail = null;

    return new TempInputStream(head);
  }

  /**
   * Opens a read stream to the buffer.
   */
  public InputStream openInputStreamNoFree()
    throws IOException
  {
    close();

    TempBuffer head = _head;

    return new TempInputStreamNoFree(head);
  }

  /**
   * Returns the head buffer.
   */
  public TempBuffer getHead()
  {
    return _head;
  }

  /**
   * clear without removing
   */
  public void clear()
  {
    _head = null;
    _tail = null;
  }

  public void writeToStream(OutputStream os)
    throws IOException
  {
    TempBuffer head = _head;
    _head = null;
    
    for (TempBuffer ptr = head; ptr != null; ptr = ptr.getNext()) {
      os.write(ptr.buffer(), 0, ptr.length());
    }
    
    TempBuffer.freeAll(head);
  }

  /**
   * Returns the total length of the buffer's bytes
   */
  public int getLength()
  {
    int length = 0;

    for (TempBuffer ptr = _head; ptr != null; ptr = ptr.getNext()) {
      length += ptr.length();
    }

    return length;
  }

  public byte []toByteArray()
  {
    int len = getLength();
    byte []data = new byte[len];

    int offset = 0;
    for (TempBuffer ptr = _head; ptr != null; ptr = ptr.getNext()) {
      System.arraycopy(ptr.buffer(), 0, data, offset, ptr.length());
      offset += ptr.length();
    }

    return data;
  }

  public void readAll(int position, char []buffer, int offset, int length)
  {
    TempBuffer ptr = _head;

    for (; ptr != null && ptr.length() <= position; ptr = ptr.getNext()) {
      position -= ptr.length();
    }

    if (ptr == null)
      return;

    for (; ptr != null && length >= 0; ptr = ptr.getNext()) {
      int sublen = Math.min(length, ptr.length() - position);

      byte []dataBuffer = ptr.buffer();
      int tail = position + sublen;

      for (; position < tail; position++) {
        buffer[offset++] = (char) (dataBuffer[position] & 0xff);
      }

      length -= sublen;
    }
  }

  public void readAll(int position, byte []buffer, int offset, int length)
  {
    TempBuffer ptr = _head;

    for (; ptr != null && ptr.length() <= position; ptr = ptr.getNext()) {
      position -= ptr.length();
    }

    if (ptr == null)
      return;

    for (; ptr != null && length >= 0; ptr = ptr.getNext()) {
      int sublen = Math.min(length, ptr.length() - position);

      byte []dataBuffer = ptr.buffer();

      System.arraycopy(dataBuffer, position, buffer, offset, sublen);

      offset += sublen;
      position = 0;
      length -= sublen;
    }
  }

  /**
   * Clean up the temp stream.
   */
  public void destroy()
  {
    TempBuffer ptr = _head;

    _head = null;
    _tail = null;

    TempBuffer.freeAll(ptr);
  }
  
  @Override
  public String toString()
  {
    TempBuffer head = _head;
    
    if (head != null) {
      return (getClass().getSimpleName()
             + "[" + Hex.toShortHex(head.buffer(), 0, head.length()) + "]");
    }
    else {
      return getClass().getSimpleName() + "[null]";
    }
  }
}
