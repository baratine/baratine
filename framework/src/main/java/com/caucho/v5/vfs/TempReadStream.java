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
import com.caucho.v5.io.TempBuffer;

public class TempReadStream extends StreamImpl {
  private TempBuffer _cursor;

  private int _offset;
  private boolean _freeWhenDone = true;

  public TempReadStream(TempBuffer cursor)
  {
    init(cursor);
  }

  public TempReadStream()
  {
  }

  public void init(TempBuffer cursor)
  {
    _cursor = cursor;
    _offset = 0;
    _freeWhenDone = true;
  }

  public void setFreeWhenDone(boolean free)
  {
    _freeWhenDone = free;
  }

  @Override
  public boolean canRead() { return true; }

  // XXX: any way to make this automatically free?
  @Override
  public int read(byte []buf, int offset, int length) throws IOException
  {
    TempBuffer cursor = _cursor;
    
    if (cursor == null)
      return -1;

    int sublen = Math.min(length, cursor.length() - _offset);

    System.arraycopy(cursor.buffer(), _offset, buf, offset, sublen);

    if (cursor.length() <= _offset + sublen) {
      _cursor = cursor.next();

      if (_freeWhenDone) {
        cursor.next(null);
        TempBuffer.free(cursor);
        cursor = null;
      }
      _offset = 0;
    }
    else
      _offset += sublen;

    return sublen;
  }

  @Override
  public int getAvailable() throws IOException
  {
    if (_cursor != null)
      return _cursor.length() - _offset;
    else
      return 0;
  }

  @Override
  public void close()
    throws IOException
  {
    if (_freeWhenDone && _cursor != null)
      TempBuffer.freeAll(_cursor);
    
    _cursor = null;
  }

  @Override
  public String toString()
  {
    return "TempReadStream[]";
  }
}
