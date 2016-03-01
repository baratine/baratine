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

package com.caucho.v5.vfs;

import java.io.IOException;
import java.io.InputStream;

import com.caucho.v5.io.StreamImpl;

/**
 * Unbuffered input stream to the stream impl
 */
public class StreamImplInputStream extends InputStream
{
  private StreamImpl _stream;
  private byte []_buf = new byte[1];

  public StreamImplInputStream(StreamImpl stream)
  {
    _stream = stream;
  }

  /**
   * Writes a byte to the underlying stream.
   *
   * @param v the value to write
   */
  public int read()
    throws IOException
  {
    int len = _stream.read(_buf, 0, 1);

    if (len == 1)
      return _buf[0] & 0xff;
    else
      return -1;
  }
  
  /**
   * Reads a buffer to the underlying stream.
   *
   * @param buffer the byte array to write.
   * @param offset the offset into the byte array.
   * @param length the number of bytes to write.
   */
  public int read(byte []buffer, int offset, int length)
    throws IOException
  {
    return _stream.read(buffer, offset, length);
  }

  /**
   * Closes the stream.
   */
  public void close() throws IOException
  {
    _stream.close();
  }
}
