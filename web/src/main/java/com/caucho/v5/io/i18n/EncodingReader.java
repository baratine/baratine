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
import java.io.UnsupportedEncodingException;

/**
 * Abstract class for a byte-to-character encoding reader and its
 * associated factory.
 *
 * <p/>Implementations need to implement <code>create</code>
 * and <code>read()</code> at minimum.  Efficient implementations will
 * also implement the <code>read</code> into a buffer.
 *
 * <p/>Implementations should not buffer the bytes.
 */
abstract public class EncodingReader extends Reader {
  private String _javaEncoding;

  public String getJavaEncoding()
  {
    return _javaEncoding;
  }

  public void setJavaEncoding(String encoding)
  {
    _javaEncoding = encoding;
  }
  
  /**
   * Returns a new encoding reader for the given stream and javaEncoding.
   *
   * @param is the input stream providing the bytes.
   * @param javaEncoding the JDK name for the encoding.
   *
   * @return an encoding reader or null for ISO-8859-1.
   */
  public abstract Reader create(InputStream is, String javaEncoding)
    throws UnsupportedEncodingException;
  
  /**
   * Returns a new encoding reader for the given stream and javaEncoding.
   *
   * @param is the input stream providing the bytes.
   * @param javaEncoding the JDK name for the encoding.
   *
   * @return an encoding reader or null for ISO-8859-1.
   */
  public Reader create(InputStream is)
    throws UnsupportedEncodingException
  {
    Reader reader = create(is, getJavaEncoding());

    if (reader != null)
      return reader;
    else
      return new ISO8859_1Reader(is);
  }

  /**
   * Returns the next character using the correct encoding.
   *
   * @return the next character or -1 on end of file.
   */
  public abstract int read()
    throws IOException;

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
    for (int i = 0; i < len; i++) {
      int ch = read();

      if (ch < 0)
        return len;

      cbuf[off + i] = (char) ch;
    }

    return len;
  }

  /**
   * Closes the reader, possibly returning it to a pool.
   */
  public void close()
  {
  }
}
