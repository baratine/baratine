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
import java.io.OutputStream;

import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.io.TempBuffer;

public class MemoryStream extends StreamImpl {
  private TempBuffer _head;
  private TempBuffer _tail;

  /*
  @Override
  public PathImpl getPath() { return new NullPath("temp:"); }
  */

  /**
   * A memory stream is writable.
   */
  @Override
  public boolean canWrite()
  {
    return true;
  }
  
  /**
   * Writes a buffer to the underlying stream.
   *
   * @param buffer the byte array to write.
   * @param offset the offset into the byte array.
   * @param length the number of bytes to write.
   * @param isEnd true when the write is flushing a close.
   */
  @Override
  public void write(byte []buf, int offset, int length, boolean isEnd)
    throws IOException
  {
    while (offset < length) {
      TempBuffer tail = _tail;
      
      if (tail == null || tail.buffer().length <= tail.length()) {
        addBuffer(TempBuffer.allocate());
        tail = _tail;
      }
      
      int tailLength = tail.length();
      
      
      byte []tailBuffer = tail.buffer();

      int sublen = tailBuffer.length - tailLength;
      if (length - offset < sublen) {
        sublen = length - offset;
      }

      System.arraycopy(buf, offset, tailBuffer, tailLength, sublen);

      offset += sublen;
      tail.length(tailLength + sublen);
    }
  }

  private void addBuffer(TempBuffer buf)
  {
    buf.setNext(null);
    buf.length(0);
    
    if (_tail != null) {
      _tail.setNext(buf);
      _tail = buf;
    } else {
      _tail = buf;
      _head = buf;
    }
    
    _head.setBufferCount(_head.getBufferCount() + 1);
  }

  public void writeToStream(OutputStream os) throws IOException
  {
    for (TempBuffer node = _head; node != null; node = node.getNext()) {
      os.write(node.buffer(), 0, node.length());
    } 
  }

  public int getLength()
  {
    if (_tail == null)
      return 0;
    else
      return (_head.getBufferCount() - 1) * _head.length() + _tail.length();
  }

  public ReadStream openReadAndSaveBuffer()
    throws IOException
  {
    close();

    TempReadStream read = new TempReadStream(_head);
    read.setFreeWhenDone(false);

    return new ReadStream(read);
  }

  public void destroy()
  {
    TempBuffer ptr;
    TempBuffer next;

    ptr = _head;
    _head = null;
    _tail = null;
    
    for (; ptr != null; ptr = next) {
      next = ptr.getNext();
      TempBuffer.free(ptr);
      ptr = null;
    }
  }
}
