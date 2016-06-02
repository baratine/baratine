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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.kraken.query;

import io.baratine.db.CursorPrepare;
import io.baratine.db.CursorPrepareSync;
import io.baratine.service.Result;
import io.baratine.service.ResultFuture;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.IoUtil;
import com.caucho.v5.kelp.Column;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kraken.table.KrakenException;
import com.caucho.v5.util.L10N;

public class InsertCursorPrepare implements CursorPrepareSync
{
  private static final L10N L = new L10N(InsertCursorPrepare.class);
  private static final Logger log
    = Logger.getLogger(InsertCursorPrepare.class.getName());
  
  private final InsertQuery _query;
  private final Column[] _columns;
  
  private RowCursor _cursor;

  public InsertCursorPrepare(InsertQuery query, 
                             Column[] columns)
  {
    Objects.requireNonNull(query);
    _query = query;
    
    Objects.requireNonNull(columns);
    _columns = columns;
  }

  @Override
  public void setInt(int index, int value)
  {
    RowCursor cursor = cursor();
    Column column = getColumn(index);
    
    cursor.setInt(column.index(), value);
  }

  @Override
  public void setLong(int index, long value)
  {
    RowCursor cursor = cursor();
    Column column = getColumn(index);
    
    cursor.setLong(column.index(), value);
  }

  @Override
  public void setDouble(int index, double value)
  {
    RowCursor cursor = cursor();
    Column column = getColumn(index);
    
    cursor.setDouble(column.index(), value);
  }

  @Override
  public void setString(int index, String value)
  {
    RowCursor cursor = cursor();
    Column column = getColumn(index);
    
    cursor.setString(column.index(), value);
  }

  @Override
  public void setObject(int index, Object value)
  {
    RowCursor cursor = cursor();
    Column column = getColumn(index);
    
    cursor.setObject(column.index(), value);
  }

  @Override
  public void setBytes(int index, byte[] value)
  {
    RowCursor cursor = cursor();
    Column column = getColumn(index);
    
    cursor.setBytes(column.index(), value, 0);
  }

  @Override
  public void setInputStream(int index, InputStream is)
  {
    if (is == null) {
      return;
    }
    
    try (OutputStream os = openOutputStream(index)) {
      long total = IoUtil.copy(is, os);

      if (log.isLoggable(Level.FINEST)) {
        log.finest(L.l("inserted {0} bytes into column {1} for sql={2}", total,
                       index, _query.getSql()));
      }
    } catch (IOException e) {
      throw new KrakenException(e);
    }
  }

  @Override
  public OutputStream openOutputStream(int index)
  {
    RowCursor cursor = cursor();
    Column column = getColumn(index);
    
    return cursor.openOutputStream(column.index());
  }
  
  private RowCursor cursor()
  {
    if (_cursor == null) {
      _cursor = _query.table().getTableKelp().cursor();
    }
    
    return _cursor;
  }
  
  private Column getColumn(int i)
  {
    if (i < 1 || _columns.length < i) {
      throw new IllegalArgumentException(L.l("'{0}' is an invalid column for '{1}'",
                                             i, _query.getSql()));
    }
    
    return _columns[i - 1];
  }

  @Override
  public void exec(Result<Object> result)
  {
    Objects.requireNonNull(result);
    
    try {
      RowCursor cursor = cursor();
    
      _cursor = null;
    
      _query.execPrepare(cursor, result);
    } catch (Throwable e) {
      result.fail(e);
    }
  }

  @Override
  public Object exec()
  {
    ResultFuture<Object> future = new ResultFuture<>();
    
    exec(future);
    
    return future.get(60, TimeUnit.SECONDS);
  }

  @Override
  public CursorPrepare clone()
  {
    return new InsertCursorPrepare(_query, _columns);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _query.getSql() + "]";
  }
}
