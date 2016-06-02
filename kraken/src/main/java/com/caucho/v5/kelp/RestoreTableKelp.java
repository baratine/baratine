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
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.caucho.v5.h3.InH3;
import com.caucho.v5.io.IoUtil;
import com.caucho.v5.util.L10N;

import io.baratine.service.Result;

/**
 * Restores a kelp table from tha parsed archive.
 */
public class RestoreTableKelp extends RestoreTableParser
{
  private static final L10N L = new L10N(RestoreTableKelp.class);
  
  private static final int STATE_END = ArchiveTableKelp.STATE_END;
  private static final int STATE_DATA = ArchiveTableKelp.STATE_DATA;
  private static final int STATE_REMOVED = ArchiveTableKelp.STATE_REMOVED;
  
  private final TableKelp _table;

  private Marshal[] _marshalArray;
    
  RestoreTableKelp(TableKelp table, Path path)
  {
    super(path);
    
    Objects.requireNonNull(table);
    
    _table = table;
  }
    
  @Override
  public void parseHeader(String key, Object value)
    throws IOException
  {
    switch (key) {
    case "table-name":
      String tableName = (String) value;
      
      if (! _table.getName().equals(tableName)) {
        throw error("Mismatched table name file={0} table={1}",
                    tableName, _table.getName());
      }
      break;
        
    case "columns":
      List<String> columns = (List) value;
      parseColumns(columns);
      break;
    }
  }
  
  private void parseColumns(List<String> columns)
    throws IOException
  {
    Marshal []marshalArray = new Marshal[columns.size() / 2];
    
    for (int i = 0; i < marshalArray.length; i++) {
      String name = columns.get(2 * i);
      String type = columns.get(2 * i + 1);
      
      Column col = _table.getColumn(name);
      
      Marshal marshal;
      
      switch (type) {
      case "STATE":
        marshal = new MarshalState();
        break;
        
      case "VERSION":
        marshal = new MarshalVersion();
        break;
        
      case "TIMEOUT":
        marshal = new MarshalTimeout();
        break;
        
      case "INT16":
      case "INT32":
        if (col != null) {
          marshal = new MarshalInt32(col.index());
        }
        else {
          marshal = new MarshalIgnore();
        }
        break;
        
      case "INT64":
        if (col != null) {
          marshal = new MarshalInt64(col.index());
        }
        else {
          marshal = new MarshalIgnore();
        }
        break;
        
      case "DOUBLE":
        if (col != null) {
          marshal = new MarshalDouble(col.index());
        }
        else {
          marshal = new MarshalIgnore();
        }
        break;
        
      case "STRING":
      case "BLOB":
      case "OBJECT":
        if (col != null) {
          marshal = new MarshalBlob(col.index());
        }
        else {
          marshal = new MarshalIgnore();
        }
        break;
        
      default:
        throw error("Unknown column type {0} for column {1}", type, name);
      }
      
      
      marshalArray[i] = marshal;
    }
    
    _marshalArray = marshalArray;
  }
  
  @Override
  protected void readData(InH3 hIn)
    throws IOException
  {
    if (_marshalArray == null) {
      throw error("Data requires a parsed marshal array");
    }
    
    Marshal []marshalArray = _marshalArray;
    
    int state;
    
    while ((state = hIn.readInt()) > 0) {
      if (state == STATE_DATA) {
        RowCursor cursor = _table.cursor();
        
        for (int i = 1; i < marshalArray.length; i++) {
          Marshal marshal = marshalArray[i];
          
          marshal.read(hIn, cursor);
        }
        
        _table.putWithVersion(cursor, Result.ignore());
      }
      else {
        throw error("Unknown state {0}", state);
      }
    }
  }
  
  private static class Marshal {
    void read(InH3 hIn, RowCursor cursor)
      throws IOException
    {
      throw new UnsupportedOperationException(getClass().getName());
    }
  }
  
  private static class MarshalState extends Marshal {
  }
  
  private static class MarshalVersion extends Marshal  {
    @Override
    void read(InH3 hIn, RowCursor cursor)
      throws IOException
    {
      long version = hIn.readLong();
      
      cursor.setVersion(version);
    }
  }
  
  private static class MarshalTimeout extends Marshal  {
    @Override
    void read(InH3 hIn, RowCursor cursor)
      throws IOException
    {
      int timeout = hIn.readInt();
      
      cursor.setTimeout(timeout);
    }
  }
  
  private static class MarshalIgnore extends Marshal  {
    @Override
    void read(InH3 hIn, RowCursor cursor)
      throws IOException
    {
      hIn.readObject();
    }
  }
  
  private static class MarshalInt32 extends Marshal  {
    private int _index;
    
    MarshalInt32(int index)
    {
      _index = index;
    }
    
    @Override
    void read(InH3 hIn, RowCursor cursor)
      throws IOException
    {
      int value = hIn.readInt();
      
      cursor.setInt(_index, value);
    }
  }
  
  private static class MarshalInt64 extends Marshal  {
    private int _index;
    
    MarshalInt64(int index)
    {
      _index = index;
    }
    
    @Override
    void read(InH3 hIn, RowCursor cursor)
      throws IOException
    {
      long value = hIn.readLong();
      
      cursor.setLong(_index, value);
    }
  }
  
  private static class MarshalDouble extends Marshal  {
    private int _index;
    
    MarshalDouble(int index)
    {
      _index = index;
    }
    
    @Override
    void read(InH3 hIn, RowCursor cursor)
      throws IOException
    {
      double value = hIn.readDouble();
      
      cursor.setDouble(_index, value);
    }
  }
  
  private static class MarshalBlob extends Marshal  {
    private int _index;
    
    MarshalBlob(int index)
    {
      _index = index;
    }
    
    @Override
    void read(InH3 hIn, RowCursor cursor)
      throws IOException
    {
      InputStream is = null;//hIn.readInputStream();
      
      if (is != null) {
        try (OutputStream os = cursor.openOutputStream(_index)) {
          IoUtil.copy(is, os);
        }
      }
    }
  }
}
