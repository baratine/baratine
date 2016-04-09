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



/**
 * A column for the log store with a 16-bit int.
 */
public class ColumnInt8 extends Column
{
  public ColumnInt8(int index,
                        String name,
                        int offset)
  {
    super(index, name, ColumnType.INT8, offset);
  }

  @Override
  public final int length()
  {
    return 1;
  }
  
  @Override
  public int getInt(byte []rowBuffer, int rowOffset)
  {
    return rowBuffer[rowOffset + getOffset()] & 0xff;
  }
  
  @Override
  public void setInt(byte []rowBuffer, int rowOffset, int value)
  {
    rowBuffer[rowOffset + getOffset()] = (byte) value;
  }
}
