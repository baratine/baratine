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

import com.caucho.v5.util.L10N;



/**
 * A column for the log store with a fixed-length byte array.
 */
public class ColumnBytes extends Column
{
  private static final L10N L = new L10N(ColumnBytes.class);
  
  private final int _length;
  
  public ColumnBytes(int index,
                         String name,
                         int offset,
                         int length)
  {
    super(index, name, ColumnType.BYTES, offset);
    
    _length = length;
  }

  @Override
  public final int length()
  {
    return _length;
  }
  
  @Override
  public final int size()
  {
    return length();
  }
  
  @Override
  public void setBytes(byte []rowBuffer, int rowOffset,
                       byte []buffer, int offset)
  {
    if (buffer.length - offset < length()) {
      throw new IllegalArgumentException(L.l("{0} requires bytes length={1}, but received {2}",
                                             this, length(), 
                                             buffer.length - offset));
    }
    
    System.arraycopy(buffer, offset, 
                     rowBuffer, rowOffset + getOffset(),
                     length());
  }
  
  @Override
  public void getBytes(byte []rowBuffer, int rowOffset,
                       byte []buffer, int offset)
  {
    if (buffer.length != length()) {
      throw new IllegalArgumentException(L.l("{0} requires bytes length={1}, but received {2}",
                                             this, length(), 
                                             buffer.length - offset));
    }
    
    System.arraycopy(rowBuffer, rowOffset + getOffset(),
                     buffer, offset,
                     length());
  }
}
