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
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

import com.caucho.v5.h3.InH3;
import com.caucho.v5.h3.OutFactoryH3;
import com.caucho.v5.h3.OutH3;
import com.caucho.v5.io.StreamSource;
import com.caucho.v5.kelp.Column.ColumnType;
import com.caucho.v5.util.Crc64;
import com.caucho.v5.util.Hex;
import com.caucho.v5.util.Utf8Util;
import com.caucho.v5.vfs.ReadStream;

import io.baratine.db.BlobReader;


/**
 * A row for the log store.
 */
public final class RowCursor
{
  private final TableKelp _table;
  private final Row _row;
  private final byte []_data;
  
  private final int _keyOffset;
  private final int _keyLength;
  
  private BlobOutputStream[] _blobs;
  private BlockLeaf _leafBlock;
  private int _leafRowOffset;
  
  //private SerializerFactory _serializer;
  private OutFactoryH3 _serializer;
  
  RowCursor(TableKelp table,
            Row logRow)
  {
    _table = table;
    _row = logRow;
    _data = new byte[logRow.getLength()];
    
    _keyOffset = logRow.getKeyOffset();
    _keyLength = logRow.getKeyLength();
  }
  
  public final byte []getBuffer()
  {
    return _data;
  }

  BlobOutputStream []getBlobs()
  {
    return _blobs;
  }

  Row getRow()
  {
    return _row;
  }

  int getKeyLength()
  {
    return _row.getKeyLength();
  }
  
  int getRemoveLength()
  {
    return _row.getRemoveLength();
  }

  int getTreeItemLength()
  {
    return _row.getTreeItemLength();
  }

  int getLength()
  {
    return _row.getLength();
  }

  /**
   * The size includes the dynamic size from any blobs
   */
  public int getSize()
  {
    int size = getLength();
    
    if (_blobs == null) {
      return size;
    }
    
    for (BlobOutputStream blob : _blobs) {
      if (blob != null) {
        size += blob.getSize();
      }
    }
    
    return size;
  }

  TableKelp getTable()
  {
    return _table;
  }

  DatabaseKelp getDatabase()
  {
    return getTable().getDatabase();
  }
  
  PageServiceSync getTableService()
  {
    return _table.getTableService();
  }
  
  void startGet()
  {
    Arrays.fill(_data, 0, _keyOffset, (byte) 0);
    
    int keyTail = _keyOffset + _keyLength;
    Arrays.fill(_data, keyTail, _data.length, (byte) 0);

    // generateBloom();
    // setVersion(state, 1);
  }
  
  public boolean isData()
  {
    int value = _data[0] & Page.CODE_MASK;
    
    return value == Page.INSERT;
  }

  public boolean isRemoved()
  {
    int value = _data[0] & Page.CODE_MASK;
    
    return value == Page.REMOVE;
  }

  
  public final int getState()
  {
    ColumnState colState = getColumnState();
    
    long value = colState.getLong(_data, 0);
    
    return (int) colState.getState(value);
  }
  
  /*
  public void setDataState()
  {
    //ColumnState colState = getColumnState();
    
    _data[0] |= 0x80;
  }
  */
  
  public long getStateTime()
  {
    return getColumnState().getTime(_data, 0);
  }
  
  public long getTime(byte []data, int offset)
  {
    return getColumnState().getTime(data, offset);
  }
  
  public long getStateTimeout()
  {
    return getColumnState().getTimeout(_data, 0);
  }
  
  public int getTimeoutBuffer(byte []data, int offset)
  {
    return getColumnState().getTimeout(data, offset);
  }
  
  public int getTimeout()
  {
    return getColumnState().getTimeout(_data, 0);
  }

  public final void setTimeout(int sec)
  {
    getColumnState().setTimeout(_data, 0, sec);
  }

  public long getVersion()
  {
    return getVersion(_data, 0);
  }

  public void setVersion(long version)
  {
    getColumnState().setVersion(_data, 0, version);
  }
  
  public long getVersion(byte []data, int offset)
  {
    return getColumnState().getVersion(data, offset);
  }
  
  private ColumnState getColumnState()
  {
    return (ColumnState) _row.getColumns()[0];
  }
  
