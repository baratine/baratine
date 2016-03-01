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

import com.caucho.v5.io.StreamImpl;

/**
 * Stream encapsulating System.err.
 */
public class StderrStream extends StreamImpl {
  private static StderrStream _stderr;

  private StderrStream()
  {
  }

  /**
   * Returns the StderrStream singleton
   */
  public static StderrStream create()
  {
    if (_stderr == null) {
      _stderr = new StderrStream();
      ConstPath path = new ConstPath(null, _stderr);
      path.setScheme("stderr");
      //_stderr.setPath(path);
    }

    return _stderr;
  }

  /**
   * The standard error stream returns true since it's writable.
   */
  public boolean canWrite()
  {
    return true;
  }

  /**
   * Writes the data to the System.out.
   *
   * @param buf the buffer to write.
   * @param offset starting offset in the buffer.
   * @param length number of bytes to write.
   * @param isEnd true when the stream is closing.
   */
  public void write(byte []buf, int offset, int length, boolean isEnd)
    throws IOException
  {
    System.err.write(buf, offset, length);
    System.err.flush();
  }
}
