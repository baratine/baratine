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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

import com.caucho.v5.h3.H3;
import com.caucho.v5.h3.OutFactoryH3;
import com.caucho.v5.h3.OutH3;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;

/**
 * btree-based database
 */
public class ArchiveTableKelp
{
  private static final L10N L = new L10N(ArchiveTableKelp.class);
  
  static final String ARCHIVE_VERSION = "Kelp/0.9.1";
  
  static final String HEADER = "\n:header:\n";
  static final String DATA = "\n:data:\n";
  
  static final int STATE_END = 0;
  static final int STATE_DATA = 1;
  static final int STATE_REMOVED = 2;
  
  private final TableKelp _table;
  private final Path _path;
  
  private boolean _isZip = true;
  private String _sql;
    
  ArchiveTableKelp(TableKelp table, Path path)
  {
    Objects.requireNonNull(table);
    Objects.requireNonNull(path);
    
    _table = table;
    _path = path;
  }
  
  public ArchiveTableKelp zip(boolean isZip)
  {
    _isZip = isZip;
    
    return this;
  }

  public ArchiveTableKelp sql(String sql)
  {
    _sql = sql;
    
    return this;
  }
    
  public void exec()
    throws IOException
  {
    Files.createDirectories(_path.getParent());
    
    try (OutputStream os = Files.newOutputStream(_path)) {
      OutputStream zOs;
      
      if (_isZip) {
        zOs = new GZIPOutputStream(os);
      }
      else {
        zOs = os;
      }
      
      zOs.write(ARCHIVE_VERSION.getBytes());
      zOs.write('\n');
      
      OutFactoryH3 serializer = H3.newOutFactory().get();
      try (OutH3 out = serializer.out(zOs)) {
        writeHeader(out);
      
        writeData(out);
      
        out.writeNull();
      }
      
      if (_isZip) {
        zOs.close();
      }
    }
  }
  
  private void writeHeader(OutH3 out)
    throws IOException
  {
    out.writeString(HEADER);
    
    out.writeString("table-name");
    out.writeString(_table.getName());
    
    long now = CurrentTime.currentTime();
    
    out.writeString("current-time");
    out.writeLong(now);
    
    if (_sql != null) {
      out.writeString("sql");
      out.writeString(_sql);
    }
    
    writeHeaderColumns(out);
    
    out.writeNull();
  }
  
  private void writeHeaderColumns(OutH3 out)
    throws IOException
  {
    ArrayList<String> columns = new ArrayList<>();
    
    columns.add("__state");
    columns.add("STATE");
    
    columns.add("__version");
    columns.add("VERSION");
    
    columns.add("__timeout");
    columns.add("TIMEOUT");
    
    Row row = _table.getRow();
    
    for (Column col : row.getColumns()) {
      if (col.name().equals("_row_state")) {
        continue;
      }
  
      columns.add(col.name());
      columns.add(col.type().toString());
    }
    
    out.writeString("columns");
    out.writeObject(columns);
    
    ArrayList<String> keyNames = new ArrayList<>();
    for (Column col : row.getKeys()) {
      keyNames.add(col.name());
    }
    
    out.writeString("keys");
    out.writeObject(keyNames);
  }
  
  private void writeData(OutH3 out)
    throws IOException
  {
    out.writeString(DATA);
    
    Row row = _table.getRow();
    Column []columns = row.getColumns();
    
    RowCursor minCursor = _table.cursor();
    RowCursor maxCursor = _table.cursor();
    maxCursor.setKeyMax();
    
    TempBuffer tBuf = TempBuffer.create();
    byte []buffer = tBuf.buffer();
    
    for (RowCursor cursor : _table.queryRange(minCursor,  maxCursor, x->true)) {
      int state;
      
      if (cursor.isData()) {
        state = STATE_DATA;
      }
      else if (cursor.isRemoved()) {
        state = STATE_REMOVED;
      }
      else {
        System.out.println("UNKNOWN_STATE: " + cursor);
        continue;
      }
      
      out.writeLong(state);
      out.writeLong(cursor.getVersion());
      out.writeLong(cursor.getStateTimeout());
      
      for (int i = 1; i < columns.length; i++) {
        Column col = columns[i];
        
        switch (col.type()) {
        case INT16:
        case INT32:
          out.writeLong(cursor.getInt(i));
          break;
          
        case INT64:
          out.writeLong(cursor.getLong(i));
          break;
          
        case DOUBLE:
          out.writeDouble(cursor.getDouble(i));
          break;
          
        case BYTES:
          //out.writeBytes(cursor.getBytes(i));
          out.writeBinary(cursor.getBytes(i));
          break;
          
        case BLOB:
        case STRING:
        case OBJECT:
        {
          try (InputStream is = cursor.openInputStream(i)) {
            int sublen;

            if (is != null) {
              while ((sublen = is.read(buffer, 0, buffer.length)) > 0) {
                out.writeBinaryPart(buffer, 0, sublen);
              }
              out.writeBinary(buffer, 0, 0);
            }
            else {
              out.writeNull();
            }
          }
          break;
        }
          
        default:
          throw new UnsupportedOperationException(L.l("Unknown column type: {0} for {1}",
                                                      col.type(), col));
        }
      }
    }
    
    out.writeLong(STATE_END);
  }
}
