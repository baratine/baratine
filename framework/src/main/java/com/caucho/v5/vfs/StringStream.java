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

public class StringStream extends StreamImpl {
  private final String _string;
  private final int _length;
  private int _index;

  StringStream(String string)
  {
    // this.path = new NullPath("string");
    _string = string;
    _length = string.length();
    _index = 0;
  }

  public static ReadStreamOld open(String string)
  {
    StringStream ss = new StringStream(string);
    return new ReadStreamOld(ss);
  }

  public PathImpl getPath() { return new StringPath(_string); }

  public boolean canRead() { return true; }

  // XXX: encoding issues
  public int read(byte []buf, int offset, int length) throws IOException
  {
    int strlen = _length;

    int start = offset;
    int end = offset + length;
    
    int index = _index;
    for (; index < strlen && offset < end; index++) {
      int ch = _string.charAt(index);

      if (ch < 0x80)
        buf[offset++] = (byte) ch;
      else if (ch < 0x800 && offset + 1 < end) {
        buf[offset++] = (byte) (0xc0 | (ch >> 6));
        buf[offset++] = (byte) (0x80 | (ch & 0x3f));
      }
      else if (ch < 0x8000 && offset + 2 < end) {
        buf[offset++] = (byte) (0xe0 | (ch >> 12));
        buf[offset++] = (byte) (0x80 | ((ch >> 6) & 0x3f));
        buf[offset++] = (byte) (0x80 | ((ch >> 6) & 0x3f));
      }
      else if (offset == start) {
        throw new IllegalStateException("buffer length is not large enough to decode UTF-8 data");
      }
      else {
        break;
      }
    }

    _index = index;

    return start < offset ? offset - start : -1;
  }

  public int getAvailable() throws IOException
  {
    return _length - _index;
  }
}
