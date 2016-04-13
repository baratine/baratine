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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.caucho.v5.io.StreamSource;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.store.temp.TempWriter;
import com.caucho.v5.util.BitsUtil;
import com.caucho.v5.util.Hex;
import com.caucho.v5.util.L10N;

class BlobOutputStream extends OutputStream
{
  private static final L10N L = new L10N(BlobOutputStream.class);
  
  final int BLOCK_SIZE = 8192;
  final int LARGE_BLOB_MASK = 0x8000;
  
  private final TableKelp _table;
  
  private TempBuffer _tempBuffer;
  private byte []_buffer;
  private int _offset;
  private int _bufferEnd;

  private final RowCursor _cursor;
  private final ColumnBlob _column;
  
  //private PageBlob _blobPage;
  //private long _blobLength;
  //private int _blobId;
  
  private boolean _isLargeBlob;
  private TempWriter _blobOut;
  
  private boolean _isClosed;
  private int _blobId;
  
  /**
   * Creates a blob output stream.
   *
   * @param store the output store
   */
  public BlobOutputStream(RowCursor cursor, 
                          ColumnBlob column)
  {
    _table = cursor.getTable();
    
    _cursor = cursor;
    _column = column;
    
    init();
  }
  
  BlobOutputStream(RowCursor cursor, 
                   ColumnBlob column,
                   TempBuffer tempBuffer,
                   int length)
  {
    _table = cursor.getTable();
    
    _cursor = cursor;
    _column = column;
    
    _tempBuffer = tempBuffer;
    _buffer = tempBuffer.buffer();
    _offset = length;
    _bufferEnd = _buffer.length;
  }

  /**
   * Called from journal to replay the blob id.
  */
  BlobOutputStream(RowCursor cursor, 
                   ColumnBlob column,
                   int blobId)
  {
    _table = cursor.getTable();
    
    _cursor = cursor;
    _column = column;

    // _blobPage = _db.getPageService().getBlobPage((int) blobId);
    _blobId = blobId;
    _isLargeBlob = true;
    /*
    if (_blobPage == null) {
      throw new IllegalStateException(L.l("Blob {0} is undefined", blobId));
    }
    */
  }

  /**
   * Initialize the output stream.
   */
  public void init()
  {
    _offset = 0;

    if (_tempBuffer == null) {
      _tempBuffer = TempBuffer.create();
      _buffer = _tempBuffer.buffer();
      _bufferEnd = _buffer.length;
    }
  }

  int getSize()
  {
    if (_blobOut != null) {
      return _blobOut.getLength();
    }
    else {
      return _offset;
    }
  }
  
  boolean isLargeBlob()
  {
    return _isLargeBlob;
  }
  
  void flushBlob()
  {
    if (! _isClosed) {
      throw new IllegalStateException();
    }
    
    if (_blobOut != null) {
      // _blobPage.write
      
      /* XXX:
      _blobPage.clearDirty();
    
      _table.getReadWrite().writeBlobPage((PageBlobImpl) _blobPage, 
                                       _blobPage.nextSaveSequence());
                                       */
    }
  }
  
  
  int getBlobId()
  {
    return _blobId;
    /*
    if (_blobId > 0) {
      return _blobId;
    }
    else {
      return _blobPage.getId();
    }
    */
  }

  /**
   * Writes a byte.
   */
  @Override
  public void write(int v)
    throws IOException
  {
    if (_bufferEnd <= _offset) {
      flushBlock(false);
    }

    _buffer[_offset++] = (byte) v;
  }

  /**
   * Writes a buffer.
   */
  @Override
  public void write(byte []buffer, int offset, int length)
    throws IOException
  {
    while (length > 0) {
      if (_bufferEnd <= _offset) {
        flushBlock(false);
      }

      int sublen = Math.min(_bufferEnd - _offset, length);

      System.arraycopy(buffer, offset, _buffer, _offset, sublen);

      offset += sublen;
      _offset += sublen;

      length -= sublen;
    }
  }

  public void writeFromStream(InputStream is)
    throws IOException
  {
    while (true) {
      if (_bufferEnd <= _offset) {
        flushBlock(false);
      }

      int sublen = _bufferEnd - _offset;

      sublen = is.read(_buffer, _offset, sublen);

      if (sublen <= 0) {
        return;
      }

      _offset += sublen;
    }
  }

  public void writeFromStream(InputStream is, int length)
    throws IOException
  {
    int len = length;
    
    while (len > 0) {
      if (_bufferEnd <= _offset) {
        flushBlock(false);
      }

      int sublen = Math.min(len , _bufferEnd - _offset);

      sublen = is.read(_buffer, _offset, sublen);

      if (sublen <= 0) {
        throw new EOFException(L.l("Unexpected end of file in {0} while reading {1} expected length {2}",
                                   this, is, length));
      }

      len -= sublen;
      _offset += sublen;
    }
  }

