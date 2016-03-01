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

package com.caucho.v5.db.temp_store;

import java.io.IOException;
import java.io.InputStream;

public class InputStreamChunked extends InputStream
{
  private InputStream _is;
  private int _length;
  
  public InputStreamChunked(InputStream is, int offset, int length)
    throws IOException
  {
    _is = is;
    _length = length;
    
    _is.skip(offset);
  }
  
  @Override
  public int read()
    throws IOException
  {
    if (_length > 0) {
      _length--;
    }
    
    return _is.read();
  }
  
  @Override
  public int read(byte []buffer, int offset, int length)
    throws IOException
  {
    int sublen = Math.min(_length, length);
    
    int result = _is.read(buffer, offset, sublen);
    
    if (result <= 0) {
      return -1;
    }
    
    _length -= result;
    
    return result;
  }
  
  @Override
  public long skip(long len)
    throws IOException
  {
    long sublen = Math.min(_length, len);
    
    sublen = _is.skip(sublen);
    
    if (sublen > 0) {
      _length = (int) (_length - sublen);
    }
    
    return sublen;
  }
  
  @Override
  public void close()
    throws IOException
  {
    _is.close();
  }
}
