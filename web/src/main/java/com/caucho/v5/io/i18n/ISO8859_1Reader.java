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

package com.caucho.v5.io.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * Implements an encoding reader for ISO-8859-1
 */
public class ISO8859_1Reader extends EncodingReader {
  private InputStream is;

  /**
   * Null-arg constructor for instantiation by com.caucho.v5.vfs.Encoding only.
   */
  public ISO8859_1Reader()
  {
  }

  /**
   * Create an ISO-8859-1 reader based on the readStream.
   */
  public ISO8859_1Reader(InputStream is)
  {
    this.is = is;
  }

  /**
   * Create a ISO-8859-1 reader based on the readStream.
   *
   * @param is the input stream providing the bytes.
   * @param javaEncoding the JDK name for the encoding.
   *
   * @return null, since ReadStream handles this directly.
   */
  public Reader create(InputStream is, String javaEncoding)
  {
    return null;
  }

  /**
   * Reads the next character.
   */
  public int read()
    throws IOException
  {
    return is.read();
  }

  /**
   * Reads the next character.
   */
  public int read(char []buf, int offset, int length)
    throws IOException
  {
    for (int i = 0; i < length; i++) {
      int ch = is.read();

      if (ch < 0)
        return i > 0 ? i : -1;
      else {
        buf[offset + i] = (char) ch;
      }
    }
      
    return length;
  }
}