  /**
   * Completes the stream.
   */
  @Override
  public void close()
    throws IOException
  {
    if (_isClosed) {
      return;
    }
    
    _isClosed = true;
    
    flushBlock(true);
      
    _cursor.setBlob(_column.getIndex(), this);
  }

  /**
   * flushes a block of the blob to the temp store if larger than
   * the inline size.
   */
  private void flushBlock(boolean isClose)
    throws IOException
  {
    if (isClose) {
      if (! _isLargeBlob && _offset <= _table.getInlineBlobMax()) {
        return;
      }
    }

    _isLargeBlob = true;
    
    if (_blobOut == null) {
      _blobOut = _table.getTempStore().openWriter();
    }
    
    if (_offset != _bufferEnd && ! isClose) {
      throw new IllegalStateException();
    }
    
    _blobOut.write(_tempBuffer.buffer(), 0, _offset);
    _offset = 0;
    
    if (! isClose) {
      return;
    }
    
    StreamSource ss = _blobOut.getStreamSource();
    _blobOut = null;
      
    int len = (int) ss.getLength();
    int blobPageSizeMax = _table.getBlobPageSizeMax();

    int pid = -1;
    int nextPid = -1;
    int tailLen = len % blobPageSizeMax;
    // long seq = 0;

    PageServiceSync tableService = _table.getTableService();
    
    if (tailLen > 0) {
      int tailOffset = len - tailLen;
      
      pid = tableService.writeBlob(pid, ss.openChild(), 
                                  tailOffset, tailLen);
      
      len -= tailLen;
    }
      
    while (len > 0) {
      int sublen = blobPageSizeMax;
      int offset = len - blobPageSizeMax;
      
      pid = tableService.writeBlob(pid, ss.openChild(), offset, sublen);
      
      len -= sublen;
    }
      
    ss.close();
    
    _blobId = Math.max(pid, 0);
  }

  private int writeTail(RowCursor rowCursor, byte[] buffer, int offset, int blobTail)
  {
    TempBuffer tempBuffer = _tempBuffer;
    
    if (tempBuffer == null) {
      return blobTail;
    }
    
    int newTail = blobTail - _offset;

    System.arraycopy(tempBuffer.buffer(), 0, buffer, newTail, _offset);
    
    rowCursor.setBlob(_column, newTail, _offset);
    
    return newTail;
  }

  int writeTail(Column column, byte[] buffer, int rowFirst, int blobTail)
  {
    if (_isLargeBlob) {
      return writeLargeBlobTail(column, buffer, rowFirst, blobTail);
    }
    
    TempBuffer tempBuffer = _tempBuffer;
    
    if (tempBuffer == null) {
      return blobTail;
    }
    
    int newTail = blobTail + _offset;
    
    if (rowFirst < newTail) {
      return -1;
    }

    System.arraycopy(tempBuffer.buffer(), 0, buffer, blobTail, _offset);
    
    column.setBlob(buffer, rowFirst, blobTail, _offset);

    return newTail;
  }
  
  int writeLargeBlobTail(Column column, 
                         byte[] buffer, int rowFirst, int blobTail)
  {
    int newTail = blobTail + 4;
    
    if (rowFirst < newTail) {
      return -1;
    }

    BitsUtil.writeInt(buffer, blobTail, getBlobId());

    column.setBlob(buffer, rowFirst, blobTail, LARGE_BLOB_MASK | 4);
    
    /*
    if (_blobId > 0) {
      _table.getTableService().setBlobPage((PageBlobImpl) _blobPage);
    }
    */

    return newTail;
  }

  void writeToStream(OutputStream os)
    throws IOException
  {
    if (_blobOut != null) {
      System.out.println("BLOB-WRITE-TO_STREAM: " + os);
      // _blobOut.writeToStream(os);
    }
    
    os.write(_tempBuffer.buffer(), 0, _offset);
  }
  
  void remove()
  {
    int blobId = _blobId;
    _blobId = -1;
    
    free();
    
    removeBlobs(blobId);
  }
  
  void free()
  {
    _blobId = -1;
    
    TempBuffer tBuf = _tempBuffer;
    
    _tempBuffer = null;
    
    if (tBuf != null) {
      tBuf.freeSelf();
    }
  }
  
  private void removeBlobs(int blobId)
  {
    PageServiceSync tableService = _table.getTableService();
    
    while (blobId > 0) {
      PageBlob blob = tableService.getBlobPage(blobId);
      
      int nextId = blob.getNextId();
      
      tableService.freeBlob(blobId);
      
      blobId = nextId;
    }
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + _column.name() + "," + _cursor + "]");
  }
}
