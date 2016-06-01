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

package com.caucho.v5.kelp.segment;

import java.util.Comparator;

/**
 * General data about the segment.
 */
public class SegmentExtent implements Comparable<SegmentExtent>
{
  private final int _id;
  private final long _address;
  private final int _length;
  
  SegmentExtent(int id,
                long address, 
                int length)
  {
    if ((address & 0xffff) != 0) {
      throw new IllegalArgumentException();
    }
    
    if (Integer.bitCount(length) != 1) {
      throw new IllegalArgumentException();
    }
    
    if (length < 0x1_0000) {
      throw new IllegalArgumentException();
    }
    
    _id = id;
    _address = address;
    _length = length;
  }

  public int getId()
  {
    return _id;
  }

  public final long address()
  {
    return _address;
  }

  public final int length()
  {
    return _length;
  }
  
  @Override
  public int compareTo(SegmentExtent extent)
  {
    return Long.signum(_address - extent._address);
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + _id
            + ",0x" + Long.toHexString(_address)
            + ",len=" + _length
            + "]");
  }
}
