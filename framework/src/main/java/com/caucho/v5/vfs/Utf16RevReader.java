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
import java.io.InputStream;
import java.io.Reader;

class Utf16RevReader extends Reader {
  private InputStream is;

  /**
   * Create a UTF-16 reader based on the readStream.
   */
  Utf16RevReader(InputStream is)
  {
    this.is = is;
  }

  /**
   * Reads into a character buffer using the correct encoding.
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
   */
  public int read(char []cbuf, int off, int len)
    throws IOException
  {
    int i = 0;

    for (i = 0; i < len; i++) {
      int ch1 = is.read();
      int ch2 = is.read();

      if (ch1 < 0)
        return i == 0 ? -1 : i;

      cbuf[off + i] = (char) ((ch2 << 8) + ch1);
    }

    return i;
  }

  /**
   * Stub close does nothing.
   */
  public void close()
  {
  }
}
