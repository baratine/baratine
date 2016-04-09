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

import com.caucho.v5.util.BitsUtil;
import com.caucho.v5.util.CurrentTime;

/**
 * The state column for a kelp row, recording the sequence and timeout.
 * 
 * state : 2
 * timeout : 30 (timeout in s, 1 billion sec = )
 * time : 48  (time in ms)
 * v_seq : 16
 */
public class ColumnState extends Column
{
  public static final int STATE_MASK = Page.CODE_MASK << 24;
  public static final int STATE_DATA = Page.INSERT << 24;
  public static final int STATE_REMOVED = 0;
  
  public static final int TIME_MASK = ~STATE_MASK;
  public static final long VERSION_MASK = ~0L; // (1L << 48) - 1;
  public static final long VERSION_TIME_SHIFT = 16;
  
  public static final int LENGTH = 12;
  
  public ColumnState(int index,
                     String name,
                     int offset)
  {
    super(index, name, ColumnType.STATE, offset);
  }

  @Override
  public final int length()
  {
    return LENGTH;
  }
  
  public static long getState(long value)
  {
    return (value & STATE_MASK);
  }
  
  public static boolean isRemoved(long value)
  {
    return getState(value) == STATE_REMOVED;
  }
  
  public static boolean isData(long value)
  {
    return getState(value) == STATE_DATA;
  }
  
  public int getTimeout(byte []rowBuffer, int rowOffset)
  {
    int value = BitsUtil.readInt(rowBuffer, rowOffset + getOffset());
    
    return value & TIME_MASK;
  }
  
  public void setTimeout(byte []rowBuffer, int rowOffset, int value)
  {
    value = value & TIME_MASK;
    
    BitsUtil.writeInt(rowBuffer, rowOffset + getOffset(), value);
  }
  
  public long getVersion(byte []rowBuffer, int rowOffset)
  {
    long value = BitsUtil.readLong(rowBuffer, rowOffset + getOffset() + 4);
    
    return value & VERSION_MASK;
  }
  
  public void setVersion(byte []rowBuffer, int rowOffset, long value)
  {
    value = value & VERSION_MASK;
    
    int offset = rowOffset + getOffset() + 4;
    
    BitsUtil.writeLong(rowBuffer, offset, value);
  }
  
  public long getTime(byte []rowBuffer, int rowOffset)
  {
    long version = BitsUtil.readLong(rowBuffer, rowOffset + getOffset() + 4);
    
    // XXX: theoretical rollover issues
    
    return (version >> 16) | (CurrentTime.currentTime() & (0xffffL << 48)); 
  }

  /*
  public void setTime(byte []rowBuffer, int rowOffset, int value)
  {
    BitsUtil.writeInt(rowBuffer, rowOffset + getOffset() + 4, value);
  }

  public void setDateTime(byte []rowBuffer, int rowOffset, 
                          int timeout,
                          long version)
  {
  }
  */

  /*
  @Override
  void writeCheckpoint(WriteStream os, byte[] buffer, int offset)
  {
  }

  @Override
  int readCheckpoint(ReadStream is, 
                     byte[] buffer, int offset, int rowLength,
                     int tail)
  {
    buffer[offset + getOffset()] = STATE_DATA;
    
    return tail;
  }

  @Override
  void writeJournal(OutputStream os, byte[] buffer, int offset,
                    BlobOutputStream blob)
    throws IOException
  {
  }

  @Override
  void readJournal(TableServiceImpl pageActor,
                   ReadStream is, 
                   byte[] buffer, int offset,
                   RowCursor cursor)
  {
  }
  */
}
