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
import java.io.OutputStream;
import java.util.ArrayList;

import com.caucho.v5.baratine.InService;
import com.caucho.v5.io.ReadBuffer;
import com.caucho.v5.io.WriteBuffer;

/**
 * A row for the log store.
 */
public final class Row {
  private final DatabaseKelp _db;
  private final Column []_columns;
  private final Column []_blobs;
  private final int _length;
  
  private final int _keyStart;
  private final int _keyLength;
  
  private final RowInSkeleton _inSkeleton;
  
  Row(DatabaseKelp db,
      Column []columns, 
      Column []blobs,
      int keyStart, 
      int keyLength)
  {
    _db = db;
    
    _columns = columns;
    _blobs = blobs;
    
    int length = 0;
    for (int i = 0; i < columns.length; i++) {
      length += columns[i].length();
    }
    
    _length = length;
    
    _keyStart = keyStart;
    _keyLength = keyLength;
    
    _inSkeleton = RowInSkeleton.build(columns);
  }
  
  public DatabaseKelp getDatabase()
  {
    return _db;
  }
  
  public final Column []getColumns()
  {
    return _columns;
  }
  
  public final Column []getBlobs()
  {
    return _blobs;
  }
  
  public final ArrayList<Column> getKeys()
  {
    ArrayList<Column> keys = new ArrayList<>();
    
    for (int i = 0; i < _columns.length; i++) {
      Column col = _columns[i];
      
      int offset = col.getOffset();
      
      if (_keyStart <= offset && offset < _keyStart + _keyLength) {
        keys.add(col);
      }
    }
    
    return keys;
  }
  
  public int getLength()
  {
    return _length;
  }
  
  public int getKeyOffset()
  {
    return _keyStart;
  }
  
  public int getKeyLength()
  {
    return _keyLength;
  }
  
  public int getRemoveLength()
  {
    return _keyLength + ColumnState.LENGTH;
  }
  
  public int getTreeItemLength()
  {
    return 1 + 2 * getKeyLength() + 4;
  }
  
  public ColumnState getColumnState()
  {
    return (ColumnState) _columns[0];
  }

  public Column findColumn(String name)
  {
    for (Column column : getColumns()) {
      if (column.name().equals(name)) {
        return column;
      }
    }

    return null;
  }

  public RowInSkeleton getInSkeleton()
  {
    return _inSkeleton;
  }
  
  //
  // lifecycle
  //
  
  void init(DatabaseKelp db)
  {
    for (Column column : getColumns()) {
      column.init(db);
    }
  }
  
  //
  // memory insert, copy, remove
  //

  int insertBlobs(byte[] buffer, int rowFirst, int blobLast,
                  BlobOutputStream[] blobs)
  {
    if (blobs == null) {
      return blobLast;
    }
    
    Column []columns = getColumns();
    int len = columns.length;
    
    for (int i = 0; i < len && blobLast >= 0; i++) {
      BlobOutputStream blob = blobs[i];
      
      if (blob != null) {
        blobLast = blob.writeTail(columns[i], buffer, rowFirst, blobLast);
      }
    }
    
    /*
    for (BlobOutputStream blob : blobs) {
      if (blob != null) {
        blob.free();
      }
    }
    */
    
    return blobLast;
  }

  int copyBlobs(byte []sourceBuffer,
                int sourceRowOffset,
                byte []targetBuffer,
                int targetRowOffset,
                int targetBlobTail)
  {
    for (Column column : getBlobs()) {
      targetBlobTail = column.insertBlob(sourceBuffer, sourceRowOffset,
                                         targetBuffer, targetRowOffset, 
                                         targetBlobTail);
      
      if (targetBlobTail < 0) {
        return targetBlobTail;
      }
    }
    
    return targetBlobTail;
  }

  @InService(PageServiceImpl.class)
  void remove(PageServiceImpl pageActor, byte[] buffer, int rowOffset)
  {
    for (Column column : getColumns()) {
      column.remove(pageActor, buffer, rowOffset);
    }
  }
  
  //
  // journal persistence
  //

  void writeJournal(OutputStream os, byte[] buffer, int offset,
                    BlobOutputStream[] blobs)
    throws IOException
  {
    Column []columns = getColumns();
    int len = columns.length;
    
    for (int i = 0; i < len; i++) {
      BlobOutputStream blob = blobs != null ? blobs[i] : null;
      
      columns[i].writeJournal(os, buffer, offset, blob);
    }
  }

