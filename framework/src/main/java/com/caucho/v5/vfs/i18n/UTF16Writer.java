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

package com.caucho.v5.vfs.i18n;

import java.io.IOException;

import com.caucho.v5.io.ByteAppendable;
import com.caucho.v5.io.OutputStreamWithBuffer;
import com.caucho.v5.io.i18n.EncodingWriter;

/**
 * Implements an encoding char-to-byte writer for UTF16 and the associated
 * factory.
 */
public class UTF16Writer extends EncodingWriter {
  private static final UTF16Writer _writer = new UTF16Writer();

  /**
   * Null-arg constructor for instantiation by com.caucho.v5.vfs.Encoding only.
   */
  public UTF16Writer()
  {
  }

  /**
   * Create a UTF-16 writer using on the WriteStream to send bytes.
   *
   * @param javaEncoding the JDK name for the encoding.
   *
   * @return the UTF-16 writer.
   */
  public EncodingWriter create(String javaEncoding)
  {
    return _writer;
  }

  /**
   * Writes the character using the correct encoding.
   */
  public void write(ByteAppendable os, char ch)
    throws IOException
  {
    os.write(ch >> 8);
    os.write(ch);
  }

  /**
   * Writes a character buffer using the UTF-16 encoding.
   *
   * @param cbuf character array with the data to write.
   * @param off starting offset into the character array.
   * @param len the number of characters to write.
   */
  public int write(OutputStreamWithBuffer os, char []cbuf, int off, int len)
    throws IOException
  {
    for (int i = 0; i < len; i++) {
      char ch = cbuf[off + i];

      os.write(ch >> 8);
      os.write(ch);
    }
    
    return len;
  }
}