  public void setRemoveState()
  {
    int stateTail = getColumnState().getLength();
    
    Arrays.fill(_data, stateTail, _keyOffset, (byte) 0);
    
    int keyTail = _keyOffset + _keyLength;
    Arrays.fill(_data, keyTail, _data.length, (byte) 0);

    //long state = ColumnState.STATE_REMOVED;
    // setVersion(state, 1);
  }

  public int getOffset(int index)
  {
    return _row.getColumns()[index].getOffset();
  }

  public final void setInt(int index, int value)
  {
    _row.getColumns()[index].setInt(_data, 0, value);
  }
  
  public final int getInt(int index)
  {
    return _row.getColumns()[index].getInt(_data, 0);
  }
  
  public final void setLong(int index, long value)
  {
    _row.getColumns()[index].setLong(_data, 0, value);
  }
  
  public final long getLong(int index)
  {
    return _row.getColumns()[index].getLong(_data, 0);
  }
  
  public final void setDouble(int index, double value)
  {
    _row.getColumns()[index].setDouble(_data, 0, value);
  }
  
  public final double getDouble(int index)
  {
    return _row.getColumns()[index].getDouble(_data, 0);
  }

  public final void setBytes(int index, byte[] buffer, int offset)
  {
    _row.getColumns()[index].setBytes(_data, 0, buffer, offset);
  }

  public void getBytes(int index, byte[] buffer, int offset)
  {
    _row.getColumns()[index].getBytes(_data, 0, buffer, offset);
  }

  public byte []getBytes(int index)
  {
    byte[] buffer = new byte[_row.getColumns()[index].getLength()];
    
    getBytes(index, buffer, 0);
    
    return buffer;
  }
  
  //
  // blobs
  //

  /**
   * Set a blob value with an open blob stream.
   */
  public final OutputStream openOutputStream(int index)
  {
    Column column = _row.getColumns()[index];
    
    return column.openOutputStream(this);
  }

  void setBlob(int index, BlobOutputStream os)
  {
    if (_blobs == null) {
      _blobs = new BlobOutputStream[_row.getColumns().length];
    }
    
    _blobs[index] = os;
  }
  
  void setLeafBlock(BlockLeaf leafBlock, int rowOffset)
  {
    _leafBlock = leafBlock;
    _leafRowOffset = rowOffset;
  }

  public final InputStream openInputStream(int index)
  {
    Column column = _row.getColumns()[index];

    BlockLeaf leaf = _leafBlock;

    if (leaf != null) {
      return column.openInputStream(this, _data, 0, leaf.getBuffer());
    }
    else {
      return null;
    }
  }

  public final BlobReader openBlobReader(int index)
  {
    Column column = _row.getColumns()[index];

    BlockLeaf leaf = _leafBlock;

    if (leaf != null) {
      return column.openBlobReader(this, _data, 0, leaf.getBuffer());
    }
    else {
      return null;
    }
  }
  
  public boolean isInputStreamAvailable(int index)
  {
    BlockLeaf blockLeaf = _leafBlock;
    
    return blockLeaf != null;
  }
  
  //
  // string
  //

  public void setString(int index, String value)
  {
    _row.getColumns()[index].setString(this, value);
  }

