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
import java.io.InputStream;
import java.io.Reader;

import com.caucho.v5.io.i18n.EncodingReader;

/**
 * Implements an encoding reader for UTF-16 using the reversed order.
 */
public class UTF16_REVReader extends EncodingReader {
  private InputStream is;

  /**
   * Null-arg constructor for instantiation by com.caucho.v5.vfs.Encoding only.
   */
  public UTF16_REVReader()
  {
  }

  /**
   * Create a UTF-16 reader using the input stream to provide bytes.
   */
  private UTF16_REVReader(InputStream is)
  {
    this.is = is;
  }

  /**
   * Create a UTF-16 reader based on the readStream.
   *
   * @param is the input stream providing the bytes.
   * @param javaEncoding the JDK name for the encoding.
   *
   * @return the UTF-16 reader.
   */
  public Reader create(InputStream is, String javaEncoding)
  {
    return new UTF16_REVReader(is);
  }

  /**
   * Reads into a character buffer using the correct encoding.
   *
   * @return the next character or -1 on end of file.
   */
  public int read()
    throws IOException
  {
    int ch1 = is.read();
    int ch2 = is.read();

    if (ch2 < 0)
      return -1;

    return (ch2 << 8) + ch1;
  }

  /**
   * Reads into a character buffer using the correct encoding.
   *
   * @param cbuf character buffer receiving the data.
   * @param off starting offset into the buffer.
   * @param len number of characters to read.
   *
   * @return the number of characters read or -1 on end of file.
   */
  public int read(char []cbuf, int off, int len)
    throws IOException
  {
    int i = 0;

    for (i = 0; i < len; i++) {
      int ch1 = is.read();
      int ch2 = is.read();

      if (ch2 < 0)
        return i == 0 ? -1 : i;

      cbuf[off + i] = (char) ((ch2 << 8) + ch1);
    }

    return i;
  }
}
