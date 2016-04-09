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

import com.caucho.v5.io.WriteBuffer;
import com.caucho.v5.util.Hex;

/**
 * btree-based node
 */
class PageLeafEntry implements Comparable<Object> {
  private final BlockLeaf _block;
  private final Row _row;
  private final int _rowOffset;
  private final int _length;
  private final int _code;

  PageLeafEntry(BlockLeaf block, Row row, int offset, int length, int code)
  {
    _block = block;
    _row = row;
    _rowOffset = offset;
    _length = length;
    _code = code;
  }

  int getCode()
  {
    return _code;
  }

  public int getSize()
  {
    return _length;
  }

  private int getKeyOffset()
  {
    if (_code == BlockLeaf.REMOVE) {
      return ColumnState.LENGTH;
    }
    else {
      return _row.getKeyOffset();
    }
  }

  void getKey(byte[] key)
  {
    System.arraycopy(_block.getBuffer(), 
                     _rowOffset + getKeyOffset(),
                     key,
                     0, 
                     _row.getKeyLength());
  }

  long getExpires()
  {
    byte []buffer = _block.getBuffer();
    int rowOffset = _rowOffset;
    
    ColumnState columnState = _row.getColumnState();
    
    long timeout = columnState.getTimeout(buffer, rowOffset);
    
    if (timeout == 0) {
      return 0;
    }
    
    long time = columnState.getTime(buffer, rowOffset);
    
    return time + timeout;
  }

  /**
   * Copies the row and its inline blobs to the target buffer.
   * 
   * @return -1 if the row or the blobs can't fit in the new buffer.
   */
  public int copyTo(byte []buffer, int rowOffset, int blobTail)
  {
    byte []blockBuffer = _block.getBuffer();

    System.arraycopy(blockBuffer, _rowOffset, buffer, rowOffset, _length);

    return _row.copyBlobs(blockBuffer, _rowOffset, buffer, rowOffset,
                          blobTail);
  }

  public void insertInto(PageLeafEntry newPage)
  {

  }

  //
  // checkpoint persistence
  //

  void writeCheckpoint(WriteBuffer os)
      throws IOException
  {
    if (_code == BlockLeaf.REMOVE) {
      return;
    }

    _row.writeCheckpoint(os, _block.getBuffer(), _rowOffset);
  }

  void writeDelta(WriteBuffer os)
      throws IOException
  {
    switch (_code) {
    case BlockLeaf.INSERT:
      _row.writeCheckpoint(os, _block.getBuffer(), _rowOffset);
      break;

    case BlockLeaf.REMOVE:
      os.write(_block.getBuffer(), 
               _rowOffset,
               ColumnState.LENGTH);
      
      os.write(_block.getBuffer(), 
               _rowOffset + ColumnState.LENGTH, // + _row.getKeyOffset(),
               _row.getKeyLength());
      break;
    }
  }

  @Override
  public int compareTo(Object o)
  {
    PageLeafEntry entry = (PageLeafEntry) o;

    int keyA = getKeyOffset();
    int keyB = entry.getKeyOffset();

    return KeyComparator.INSTANCE.compare(_block.getBuffer(),
                                          _rowOffset + keyA,
                                          entry._block.getBuffer(),
                                          entry._rowOffset + keyB,
                                          _row.getKeyLength());
  }

  @Override
  public int hashCode()
  {
    byte []buffer = _block.getBuffer();

    int offset = _rowOffset + _row.getKeyOffset();
    int len = _row.getKeyLength();

    int hash = 37;

    for (int i = 0; i < len; i++) {
      hash = 65521 * hash + buffer[offset + i];
    }

    return hash;
  }

  @Override
  public boolean equals(Object o)
  {
    return compareTo(o) == 0;
  }

  public String toString()
  {
    StringBuilder sb = new StringBuilder();

    sb.append(getClass().getSimpleName()).append("[");

    byte []buffer = _block.getBuffer();

    int offset = _rowOffset + _row.getKeyOffset();
    int len = _row.getKeyLength();

    sb.append(Hex.toShortHex(buffer, offset, len));

    sb.append("]");

    return sb.toString();
  }
}
