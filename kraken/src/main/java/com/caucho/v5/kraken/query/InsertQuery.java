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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.h3.OutFactoryH3;
import com.caucho.v5.io.IoUtil;
import com.caucho.v5.kelp.Column;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kraken.table.KrakenException;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.util.L10N;

import io.baratine.db.CursorPrepareSync;
import io.baratine.service.Result;

public class InsertQuery extends QueryKraken
{
  private static final Logger log
    = Logger.getLogger(InsertQuery.class.getName());

  private static final L10N L = new L10N(InsertQuery.class);

  private TableKraken _table;
  private ArrayList<Column> _columns;
  private ArrayList<ExprKraken> _values;
  //private SerializerFactory _serializer;
  private OutFactoryH3 _serializer;

  public InsertQuery(String sql,
                     InsertQueryBuilder builder,
                     TableKraken table,
                     ArrayList<Column> columns,
                     ArrayList<ExprKraken> values)
  {
    super(sql);

    _table = table;
    _columns = columns;
    _values = values;

    //_serializer = builder.serializerFactory();
    //_serializer.setAllowNonSerializable(true);
    _serializer = table.serializer();
  }
  
  @Override
  public TableKraken table()
  {
    return _table;
  }

  @Override
  public void exec(Result<Object> result,
                   Object ...params)
  {
    Objects.requireNonNull(result);
    
    TableKelp tableKelp = _table.getTableKelp();

    RowCursor cursor = tableKelp.cursor();

    if (_serializer != null) {
      cursor.serializer(_serializer);
    }
    
    exec(cursor, result, params);
  }

  private void exec(RowCursor cursor,
                    Result<Object> result,
                    Object ...params)
  {
    Objects.requireNonNull(result);

    if (table().isClosed()) {
      throw new IllegalStateException(L.l("{0} is closed", table()));
    }
    
    for (int i = 0; i < _columns.size(); i++) {
      Column column = _columns.get(i);
      ExprKraken value = _values.get(i);

      switch (column.type()) {
      case INT16:
        cursor.setInt(column.getIndex(), value.evalInt(cursor, params));
        break;

      case INT32:
        cursor.setInt(column.getIndex(), value.evalInt(cursor, params));
        break;

      case INT64:
        cursor.setLong(column.getIndex(), value.evalLong(cursor, params));
        break;

      case BYTES:
        cursor.setBytes(column.getIndex(), value.evalBytes(params), 0);
        break;

      case STRING:
        cursor.setString(column.getIndex(), value.evalString(params));
        break;

      case OBJECT:
        cursor.setObject(column.getIndex(), value.evalObject(cursor, params));
        break;

      case DOUBLE:
        cursor.setDouble(column.getIndex(), value.evalDouble(params));
        break;

      case BLOB:
        try (InputStream is = value.evalInputStream(params)) {
          if (is != null) {
            try (OutputStream os = cursor.openOutputStream(column.getIndex())) {
              long total = IoUtil.copy(is, os);

              if (log.isLoggable(Level.FINEST)) {
                log.finest(L.l("inserted {0} bytes into column {1} for sql={2}", total, i, getSql()));
              }
            }
          }
        } catch (IOException e) {
          throw new KrakenException(e);
        }
        break;

      default:
        throw new UnsupportedOperationException(String.valueOf(column));
      }
    }

    /*
    System.out.println("INSERT: " + cursor
                       + " " + BartenderSystem.getCurrentSelfServer()
                       + "\n  " + _table.getReplicationCallback() + "\n  " + this);
                       */
    
    _table.getTableKelp().put(cursor, _table.getBackupCallback(), result);
  }
  
  @Override
  public CursorPrepareSync prepare()
  {
    return new InsertCursorPrepare(this, prepareColumns());
  }

  void execPrepare(RowCursor cursor, Result<Object> result)
  {
    fillPrepare(cursor, new Object[0]);
    
    _table.getTableKelp().put(cursor, _table.getBackupCallback(), result);
  }

  private void fillPrepare(RowCursor cursor,
                           Object []params)
  {
    for (int i = 0; i < _columns.size(); i++) {
      Column column = _columns.get(i);
      ExprKraken value = _values.get(i);
      
      if (value instanceof ParamExpr) {
        continue;
      }

      switch (column.type()) {
      case INT16:
        cursor.setInt(column.getIndex(), value.evalInt(cursor, params));
        break;

      case INT32:
        cursor.setInt(column.getIndex(), value.evalInt(cursor, params));
        break;

      case INT64:
        cursor.setLong(column.getIndex(), value.evalLong(cursor, params));
        break;

      case BYTES:
        cursor.setBytes(column.getIndex(), value.evalBytes(params), 0);
        break;

      case STRING:
        cursor.setString(column.getIndex(), value.evalString(params));
        break;

      case OBJECT:
        cursor.setObject(column.getIndex(), value.evalObject(cursor, params));
        break;

      case DOUBLE:
        cursor.setDouble(column.getIndex(), value.evalDouble(params));
        break;

      case BLOB:
        try (InputStream is = value.evalInputStream(params)) {
          if (is != null) {
            try (OutputStream os = cursor.openOutputStream(column.getIndex())) {
              long total = IoUtil.copy(is, os);

              if (log.isLoggable(Level.FINEST)) {
                log.finest(L.l("inserted {0} bytes into column {1} for sql={2}", total, i, getSql()));
              }
            }
          }
        } catch (IOException e) {
          throw new KrakenException(e);
        }
        break;

      default:
        throw new UnsupportedOperationException(String.valueOf(column));
      }
    }
  }

  private Column []prepareColumns()
  {
    int paramCount = countParams();
    
    Column[] columns = new Column[paramCount];
    
    int j = 0;
    
    for (int i = 0; i < _columns.size(); i++) {
      Column column = _columns.get(i);
      ExprKraken value = _values.get(i);

      if (value instanceof ParamExpr) {
        columns[j++] = column;
      }
    }
    
    return columns;
  }
  
  private int countParams()
  {
    int j = 0;
    
    for (int i = 0; i < _values.size(); i++) {
      ExprKraken value = _values.get(i);
      
      if (value instanceof ParamExpr) {
        j++;
      }
    }
    
    return j;
  }
}
