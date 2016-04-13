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

import java.io.*;

import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.io.TempInputStream;

@SuppressWarnings("serial")
public class TempStream extends StreamImpl
  implements java.io.Serializable, TempStreamApi
{
  private String _encoding;
  private TempBuffer _head;
  private TempBuffer _tail;

  public TempStream()
  {
  }

  /**
   * Initializes the temp stream for writing.
   */
  public void openWrite()
  {
    TempBuffer ptr = _head;

    _head = null;
    _tail = null;

    _encoding = null;

    TempBuffer.freeAll(ptr);
  }

  public byte []getTail()
  {
    return _tail.buffer();
  }

  /**
   * Sets the encoding.
   */
  public void setEncoding(String encoding)
  {
    _encoding = encoding;
  }

  /**
   * Gets the encoding.
   */
  public String getEncoding()
  {
    return _encoding;
  }

  @Override
  public boolean canWrite() { return true; }

  /**
   * Writes a chunk of data to the temp stream.
   */
  @Override
  public void write(byte []buf, int offset, int length, boolean isEnd)
    throws IOException
  {
    while (length > 0) {
      if (_tail == null) {
        addBuffer(TempBuffer.create());
      }
      else if (_tail.buffer().length <= _tail.length()) {
        addBuffer(TempBuffer.create());
      }
      
      TempBuffer tail = _tail;

      int sublen = Math.min(length, tail.buffer().length - tail.length());

      System.arraycopy(buf, offset, tail.buffer(), tail.length(), sublen);

      length -= sublen;
      offset += sublen;
      _tail.length(_tail.length() + sublen);
    }
  }

  private void addBuffer(TempBuffer buf)
  {
    buf.next(null);
    
    if (_tail != null) {
      _tail.next(buf);
      _tail = buf;
    } else {
      _tail = buf;
      _head = buf;
    }

    _head.setBufferCount(_head.getBufferCount() + 1);
  }

  @Override
  public void flush()
    throws IOException
  {
  }

  /**
   * Opens a read stream to the buffer.
   */
  public ReadStreamOld openRead()
    throws IOException
  {
    closeWrite();
    
    TempReadStream read = new TempReadStream(_head);
    read.setFreeWhenDone(true);
    _head = null;
    _tail = null;
    
    return new ReadStreamOld(read);
  }

  /**
   * Opens a read stream to the buffer.
   *
   * @param free if true, frees the buffer as it's read
   */
  public ReadStreamOld openReadAndSaveBuffer()
    throws IOException
  {
    closeWrite();

    TempReadStream read = new TempReadStream(_head);
    read.setFreeWhenDone(false);
    
    return new ReadStreamOld(read);
  }

  /**
   * Opens a read stream to the buffer.
   */
  public void openRead(ReadStreamOld rs)
    throws IOException
  {
    closeWrite();

    TempReadStream tempReadStream = new TempReadStream(_head);
    //tempReadStream.setPath(getPath());
    tempReadStream.setFreeWhenDone(true);
    
    _head = null;
    _tail = null;
    
    rs.init(tempReadStream);
  }

  /**
   * Returns an input stream to the contents, freeing the value
   * automatically.
   */
  public InputStream getInputStream()
    throws IOException
  {
    closeWrite();
    
    TempBuffer head = _head;
    _head = null;
    _tail = null;
    
    return new TempInputStream(head);
  }
  
  public InputStream openInputStream()
    throws IOException
  {
    closeWrite();
    
    TempBuffer head = _head;
    _head = null;
    _tail = null;
    
    return new TempInputStream(head);
  }

  /**
   * Returns the head buffer.
   */
  public TempBuffer getHead()
  {
    return _head;
  }

  public void writeToStream(OutputStream os)
    throws IOException
  {
    for (TempBuffer ptr = _head; ptr != null; ptr = ptr.next()) {
      os.write(ptr.buffer(), 0, ptr.length());
    }
  }

  /**
   * Returns the total length of the buffer's bytes
   */
  public int getLength()
  {
    int length = 0;
    
    for (TempBuffer ptr = _head; ptr != null; ptr = ptr.next()) {
      length += ptr.length();
    }

    return length;
  }

  @Override
  public void clearWrite()
  {
    TempBuffer ptr = _head;

    _head = null;
    _tail = null;

    TempBuffer.freeAll(ptr);
  }

  public void discard()
  {
    _head = null;
    _tail = null;
  }

  /**
   * Copies the temp stream;
   */
  public TempStream copy()
  {
    TempStream newStream = new TempStream();

    TempBuffer ptr = _head;

    for (; ptr != null; ptr = ptr.next()) {
      TempBuffer newPtr = TempBuffer.create();
      
      if (newStream._tail != null)
        newStream._tail.next(newPtr);
      else
        newStream._head = newPtr;
      newStream._tail = newPtr;

      newPtr.write(ptr.buffer(), 0, ptr.length());
    }

    return newStream;
  }

  /**
   * Clean up the temp stream.
   */
  @Override
  public void destroy()
  {
    try {
      close();
    } catch (IOException e) {
    } finally {
      TempBuffer ptr = _head;
    
      _head = null;
      _tail = null;

      TempBuffer.freeAll(ptr);
    }
  }
}