  public String getString(int index)
  {
    try (InputStream is = openInputStream(index)) {
      if (is == null) {
        // XXX: currently blob can't distinguish null values from empty string.
        return "";
      }
      
      return Utf8Util.readString(is);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  /*
  public void setSerializerFactory(SerializerFactory serializer)
  {
    _serializer = serializer;
  }
  */
  
  public void serializer(OutFactoryH3 serializer)
  {
    _serializer = serializer;
  }
  
  /*
  public SerializerFactory getSerializerFactory()
  {
    SerializerFactory serializer = _serializer;
    
    Objects.requireNonNull(serializer);
    if (serializer == null) {
      serializer = new SerializerFactory();
      serializer.setAllowNonSerializable(true);
    }
    
    return serializer;
  }
  */
  
  public OutFactoryH3 serializer()
  {
    OutFactoryH3 serializer = _serializer;
    
    Objects.requireNonNull(serializer);
    
    return serializer;
  }

  /*
  public void setObject(int index, Object value)
  {
    try (OutputStream os = openOutputStream(index)) {
      try (OutH3 out = getSerializerFactory().out(os)) {
      Hessian2Output hOut = new Hessian2Output(os);
      
      hOut.setSerializerFactory(getSerializerFactory());
      
      hOut.writeObject(value);
      
      hOut.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  */
  
  public void setObject(int index, Object value)
  {
    try (OutputStream os = openOutputStream(index)) {
      try (OutH3 out = serializer().out(os)) {
        out.writeObject(value);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /*
  public Object getObject(int index)
  {
    try (InputStream is = openInputStream(index)) {
      if (is == null) {
        return null;
      }
      
      Hessian2Input hIn = new Hessian2Input(is);
      
      return hIn.readObject();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  */
  
  public Object getObject(int index)
  {
    try (InputStream is = openInputStream(index)) {
      if (is == null) {
        return null;
      }
      
      try (InH3 in = serializer().in(is)) {
        return in.readObject();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (Exception e) {
      e.printStackTrace();;
      throw e;
    }
  }

  public final byte []getKey()
  {
    byte []key = new byte[_keyLength];
    
    getKey(key);
    
    return key;
  }

  public final void getKey(byte[] key)
  {
    System.arraycopy(_data, _keyOffset, key, 0, _keyLength);
  }

  public final void getKey(byte[] buffer, int offset)
  {
    System.arraycopy(_data, _keyOffset, buffer, offset, _keyLength);
  }

  public final void setKey(byte[] buffer, int offset)
  {
    System.arraycopy(buffer, offset, _data, _keyOffset, _keyLength);
  }
  
  public final void getRemove(byte []rowBuffer, int rowOffset)
  {
    // copy the state, including the timestamp
    System.arraycopy(_data, 0, rowBuffer, rowOffset, ColumnState.LENGTH);

    // set the remove code
    rowBuffer[rowOffset]
        = (byte) ((rowBuffer[rowOffset] & ~Page.CODE_MASK) | Page.REMOVE);

    int keyOffset = rowOffset + ColumnState.LENGTH;
    
    // copy the key
    System.arraycopy(_data, _keyOffset, rowBuffer, keyOffset, _keyLength);
  }
  
  public final void setRemove(byte []rowBuffer, int rowOffset)
  {
    // copy the state, including the timestamp
    System.arraycopy(rowBuffer, rowOffset, 
                     _data, 0, 
                     ColumnState.LENGTH);
    
    int keyOffset = rowOffset + ColumnState.LENGTH;

    // copy the key
    System.arraycopy(rowBuffer, keyOffset,
                     _data, _keyOffset,
                     _keyLength);
  }

  public void incrementKey()
  {
    for (int i = _keyLength - 1; i >= 0; i--) {
      int data = _data[_keyOffset + i] & 0xff;
      
      _data[_keyOffset + i] = (byte) (data + 1);
      
      if (data != 0xff) {
        return;
      }
    }
  }

  public void setKey(RowCursor cursor)
  {
    System.arraycopy(cursor._data, _keyOffset, _data, _keyOffset, _keyLength);
  }

  public int compareKey(byte[] buffer, int offset)
  {
    KeyComparator keyComp = KeyComparator.INSTANCE;

    return keyComp.compare(_data, _keyOffset,
                           buffer, offset,
                           _keyLength);
  }

  public int compareKeyRemove(byte[] rowBuffer, int rowOffset)
  {
    KeyComparator keyComp = KeyComparator.INSTANCE;

    int keyOffset = ColumnState.LENGTH;
    
    return keyComp.compare(_data, keyOffset,
                           rowBuffer, rowOffset + keyOffset,
                           _keyLength);
  }

  public int compareKeyRow(byte[] rowBuffer, int rowOffset)
  {
    KeyComparator keyComp = KeyComparator.INSTANCE;
  
    return keyComp.compare(_data, _keyOffset,
                           rowBuffer, rowOffset + _keyOffset,
                           _keyLength);
  }

  public long generateKeyHash()
  {
    // arbitrary seed
    // long seed = 0x1539_bcde_6543_0314L;
    long seed = 0;
    
    long hash = Crc64.generate(seed, _data, _keyOffset, _keyLength);
    
    // hash = hash ^ (hash >> (_db.getBloomBits() + 3));
    
    hash = hash ^ Long.reverse(hash);
    
    return hash;
  }

  public final void setRow(byte[] buffer, int rowOffset)
  {
    int code = buffer[rowOffset];

    if (isInsert(code)) {
      System.arraycopy(buffer, rowOffset, _data, 0, _data.length);
    }
    else if (isRemove(code)) {
      System.arraycopy(buffer, rowOffset, _data, 0, getRemoveLength());
    }
    else {
      System.arraycopy(buffer, rowOffset, _data, 0, _data.length);
    }
  }
  
  private boolean isInsert(int code)
  {
    return (code & Page.CODE_MASK) == Page.INSERT;
  }
  
  private boolean isRemove(int code)
  {
    return (code & Page.CODE_MASK) == Page.REMOVE;
  }

  public final void fillRow(byte[] buffer, int rowOffset)
  {
    int code = buffer[rowOffset];
    
    if ((code & Page.CODE_MASK) == Page.REMOVE) {
      int stateLength = getColumnState().getLength();
      int keyOffset = getRow().getKeyOffset();
      int keyLength = getKeyLength();
      
      System.arraycopy(buffer, rowOffset, _data, 0, stateLength);
      System.arraycopy(buffer, rowOffset + stateLength, _data, keyOffset, keyLength);
      
      int keyTail = keyOffset + keyLength;
      Arrays.fill(_data, keyTail, _data.length, (byte) 0);
    }
    else {
      System.arraycopy(buffer, rowOffset, _data, 0, _data.length);
    }
  }

  public void setRow(ByteBuffer workBuffer, int address)
  {
    workBuffer.position(address);
    workBuffer.get(_data, 0, _data.length);
  }

  public final void getRow(byte[] buffer, int rowOffset)
  {
    System.arraycopy(_data, 0, buffer, rowOffset, _data.length);
  }
  
  final void writeJournal(OutputStream os)
    throws IOException
  {
    _row.writeJournal(os, _data, 0, _blobs);
  }

  // read journal
  void readJournal(PageServiceImpl pageActor, ReadStream is)
    throws IOException
  {
    _row.readJournal(pageActor, is, _data, 0, this);
  }
  
  //
  // stream for persistence and distributed replication.

  /**
   * Reads into the cursor from an input stream
   * 
   * @param is the InputStream containing the serialized data
   */
  public void readStream(InputStream is)
    throws IOException
  {
    _row.readStream(is, _data, 0, this);
  }

  void setBlob(ColumnBlob column, int newTail, int offset)
  {
    column.setBlob(_data, 0, newTail, offset);
  }

  public void getRow(ByteBuffer workBuffer, int address)
  {
    workBuffer.position(address);
    workBuffer.put(_data, 0, _data.length);
  }

  public final void copyFrom(RowCursor row)
  {
    System.arraycopy(row._data, 0, _data, 0, _data.length);
  }
  
  public int compareTo(RowCursor cursor)
  {
    KeyComparator keyComp = KeyComparator.INSTANCE;
    
    return keyComp.compare(_data, _keyOffset, 
                           cursor._data, _keyOffset,
                           _keyLength);
  }

  ColumnBlob getInodeColumn(int i)
  {
    Column []columns = _row.getColumns();
    
    for (; i < columns.length; i++) {
      Column column = columns[i];
      
      if (column.getType() == ColumnType.BLOB) {
        return (ColumnBlob) column;
      }
    }
    
    return null;
  }
  
  public void clear()
  {
    Arrays.fill(_data, 0, _data.length, (byte) 0);
  }

  public void setKeyMax()
  {
    Arrays.fill(_data, _keyOffset, _keyOffset + _keyLength, (byte) 0xff);
  }

  public void freeBlobs()
  {
    BlobOutputStream[] blobs = _blobs;
    
    _blobs = null;
    
    if (blobs != null) {
      for (BlobOutputStream blob : blobs) {
        if (blob != null) {
          blob.free();
        }
      }
    }
  }

  public void removeBlobs()
  {
    BlobOutputStream[] blobs = _blobs;
    
    _blobs = null;
    
    if (blobs != null) {
      for (BlobOutputStream blob : blobs) {
        if (blob != null) {
          blob.remove();
        }
      }
    }
  }
  
  public StreamSource toStream()
  {
    BlockLeaf blockLeaf = _leafBlock;
    
    byte []block = blockLeaf.getBuffer();

    return _table.getTableServiceImpl().toStream(block, _leafRowOffset, block);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + Hex.toShortHex(_data, _keyOffset, _keyLength) + "]";
  }
}
