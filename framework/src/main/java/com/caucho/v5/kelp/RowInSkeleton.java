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
import com.caucho.v5.util.L10N;


/**
 * Input stream skeleton for a row.
 */
abstract class RowInSkeleton
{
  private static final L10N L = new L10N(RowInSkeleton.class);
  
  private RowInSkeleton _next;
  
  protected RowInSkeleton(RowInSkeleton next)
  {
    _next = next;
  }
  
  abstract void read(Page page,
                     byte []srcBuffer, 
                     int srcOffset,
                     byte []dstBuffer,
                     int dstOffset, 
                     int length);

  final RowInSkeleton next(byte[] block, int rowOffset)
  {
    RowInSkeleton next = _next;
    
    if (next != null) {
      return next.select(block, rowOffset);
    }
    else {
      return null;
    }
  }
  
  RowInSkeleton select(byte []block, int rowOffset)
  {
    return this;
  }

  abstract int srcOffset(byte[] block, int rowOffset, PageServiceImpl table);
  
  abstract int srcLength(byte[] block, int rowOffset, PageServiceImpl table);
  
  abstract int blobLength(byte[] block, int rowOffset, PageServiceImpl table);
  
  abstract Page srcPage(byte[] block, int rowOffset, PageServiceImpl table);

  static RowInSkeleton build(Column []columns)
  {
    RowInSkeleton head = null;
    
    for (int i = columns.length - 1; i >= 0; i--) {
      Column column = columns[i];
      
      if (column.getType().isBlob()) {
        head = new RowInBlob(head, column.getOffset());
      }
    }
    
    int length = -1;
    int offset = -1;
    
    for (int i = columns.length - 1; i >= 0; i--) {
      Column column = columns[i];
      
      if (column.getType().isBlob()) {
        if (length > 0) {
          head = new RowInFixed(head, offset, length);
          offset = -1;
          length = -1;
        }
      }
      else if (length > 0) {
        offset = column.getOffset();
        length += column.getLength();
      }
      else {
        offset = column.getOffset();
        length = column.getLength();
      }
    }
    
    if (length > 0) {
      head = new RowInFixed(head, offset, length);
    }
    
    return head;
  }
  
  private static class RowInFixed extends RowInSkeleton {
    private int _offset;
    private int _length;
    
    private RowInFixed(RowInSkeleton next, int offset, int length)
    {
      super(next);
      
      _offset = offset;
      _length = length;
    }

    @Override
    void read(Page page,
              byte[] srcBuffer, int srcOffset, 
              byte[] dstBuffer, int dstOffset,
              int length)
    {
      System.arraycopy(srcBuffer, srcOffset, dstBuffer, dstOffset, length);
    }

    @Override
    int srcOffset(byte[] block, int rowOffset, PageServiceImpl table)
    {
      return rowOffset + _offset;
    }

    @Override
    int srcLength(byte[] block, int rowOffset, PageServiceImpl table)
    {
      return _length;
    }

    @Override
    int blobLength(byte[] block, int rowOffset, PageServiceImpl table)
    {
      return -1;
    }

    @Override
    Page srcPage(byte[] block, int rowOffset, PageServiceImpl table)
    {
      return null;
    }
  }
  
  private static class RowInBlobInline extends RowInSkeleton {
    private final int _offset;
    
    RowInBlobInline(RowInSkeleton next, int offset)
    {
      super(next);
      
      _offset = offset;
    }

    @Override
    void read(Page page,
              byte[] srcBuffer, int srcOffset, 
              byte[] dstBuffer, int dstOffset,
              int length)
    {
      System.arraycopy(srcBuffer, srcOffset, dstBuffer, dstOffset, length);
    }

    @Override
    int srcOffset(byte[] block, int rowOffset, PageServiceImpl table)
    {
      return BitsUtil.readInt16(block, rowOffset + _offset);
    }

