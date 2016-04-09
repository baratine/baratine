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

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import com.caucho.v5.io.TempBuffer;

public class PageBlobInputStream extends InputStream
{
  private long _offset;
  
  private byte []_data;

  private BlobReaderPageImpl _pageReader;

  private final long _length;
  
  /**
   * Creates a blob output stream.
   *
   * @param store the output store
   */
  public PageBlobInputStream(RowCursor cursor,
                             ColumnBlob column,
                             PageBlob blobPage)
  {
    _pageReader = new BlobReaderPageImpl(cursor, column, blobPage);
    
    _length = _pageReader.getLength();
  }

  /**
   * Reads a byte.
   */
  @Override
  public int read()
    throws IOException
  {
    if (_data == null) {
      _data = new byte[1];
    }
    
    int result = _pageReader.read(_offset, _data, 0, 1);
    
    if (result > 0) {
      _offset++;
      
      return _data[0] & 0xff;
    }
    else {
      return -1;
    }
  }

  /**
   * Reads a buffer.
   */
  @Override
  public int read(byte []buf, int offset, int length)
    throws IOException
  {
    int sublen = _pageReader.read(_offset, buf, offset, length);
    
    if (sublen > 0) {
      _offset += sublen;
      
      return sublen;
    }
    else {
      return sublen;
    }
  }
  
  @Override
  public long skip(long n)
    throws IOException
  {
    long offset = _offset;
    
    _offset = Math.min(_length, _offset + n);
    
    return _offset - offset;
  }
  
  /**
   * Closes the stream.
   */
  @Override
  public void close()
  {
    _pageReader.close();
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
           + "[" + _pageReader + "]");
  }
}
