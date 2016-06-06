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

package com.caucho.v5.kelp.upgrade;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.caucho.v5.kelp.Column.ColumnType;
import com.caucho.v5.kelp.upgrade.RowUpgrade.RowUpgradeBuilder;
import com.caucho.v5.util.BitsUtil;

/**
 * scanner for the row
 */
public class RowUpgrade10
{
  private int _keyOffset;
  private int _keyLength;

  public RowUpgrade10(int keyOffset, int keyLength)
  {
    _keyOffset = keyOffset;
    _keyLength = keyLength;
  }
  
  public RowUpgrade read(byte []data)
  {
    try (InputStream is = new ByteArrayInputStream(data)) {
      int nameLen = BitsUtil.readInt16(is);
      byte []nameBytes = new byte[nameLen];
      
      is.read(nameBytes);
      
      RowUpgradeBuilder builder = new RowUpgradeBuilder();
      
      String name = new String(nameBytes, "UTF-8");
      
      builder.name(name);

      int columnStart = BitsUtil.readInt16(is);
      int columnEnd = BitsUtil.readInt16(is);

      int columns = BitsUtil.readInt16(is);

      for (int i = 0; i < columns; i++) {
        ColumnUpgrade column = readColumn(is, builder);
        
        builder.column(column);
      }
      
      int blobs = BitsUtil.readInt16(is);

      for (int i = 0; i < blobs; i++) {
        ColumnUpgrade column = readColumn(is, builder);
        
        //builder.column(column);
      }
      
      readProps(is, builder);
      
      return builder.build();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  private void readProps(InputStream is, RowUpgradeBuilder builder)
    throws IOException
  {
    int props = BitsUtil.readInt16(is);
    
    for (int i = 0; i < props; i++) {
      String propName = readString(is);
      
      switch (propName) {
      case "class":
        readScheme(is, builder);
        break;
        
      default:
        System.out.println("Unknown: " + propName);
        
        return;
      }
    }
  }
  
  private void readScheme(InputStream is, RowUpgradeBuilder builder)
    throws IOException
  {
    String className = readString(is);
    
    int fieldLen = BitsUtil.readInt16(is);
    String []fields = new String[fieldLen];
    
    for (int i = 0; i < fieldLen; i++) {
      fields[i] = readString(is);
    }
    
    builder.scheme(className, fields);
  }
  
  private String readString(InputStream is)
    throws IOException
  {
    int len = BitsUtil.readInt16(is);
    
    byte []data = new byte[len];
    is.read(data, 0, len);
    
    return new String(data, "UTF-8");
  }
  
  private ColumnUpgrade readColumn(InputStream is,
                                   RowUpgradeBuilder builder)
    throws IOException
  {
    int type = BitsUtil.readInt16(is);
    int length = BitsUtil.readInt16(is);
    
    int nameLength = BitsUtil.readInt16(is);
    byte []nameBytes = new byte[nameLength];
    is.read(nameBytes);
    
    int offset = builder.rowLength();
    
    boolean isKey = _keyOffset <= offset && offset < _keyOffset + _keyLength;
    
    String name = new String(nameBytes, "UTF-8");

    switch (ColumnTypes10.values()[type]) {
    case STATE:
      return builder.column(name, ColumnType.STATE, offset, length, isKey);
      
    case BOOL:
      return builder.column(name, ColumnType.BOOL, offset, length, isKey);
      
    case INT8:
      return builder.column(name, ColumnType.INT8, offset, length, isKey);
      
    case INT16:
      return builder.column(name, ColumnType.INT16, offset, length, isKey);
      
    case INT32:
      return builder.column(name, ColumnType.INT32, offset, length, isKey);
      
    case INT64:
      return builder.column(name, ColumnType.INT64, offset, length, isKey);
      
    case FLOAT:
      return builder.column(name, ColumnType.FLOAT, offset, length, isKey);
      
    case DOUBLE:
      return builder.column(name, ColumnType.DOUBLE, offset, length, isKey);
      
    case TIMESTAMP:
      return builder.column(name, ColumnType.TIMESTAMP, offset, length, isKey);
      
    case IDENTITY:
      return builder.column(name, ColumnType.IDENTITY, offset, length, isKey);
      
    case BYTES:
      return builder.column(name, ColumnType.BYTES, offset, length, isKey);
      
    case BLOB:
      return builder.column(name, ColumnType.BLOB, offset, length, isKey);
      
    case STRING:
      return builder.column(name, ColumnType.STRING, offset, length, isKey);
      
    case OBJECT:
      return builder.column(name, ColumnType.OBJECT, offset, length, isKey);
      
    default:
      throw new IllegalStateException(String.valueOf(ColumnTypes10.values()[type]));
    }
  }
  
  static enum ColumnTypes10 {
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
      
    BLOB,
    STRING,
    OBJECT;
  }
}
