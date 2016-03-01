/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
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

package com.caucho.v5.kelp;

import io.baratine.db.BlobReader;

public class BlobReaderImpl implements BlobReader
{
  private final RowCursor _cursor;
  private final ColumnBlob _column;

  private byte []_pageBuffer;
  private int _offset;
  private int _tail;
  private int _length;

  /**
   * Creates a blob output stream.
   *
   * @param store the output store
   */
  public BlobReaderImpl(RowCursor cursor,
                         ColumnBlob column,
                         int blobOffset,
                         int blobLength,
                         byte []pageBuffer)
  {
    _cursor = cursor;
    _column = column;
    
    _offset = blobOffset;
    _length = blobLength;
    
    _tail = blobOffset + blobLength;
    _pageBuffer = pageBuffer;
  }
  
  @Override
  public long getLength()
  {
    return _length;
  }

  /**
   * Reads a buffer.
   */
  @Override
  public int read(long pos, byte []buf, int offset, int length)
  {
    if (pos < 0) {
      throw new IllegalArgumentException();
    }
    
    if (_length <= pos) {
      return -1;
    }
    
    int bufOffset = (int) (_offset + pos);
    int sublen = (int) Math.min(length, _length - pos);
    
    System.arraycopy(_pageBuffer, bufOffset, buf, offset, sublen);
    
    return sublen;
  }
  
  /**
   * Closes the buffer.
   */
  public void close()
  {
  }

  @Override
  public boolean isValid()
  {
    return false;
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
           + "[" + _column.name() + "," + _cursor + "]"); 
  }
}