    @Override
    int srcLength(byte[] block, int rowOffset, PageServiceImpl table)
    {
      return BitsUtil.readInt16(block, rowOffset + _offset + 2);
    }

    @Override
    int blobLength(byte[] block, int rowOffset, PageServiceImpl table)
    {
      return srcLength(block, rowOffset, table);
    }

    @Override
    Page srcPage(byte[] block, int rowOffset, PageServiceImpl table)
    {
      return null;
    }
  }
  
  private static class RowInBlobPage extends RowInSkeleton {
    private final int _offset;
    
    RowInBlobPage(RowInSkeleton next, int offset)
    {
      super(next);
      
      _offset = offset;
    }

    @Override
    void read(Page page,
              byte[] srcBuffer, int srcOffset, 
              byte[] dstBuffer, int dstOffset,
              int length)
    {
      PageBlob pageBlob = (PageBlob) page;
      
      while (length > 0) {
        int sublen = pageBlob.read(srcOffset, dstBuffer, dstOffset, length);
        
        if (sublen < 0) {
          throw new IllegalStateException(L.l("unexpected end of file blob {0} with offset {1}",
                                              pageBlob, srcOffset));
        }
        
        length -= sublen;
        srcOffset += sublen;
        dstOffset += sublen;
      }
    }

    @Override
    int srcOffset(byte[] block, int rowOffset, PageServiceImpl table)
    {
      // return BitsUtil.readInt16(block, rowOffset + _offset);
      return 0;
    }

    @Override
    int srcLength(byte[] block, int rowOffset, PageServiceImpl table)
    {
      Page page = srcPage(block, rowOffset, table);
      
      if (page != null) {
        return page.getSize();
      }
      else {
        return 0;
      }
    }

    @Override
    int blobLength(byte[] block, int rowOffset, PageServiceImpl table)
    {
      Page page = srcPage(block, rowOffset, table);
      
      if (page != null) {
        return page.getSize();
      }
      else {
        return 0;
      }
    }

    @Override
    Page srcPage(byte[] block, int rowOffset, PageServiceImpl table)
    {
      int offset = BitsUtil.readInt16(block, rowOffset + _offset);
      
      int pid = BitsUtil.readInt(block, offset);
      
      if (pid > 0) {
        return table.getPage(pid);
      }
      else {
        return null;
      }
    }
  }
  
  private static class RowInBlob extends RowInSkeleton {
    private int _offset;
    
    private RowInBlobInline _blobInline;
    private RowInBlobPage _blobPage;
    
    RowInBlob(RowInSkeleton next, int offset)
    {
      super(next);
      
      _offset = offset;
      
      _blobInline = new RowInBlobInline(next, offset);
      _blobPage = new RowInBlobPage(next, offset);
    }

    @Override
    void read(Page page,
              byte[] srcBuffer, int srcOffset, 
              byte[] dstBuffer, int dstOffset,
              int length)
    {
      throw new UnsupportedOperationException(getClass().getName());
    }

    @Override
    RowInSkeleton select(byte[] block, int rowOffset)
    {
      int length = BitsUtil.readInt16(block, rowOffset + _offset + 2);
      
      if ((length & ColumnBlob.LARGE_BLOB_MASK) == 0) {
        return _blobInline;
      }
      else {
        return _blobPage;
      }
    }

    @Override
    int srcOffset(byte[] block, int rowOffset, PageServiceImpl table)
    {
      throw new UnsupportedOperationException();
    }

    @Override
    int srcLength(byte[] block, int rowOffset, PageServiceImpl table)
    {
      throw new UnsupportedOperationException();
    }

    @Override
    Page srcPage(byte[] block, int rowOffset, PageServiceImpl table)
    {
      throw new UnsupportedOperationException();
    }

    @Override
    int blobLength(byte[] block, int rowOffset, PageServiceImpl table)
    {
      throw new UnsupportedOperationException();
    }
  }
}
