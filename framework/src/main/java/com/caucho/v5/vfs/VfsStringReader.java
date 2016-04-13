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

import com.caucho.v5.io.StreamImpl;

/**
 * A stream reading data from a string.  The reader produces bytes using
 * UTF-8.
 */
public class VfsStringReader extends StreamImpl {
  private String string;
  private int length;
  private int index;

  private VfsStringReader(String string)
  {
    // this.path = new NullPath("string");
    this.string = string;
    this.length = string.length();
    this.index = 0;
  }

  /**
   * Creates a new ReadStream reading bytes from the given string.
   *
   * @param string the source string.
   *
   * @return a ReadStream reading from the string.
   */
  public static ReadStreamOld open(String string)
  {
    VfsStringReader ss = new VfsStringReader(string);
    ReadStreamOld rs = new ReadStreamOld(ss);
    try {
      rs.setEncoding("UTF-8");
    } catch (Exception e) {
    }
    //rs.setPath(new NullPath("string"));
    return rs;
  }

  /**
   * The string reader can always read.
   */
  public boolean canRead()
  {
    return true;
  }

  public int read(byte []buf, int offset, int length)
    throws IOException
  {
    char ch;

    int i = 0;
    for (; index < this.length && i < length; index++) {
      ch = string.charAt(index);

      if (ch < 0x80) {
        buf[offset + i] = (byte) ch;
        i++;
      }
      else if (i + 1 >= length)
        break;
      else if (ch < 0x800) {
        buf[offset + i] = (byte) (0xc0 + (ch >> 6));
        buf[offset + i + 1] = (byte) (0x80 + (ch & 0x3f));
        i += 2;
      }
      else if (i + 2 >= length)
        break;
      else {
        buf[offset + i] = (byte) (0xe0 + (ch >> 12));
        buf[offset + i + 1] = (byte) (0x80 + ((ch >> 6) & 0x3f));
        buf[offset + i + 2] = (byte) (0x80 + (ch & 0x3f));
        
        i += 3;
      }
    }

    return i > 0 ? i : -1;
  }

  /**
   * Returns the number of characters available as an approximation to
   * the number of bytes ready.
   */
  public int getAvailable() throws IOException
  {
    return length - index;
  }
}