  void readJournal(PageServiceImpl pageActor, 
                   ReadBuffer is, 
                   byte[] buffer, int offset,
                   RowCursor cursor)
    throws IOException
  {
    for (Column column : getColumns()) {
      column.readJournal(pageActor, is, buffer, offset, cursor);
    }
  }

  /**
   * Fills a cursor given an input stream
   * 
   * @param is stream containing the serialized row 
   * @param buffer the cursor's data buffer for the fixed part of the stream
   * @param offset the cursor's offset for the fixed part of the stream
   * @param cursor the cursor itself for holding the blob
   * @throws IOException
   */
  void readStream(InputStream is, 
                  byte[] buffer, int offset,
                  RowCursor cursor)
    throws IOException
  {
    for (Column column : getColumns()) {
      column.readStream(is, buffer, offset, cursor);
    }
    
    for (Column column : getBlobs()) {
      column.readStreamBlob(is, buffer, offset, cursor);
    }
  }

  void writeStream(OutputStream os, byte[] buffer, int offset,
                   byte[] blobBuffer, PageServiceImpl tableService)
    throws IOException
  {
    for (Column column : getColumns()) {
      column.writeStream(os, buffer, offset);
    }
    
    for (Column column : getBlobs()) {
      column.writeStreamBlob(os, buffer, offset, blobBuffer, tableService);
    }
  }

  public long getLength(byte[] buffer, int rowOffset, PageServiceImpl pageService)
  {
    long length = 0;
    
    for (Column column : getColumns()) {
      length = column.getLength(length, buffer, rowOffset, pageService);
    }
    
    return length;
  }
  
  public InputStream openInputStream(byte []buffer, 
                                     int rowOffset, 
                                     PageServiceImpl tableService)
  {
    return new RowInputStream(_inSkeleton, buffer, rowOffset, tableService);
  }
  
  //
  // checkpoint persistence
  //

  void writeCheckpoint(WriteBuffer os, byte[] buffer, int offset)
    throws IOException
  {
    for (Column column : getColumns()) {
      column.writeCheckpoint(os, buffer, offset);
    }
  }

  /**
   * Reads column-specific data like blobs from the checkpoint.
   * 
   * Returns -1 if the data does not fit into the current block.
   */
  int readCheckpoint(ReadBuffer is, 
                     byte[] blockBuffer,
                     int rowOffset,
                     int blobTail)
    throws IOException
  {
    int rowLength = getLength();
    
    if (rowOffset < blobTail) {
      return -1;
    }
    
    for (Column column : getColumns()) {
      blobTail = column.readCheckpoint(is, 
                                       blockBuffer, 
                                       rowOffset, rowLength, 
                                       blobTail);
    }
    
    return blobTail;
  }

  /**
   * Validates the row, checking for corruption.
   */
  public void validate(byte[] buffer, int rowOffset, int rowHead, int blobTail)
  {
    for (Column column : getColumns()) {
      column.validate(buffer, rowOffset, rowHead, blobTail);
    }
  }

  public static int compareKey(byte[] keyA, byte []keyB)
  {
    KeyComparator keyComp = KeyComparator.INSTANCE;

    return keyComp.compare(keyA, 0, keyB, 0, keyA.length);
  }

  public static int compareKey(byte[] keyA, int offsetA, 
                               byte []keyB, int offsetB,
                               int keyLength)
  {
    KeyComparator keyComp = KeyComparator.INSTANCE;

    return keyComp.compare(keyA, offsetA, keyB, offsetB, keyLength);
  }

  /**
   * Increment a key.
   */
  static void incrementKey(byte[] key)
  {
    for (int i = key.length - 1; i >= 0; i--) {
      int v = key[i] & 0xff;
      
      if (v < 0xff) {
        key[i] = (byte) (v + 1);
        return;
      }
      
      key[i] = 0;
    }
  }

  /**
   * Decrement a key.
   */
  static void decrementKey(byte[] key)
  {
    for (int i = key.length - 1; i >= 0; i--) {
      int v = key[i] & 0xff;
      
      if (v > 0) {
        key[i] = (byte) (v - 1);
        return;
      }
      
      key[i] = (byte) 0xff;
    }
  }
  
  static byte []copyKey(byte []key)
  {
    byte []newKey = new byte[key.length];
    
    System.arraycopy(key, 0, newKey, 0, key.length);
    
    return newKey;
  }
}
