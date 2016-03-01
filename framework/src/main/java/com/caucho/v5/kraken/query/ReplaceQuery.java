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
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.h3.OutFactoryH3;
import com.caucho.v5.io.IoUtil;
import com.caucho.v5.kelp.Column;
import com.caucho.v5.kelp.RowCursor;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kelp.UpdateKelp;
import com.caucho.v5.kelp.query.EnvKelp;
import com.caucho.v5.kelp.query.ExprKelp;
import com.caucho.v5.kraken.table.KrakenException;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.util.L10N;

import io.baratine.service.Result;

public class ReplaceQuery extends QueryKraken
{
  private static final Logger log
    = Logger.getLogger(ReplaceQuery.class.getName());

  private static final L10N L = new L10N(ReplaceQuery.class);

  private TableKraken _table;
  
  private ArrayList<Column> _columns;
  private ArrayList<ExprKelp> _values;
  
  private ArrayList<Column> _updateColumns;
  private ArrayList<ExprKelp> _updateValues;
  
  //private SerializerFactory _serializer;
  private OutFactoryH3 _serializer;

  public ReplaceQuery(String sql,
                     TableKraken table,
                     ArrayList<Column> columns,
                     ArrayList<ExprKelp> values,
                     ArrayList<Column> updateColumns,
                     ArrayList<ExprKelp> updateValues)
  {
    super(sql);

    _table = table;
    
    _columns = columns;
    _values = values;
    
    _updateColumns = updateColumns;
    _updateValues = updateValues;

    //_serializer = new SerializerFactory();
    //_serializer.setAllowNonSerializable(true);
    _serializer = table.serializer();
  }

  @Override
  public void exec(Result<Object> result,
                   Object ...params)
  {
    TableKelp tableKelp = _table.getTableKelp();

    RowCursor cursor = tableKelp.cursor();

    if (_serializer != null) {
      cursor.serializer(_serializer);
    }
    
    EnvKelp envKelp = new EnvKelp(params);
    envKelp.setCursor(cursor);

    for (int i = 0; i < _columns.size(); i++) {
      Column column = _columns.get(i);
      ExprKelp value = _values.get(i);
      
      switch (column.getType()) {
      case INT16:
        cursor.setInt(column.getIndex(), value.evalInt(envKelp));
        break;

      case INT32:
        cursor.setInt(column.getIndex(), value.evalInt(envKelp));
        break;

      case INT64:
        cursor.setLong(column.getIndex(), value.evalLong(envKelp));
        break;

      case BYTES:
        cursor.setBytes(column.getIndex(), value.evalBytes(envKelp), 0);
        break;

      case STRING:
        cursor.setString(column.getIndex(), value.evalString(envKelp));
        break;

      case OBJECT:
        cursor.setObject(column.getIndex(), value.evalObject(envKelp));
        break;

      case DOUBLE:
        cursor.setDouble(column.getIndex(), value.evalDouble(envKelp));
        break;

      case BLOB:
        try (InputStream is = value.evalInputStream(envKelp)) {
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

    _table.getTableKelp().replace(cursor, 
                                  envKelp,
                                  new ReplaceExprImpl(),
                                  _table.getBackupCallback(),
                                  (Result) result);
  }
  
  private void execReplace(RowCursor cursor, EnvKelp envKelp)
  {

    for (int i = 0; i < _updateColumns.size(); i++) {
      Column column = _updateColumns.get(i);
      ExprKelp value = _updateValues.get(i);

      switch (column.getType()) {
      case INT16:
        cursor.setInt(column.getIndex(), value.evalInt(envKelp));
        break;

      case INT32:
        cursor.setInt(column.getIndex(), value.evalInt(envKelp));
        break;

      case INT64:
        cursor.setLong(column.getIndex(), value.evalLong(envKelp));
        break;

      case BYTES:
        cursor.setBytes(column.getIndex(), value.evalBytes(envKelp), 0);
        break;

      case STRING:
        cursor.setString(column.getIndex(), value.evalString(envKelp));
        break;

      case OBJECT:
        cursor.setObject(column.getIndex(), value.evalObject(envKelp));
        break;

      case DOUBLE:
        cursor.setDouble(column.getIndex(), value.evalDouble(envKelp));
        break;

      case BLOB:
        try (InputStream is = value.evalInputStream(envKelp)) {
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
  
  private class ReplaceExprImpl implements UpdateKelp {
    @Override
    public boolean onRow(RowCursor cursor, EnvKelp env)
    {
      env.setCursor(cursor);
      
      execReplace(cursor, env);
      
      return true;
    }
  }
}
