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

package com.caucho.v5.kraken.table;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;

import com.caucho.v5.io.TempOutputStream;
import com.caucho.v5.kelp.GetStreamResult;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.TableKelp;

/**
 * Input stream for a row.
 */
public class SaveTableInputStream extends InputStream
{
  private TableKraken _table;
  private InputStream _isHeader;
  
  private SaveChunkInputStream _isChunk = new SaveChunkInputStream();
  private TableKelp _tableKelp;
  private Iterator<RowCursor> _iterator;
  
  public SaveTableInputStream(TableKraken table)
    throws IOException
  {
    _table = table;
    _tableKelp = table.getTableKelp();
    
    HashMap<String,String> props = new HashMap<>();
    
    props.put("name", table.getName());
    props.put("sql", table.getSql());
    
    TempOutputStream tos = new TempOutputStream();
    
    /*
    try (WriteStream outWs = new WriteStream(tos)) {
      try (JsonWriter out = new JsonWriter(outWs)) {
        out.write(props);
      }
    }
    
    tos.close();
    */
    
    _isHeader = tos.openInputStream();
      
    _isChunk.init(_isHeader);
  }
  
  @Override
  public int read()
    throws IOException
  {
    int result = _isChunk.read();
    
    if (result >= 0) {
      return result;
    }
    
    if (_isHeader != null) {
      _isHeader = null;
      
      RowCursor min = _tableKelp.cursor();
      RowCursor max = _tableKelp.cursor();
      max.setKeyMax();
      
      Iterable<RowCursor> iter = _tableKelp.queryRange(min, max, null);
      
      if (iter != null) {
        _iterator = iter.iterator();
      }
    }

    if (_iterator != null) {
      InputStream is = null;
      
      if (_iterator.hasNext()) {
        RowCursor cursor = _iterator.next();
        
        GetStreamResult getResult = _tableKelp.getStream(cursor);
        
        if (getResult.isUpdate()) {
          is = getResult.getStreamSource().getInputStream();
        }
      }
      else {
        _iterator = null;
        
        TempOutputStream tos = new TempOutputStream();
        tos.close();
        
        is = tos.openInputStream();
      }
      
      _isChunk.init(is);
    }
    
    
    return result;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _table + "]";
  }
}
