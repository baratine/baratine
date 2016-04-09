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

import java.util.ArrayList;
import java.util.Objects;

import com.caucho.v5.util.L10N;

/**
 * A row for the log store.
 */
public class RowBuilder {
  private static final L10N L = new L10N(RowBuilder.class);
  
  private final ArrayList<Column> _columns = new ArrayList<>();
  private int _keyStart;
  private int _keyLength;
  private int _offset;
  
  public RowBuilder()
  {
    ColumnState stateColumn = new ColumnState(0, "_row_state", 0);
    
    _offset += stateColumn.length();
    _columns.add(stateColumn);
  }
  
  public RowBuilder startKey()
  {
    if (_keyStart > 0) {
      throw new IllegalStateException(L.l("key has already started"));
    }
    
    _keyStart = _offset;
    
    return this;
  }
  
  public RowBuilder endKey()
  {
    if (_keyStart <= 0) {
      throw new IllegalStateException(L.l("key has not been started"));
    }
    
    if (_keyLength > 0) {
      throw new IllegalStateException(L.l("key has already ended"));
    }
    
    if (_keyStart == _offset) {
      throw new IllegalStateException(L.l("key has zero length"));
    }
    
    _keyLength = _offset - _keyStart;
    
    return this;
  }
  
  public RowBuilder int8(String name)
  {
    ColumnInt8 column = new ColumnInt8(_columns.size(), name, _offset);
    
    _offset += column.length();
    _columns.add(column);
    
    return this;
  }

  /**
   * Creates a 16-bit integer valued column
   * 
   * @param name the column name
   */
  public RowBuilder int16(String name)
  {
    ColumnInt16 column = new ColumnInt16(_columns.size(), name, _offset);
    
    _offset += column.length();
    _columns.add(column);
    
    return this;
  }

  /**
   * Creates a 32-bit integer valued column
   * 
   * @param name the column name
   */
  public RowBuilder int32(String name)
  {
    ColumnInt32 column = new ColumnInt32(_columns.size(), name, _offset);
    
    _offset += column.length();
    _columns.add(column);
    
    return this;
  }

  /**
   * Creates a 64-bit long valued column
   * 
   * @param name the column name
   */
  public RowBuilder int64(String name)
  {
    ColumnInt64 column = new ColumnInt64(_columns.size(), name, _offset);
    
    _offset += column.length();
    _columns.add(column);
    
    return this;
  }
  
  /**
   * Creates a double valued column.
   * 
   * @param name the column name
   */
  public RowBuilder doubleCol(String name)
  {
    ColumnDouble column = new ColumnDouble(_columns.size(), name, _offset);
    
    _offset += column.length();
    _columns.add(column);
    
    return this;
  }
  
  public RowBuilder bytes(String name, int length)
  {
    ColumnBytes column = new ColumnBytes(_columns.size(), name, 
                                         _offset, length);
    
    _offset += column.length();
    _columns.add(column);
    
    return this;
  }
  
  public RowBuilder blob(String name)
  {
    ColumnBlob column = new ColumnBlob(_columns.size(), name, _offset);
    
    _offset += column.length();
    _columns.add(column);
    
    return this;
  }
  
  public RowBuilder string(String name)
  {
    ColumnString column = new ColumnString(_columns.size(), name, _offset);
    
    _offset += column.length();
    _columns.add(column);
    
    return this;
  }
  
  public RowBuilder object(String name)
  {
    ColumnObject column = new ColumnObject(_columns.size(), name, _offset);
    
    _offset += column.length();
    _columns.add(column);
    
    return this;
  }
  
  public Row build(DatabaseKelp db)
  {
    Objects.requireNonNull(db);
    
    if (_keyLength <= 0) {
      throw new IllegalStateException(L.l("key has not been defined"));
    }
    
    Column []columns = new Column[_columns.size()];
    _columns.toArray(columns);
    
    Column []blobs = new Column[countBlobs()];
    
    int index = 0;
    for (int i = 0; i < _columns.size(); i++) {
      if (_columns.get(i).type().isBlob()) {
        blobs[index++] = _columns.get(i);
      }
    }
    
    return new Row(db, columns, blobs, _keyStart, _keyLength);
  }
  
  private int countBlobs()
  {
    int count = 0;
    
    for (int i = 0; i < _columns.size(); i++) {
      if (_columns.get(i).type().isBlob()) {
        count++;
      }
    }
    
    return count;
  }
}
