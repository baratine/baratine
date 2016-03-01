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
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

/**
 * Reader to handle any encoding by passing it to the JDK.
 */
public class JDKReader extends EncodingReader {
  /**
   * Null-arg constructor for instantiation by com.caucho.v5.vfs.Encoding only.
   */
  public JDKReader()
  {
  }

  /**
   * Create a JDK-based reader.
   *
   * @param is the input stream providing the bytes.
   * @param javaEncoding the JDK name for the encoding.
   *
   * @return an InputStreamReader
   */
  public Reader create(InputStream is, String javaEncoding)
    throws UnsupportedEncodingException
  {
    if (Charset.isSupported(javaEncoding)) {
      Charset charset = Charset.forName(javaEncoding);
    
      return new InputStreamReader(is, charset);
    }
    else {
      // RSN-274, Charset doesn't support all java.io encoding
      return new InputStreamReader(is, javaEncoding);
    }
  }

  /**
   * Reads into a character buffer using the correct encoding.
   */
  public int read()
    throws IOException
  {
    throw new UnsupportedOperationException();
  }
}
