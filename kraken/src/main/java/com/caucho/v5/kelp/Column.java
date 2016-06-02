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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.caucho.v5.io.IoUtil;
import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.WriteStream;
import com.caucho.v5.util.BitsUtil;
import com.caucho.v5.util.Crc64;

import io.baratine.db.BlobReader;

/**
 * A column for the log store.
 */
abstract public class Column
{
  private final int _index;
  private final String _name;
  private final ColumnType _type;
  private final int _offset;
  
  protected Column(int index,
                   String name, 
                   ColumnType type,
                   int offset)
  {
    _index = index;
    _name = name;
    _type = type;
    _offset = offset;
  }

  /**
   * name of the column
   */
  public final String name()
  {
    return _name;
  }
  
  /**
   * index of the column in the row.
   */
  public final int index()
  {
    return _index;
  }

  /**
   * type of the column (INT8, INT32, STRING, etc.)
   */
  public ColumnType type()
  {
    return _type;
  }
  
  public int size()
  {
    return 0;
  }
  
  /**
   * offset of the column data in the fixed byte buffer
   */
  public final int offset()
  {
    return _offset;
  }
  
  /**
   * fixed length of the column data in the fixed byte buffer
   */
  abstract public int length();
  
  //
  // lifecycle
  //
  
  void init(DatabaseKelp db)
  {
  }
  
  //
  // operations
  //

  public int getInt(byte[] data, int i)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void setInt(byte[] data, int i, int value)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void setLong(byte[] data, int i, long value)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public long getLong(byte[] data, int i)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void setDouble(byte[] data, int i, double value)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public double getDouble(byte[] data, int i)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void setFloat(byte[] data, int i, float value)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public float getFloat(byte[] data, int i)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void setBytes(byte[] data, int i, byte[] buffer, int offset)
  {
  }

  public void getBytes(byte[] data, int i, byte[] buffer, int offset)
  {
  }
  
  /**
   * fill the buffer data for columns with automatic values, like identity 
   */
  public void autoFill(byte[] buffer, int offset)
  {
  }

  public void buildHash(StringBuilder sb, byte[] buffer, int offset)
  {
  }
  
  public long crc64(long crc64)
  {
    crc64 = Crc64.generate(crc64, type().name());
    crc64 = Crc64.generate(crc64, name());
    crc64 = Crc64.generate(crc64, offset());
    crc64 = Crc64.generate(crc64, length());
    
    return crc64;
  }
  
  //
  // blobs
  //
  
  public OutputStream openOutputStream(RowCursor cursor)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public InputStream openInputStream(RowCursor cursor,
                                     byte []rowBuffer,
                                     int rowOffset,
                                     byte []pageBuffer)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public BlobReader openBlobReader(RowCursor cursor,
                                    byte []rowBuffer,
                                    int rowOffset,
                                    byte []pageBuffer)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  public void setString(RowCursor cursor, String value)
  {
    throw new UnsupportedOperationException(toString());
  }

  public void remove(PageServiceImpl pageActor, byte[] buffer, int rowOffset)
  {
  }

  void freeBlocks(byte []rowBuffer, int rowOffset)
  {
  }

  int insertBlob(byte[] sourceBuffer, int sourceRowOffset,
                 byte[] targetBuffer, int targetRowOffset, 
                 int targetBlobTail)
  {
    return targetBlobTail;
  }
  
  void setBlob(byte []buffer, int rowFirst,
               int blobOffset, int blobLength)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  //
  // journal persistence
  //

  void writeJournal(OutputStream os, byte[] buffer, int offset,
                    BlobOutputStream blob)
    throws IOException
  {
    os.write(buffer, offset + offset(), length());
  }

  void readJournal(PageServiceImpl pageActor, 
                   ReadStream is, 
                   byte[] buffer, int offset, RowCursor cursor)
    throws IOException
  {
    is.readAll(buffer, offset + offset(), length());
  }

  void readStream(InputStream is, 
                  byte[] buffer, int offset, RowCursor cursor)
    throws IOException
  {
    IoUtil.readAll(is, buffer, offset + offset(), length());
  }

  void readStreamBlob(InputStream is, 
                      byte[] buffer, int offset, RowCursor cursor)
    throws IOException
  {
  }
  
  protected void readAll(InputStream is,
                        byte []buffer, int offset, int length)
    throws IOException
  {
    while (length > 0) {
      int sublen = is.read(buffer, offset, length);
      
      if (sublen <= 0) {
        return;
      }
      
      offset += sublen;
      length -= sublen;
    }
  }

  void writeStream(OutputStream os, byte[] buffer, int offset)
    throws IOException
  {
    os.write(buffer, offset + offset(), length());
  }

  void writeStreamBlob(OutputStream os, byte[] buffer, int offset,
                       byte[] blobBuffer,
                       PageServiceImpl tableService)
    throws IOException
  {
  }

  public long getLength(long length, byte[] buffer, int rowOffset,
                        PageServiceImpl pageService)
  {
    return length + length();
  }
  
  //
  // checkpoint persistence
  //

  void writeCheckpoint(WriteStream os, byte[] buffer, int offset)
    throws IOException
  {
    os.write(buffer, offset + offset(), length());
  }
  
  int readCheckpoint(ReadStream is, 
                     byte[] buffer, int rowFirst, int rowLength,
                     int blobTail)
    throws IOException
  {
    is.readAll(buffer, rowFirst + offset(), length());

    return blobTail;
  }
  
  //
  // validation
  //

  /**
   * Validates the column, checking for corruption.
   */
  public void validate(byte[] buffer, int rowOffset, int rowHead, int blobTail)
  {
  }

  public void toData(OutputStream os)
    throws IOException
  {
    BitsUtil.writeInt16(os, type().ordinal());
    BitsUtil.writeInt16(os, length());
    byte []name = name().getBytes("UTF-8");
    BitsUtil.writeInt16(os, name.length);
    os.write(name);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + name() + ",offset=" + offset() + "]";
  }
  
  public enum ColumnType {
    STATE,
    KEY_START, // used for serialization
    KEY_END,
    BOOL,
    INT8,
    INT16,
    INT32,
    INT64,
    FLOAT,
    DOUBLE,
    TIMESTAMP,
    IDENTITY,
    BYTES,
    
    BLOB { // variable length data
      @Override
      public boolean isBlob() { return true; }
    },
    STRING {
      @Override
      public boolean isBlob() { return true; }
    },
    OBJECT {
      @Override
      public boolean isBlob() { return true; }
    },
    ; 
    
    public boolean isBlob()
    {
      return false;
    }
  }
}
