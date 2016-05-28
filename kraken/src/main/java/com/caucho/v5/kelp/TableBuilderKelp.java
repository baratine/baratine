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

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.caucho.v5.util.Fnv256;
import com.caucho.v5.util.L10N;

import io.baratine.service.Result;
import io.baratine.service.ResultFuture;

/**
 * The store manages the block-based persistent store file.  Each table
 * will have its own store file, table.db.
 *
 * The store is log-based around segments. Each segment is write-only
 * until it is filled and the garbage-collected.
 */
public class TableBuilderKelp
{
  private static final L10N L = new L10N(TableBuilderKelp.class);
  
  private final DatabaseKelp _db;
  private final String _name;
  
  private RowBuilder _rowBuilder;

  private boolean _isValidate = false; // true;

  TableBuilderKelp(DatabaseKelp db, String name)
  {
    Objects.requireNonNull(db);
    Objects.requireNonNull(name);
    
    _db = db;
    _name = name;
    
    _rowBuilder = new RowBuilder(name);
  }
  
  public TableBuilderKelp columnInt8(String name)
  {
    _rowBuilder.int8(name);
    
    return this;
  }
  
  public TableBuilderKelp columnInt16(String name)
  {
    _rowBuilder.int16(name);
    
    return this;
  }
  
  public TableBuilderKelp columnInt32(String name)
  {
    _rowBuilder.int32(name);
    
    return this;
  }
  
  public TableBuilderKelp columnInt64(String name)
  {
    _rowBuilder.int64(name);
    
    return this;
  }
  
  public TableBuilderKelp columnDouble(String name)
  {
    _rowBuilder.doubleCol(name);
    
    return this;
  }
  
  public TableBuilderKelp columnBytes(String name, int size)
  {
    _rowBuilder.bytes(name, size);
    
    return this;
  }
  
  public TableBuilderKelp columnBlob(String name)
  {
    _rowBuilder.blob(name);
    
    return this;
  }

  public TableBuilderKelp columnString(String name)
  {
    _rowBuilder.string(name);
    
    return this;
  }

  public TableBuilderKelp columnObject(String name)
  {
    _rowBuilder.object(name);
    
    return this;
  }
  
  public TableBuilderKelp startKey()
  {
    _rowBuilder.startKey();
    
    return this;
  }
  
  public TableBuilderKelp endKey()
  {
    _rowBuilder.endKey();
    
    return this;
  }

  public TableBuilderKelp validate(boolean isValidate)
  {
    _isValidate  = isValidate;
    
    return this;
  }

  public boolean isValidate()
  {
    return _isValidate;
  }
  
  public TableKelp build()
  {
    ResultFuture<TableKelp> future = new ResultFuture<>();
    
    build(future);
    
    TableKelp table = future.get(60, TimeUnit.SECONDS);
    
    return table;
  }
  
  public void build(Result<TableKelp> result)
  {
    Row row = _rowBuilder.build(_db);

    /*
    try {
      return new DatabaseKelp(_path,
                               _rowBuilder,
                               this);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    */

    Fnv256 keyGen = new Fnv256();
    keyGen.update(_name);
    keyGen.updateInt32(row.length());
    keyGen.updateInt32(row.keyOffset());
    keyGen.updateInt32(row.keyLength());
    
    for (Column col : row.columns()) {
      keyGen.update(col.name());
      keyGen.updateInt32(col.type().ordinal());
      keyGen.updateInt32(col.length());
    }

    byte []tableKey = keyGen.getDigest();

    if (tableKey.length != TableKelp.TABLE_KEY_SIZE) {
      throw new IllegalStateException();
    }
    
    _db.getDatabaseService().addTable(_name, tableKey, row, result);
  }
}
