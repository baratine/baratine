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

import com.caucho.v5.util.Crc64;

import java.io.IOException;
import java.io.OutputStream;

public class Crc64OutputStream extends OutputStream {
  private final OutputStream _next;
  private long _crc = 1;

  public Crc64OutputStream(OutputStream next)
  {
    _next = next;
  }

  /**
   * Returns the CRC value.
   */
  public long getDigest()
  {
    return _crc;
  }
  
  @Override
  public void write(int data)
    throws IOException
  {
    _crc = Crc64.generate(_crc, data);
    
    _next.write(data);
  }

  /**
   * Writes a buffer to the underlying stream.
   *
   * @param buffer the byte array to write.
   * @param offset the offset into the byte array.
   * @param length the number of bytes to write.
   * @param
   * @Override isEnd true when the write is flushing a close.
   */
  @Override
  public void write(byte []buffer, int offset, int length)
    throws IOException
  {
    _crc = Crc64.generate(_crc, buffer, offset, length);
      
    _next.write(buffer, offset, length);
  }

  /**
   * Closes the stream output.
   */
  public void close() throws IOException
  {
    _next.close();
  }
}
