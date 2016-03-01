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

import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.util.Crc64;

import java.io.IOException;

public class Crc64Stream extends StreamImpl {
  private StreamImpl _next;
  private long _crc;

  public Crc64Stream(StreamImpl next)
  {
    _next = next;
  }

  public Crc64Stream()
  {
  }

  /**
   * Initialize the filter with a new stream.
   */
  public void init(StreamImpl next)
  {
    _next = next;
    _crc = 0;
  }

  /**
   * Returns the CRC value.
   */
  public long getCRC()
  {
    return _crc;
  }

  /**
   * Returns true if the stream can read.
   */
  public boolean canRead()
  {
    return _next.canRead();
  }


  /**
   * Reads a buffer from the underlying stream.
   *
   * @param buffer the byte array to read.
   * @param offset the offset into the byte array.
   * @param length the number of bytes to read.
   */
  public int read(byte []buffer, int offset, int length)
    throws IOException
  {
    int len = _next.read(buffer, offset, length);

    _crc = Crc64.generate(_crc, buffer, offset, len);

    return len;
  }

  /**
   * Returns true if the stream can write.
   */
  public boolean canWrite()
  {
    return _next.canWrite();
  }


  /**
   * Writes a buffer to the underlying stream.
   *
   * @param buffer the byte array to write.
   * @param offset the offset into the byte array.
   * @param length the number of bytes to write.
   * @param isEnd true when the write is flushing a close.
   */
  public void write(byte []buffer, int offset, int length, boolean isEnd)
    throws IOException
  {
    _crc = Crc64.generate(_crc, buffer, offset, length);
    
    _next.write(buffer, offset, length, isEnd);
  }

  /**
   * Clears any buffered values in the write.
   */
  public void clearWrite()
  {
    _next.clearWrite();
  }

  /**
   * Flushes the write output.
   */
  public void flush() throws IOException
  {
    _next.flush();
  }

  /**
   * Closes the write output.
   */
  public void closeWrite() throws IOException
  {
    _next.closeWrite();
  }

  /**
   * Returns the path.
   */
  /*
  public PathImpl getPath()
  {
    return _next.getPath();
  }
  */

  /**
   * Sets the path.
   */
  /*
  public void setPath(PathImpl path)
  {
    _next.setPath(path);
  }
  */

  /**
   * Closes the stream output.
   */
  public void close() throws IOException
  {
    _next.close();
  }
}
