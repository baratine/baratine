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

import java.util.logging.Logger;

import com.caucho.v5.kelp.Column;
import com.caucho.v5.kelp.TableKelp;
import com.caucho.v5.kraken.info.ColumnInfo;
import com.caucho.v5.kraken.info.TableInfo;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.kraken.table.KrakenImpl;
import com.caucho.v5.util.Hex;
import com.caucho.v5.util.L10N;

import io.baratine.service.Result;

public class QueryShow extends QueryKraken
{
  private static final L10N L = new L10N(QueryShow.class);
  private static final Logger log = Logger.getLogger(QueryShow.class.getName());
  
  private final KrakenImpl _kraken;
  private final String _tableName;

  private String _method;
  
  QueryShow(QueryBuilderShow builder)
  {
    super(builder.sql());
    
    _kraken = builder.getTableManager();
    _tableName = builder.getTableName();
    _method = builder.method();
  }
  
  @Override
  public void exec(Result<Object> result,
                   Object ...params)
  {
    _kraken.loadTable(_tableName, 
                            result.then((table,r)->execQuery(r,table,params)));
  }
  
  private void execQuery(Result<Object> result,
                         TableKraken tableKraken,
                         Object []params)
  {
    switch (_method) {
    case "table":
      showTable(result, tableKraken, params);
      break;
      
    case "tableinfo":
      showTableInfo(result, tableKraken, params);
      break;
      
    default:
      result.fail(new UnsupportedOperationException(_method));
    }
  }
  
  private void showTable(Result<Object> result,
                         TableKraken tableKraken,
                         Object []params)
  {
    if (tableKraken == null) {
      result.ok(L.l("{0} is an unknown table for SHOW TABLE", _tableName));
      return;
    }
    
    TableKelp table = tableKraken.getTableKelp();
    
    StringBuilder sb = new StringBuilder();
    sb.append("CREATE TABLE ");
    sb.append(tableKraken.getPodName());
    sb.append(".");
    sb.append(tableKraken.getName());
    sb.append(" ( #");
    sb.append(Hex.toShortHex(table.getTableKey()));
    
    boolean isFirst = true;
    
    for (Column col : table.row().columns()) {
      if (! isFirst) {
        sb.append(",");
      }
      isFirst = false;
      
      sb.append("\n  ").append(col.name());
      sb.append(" ").append(col.type());
      
      if (col.size() > 0) {
        sb.append("(" + col.size() + ")");
      }
    }
    
    sb.append("\n  primary key (");
    
    isFirst = true;
    for (Column col : table.row().keys()) {
      if (! isFirst) {
        sb.append(", ");
      }
      isFirst = false;
      
      sb.append(col.name());
    }
    
    sb.append(")");
    
    sb.append("\n)");

    result.ok(sb.toString());
  }
  
  private void showTableInfo(Result<Object> result,
                             TableKraken tableKraken,
                             Object []params)
  {
    if (tableKraken == null) {
      result.ok(null);
      return;
    }
    
    TableKelp table = tableKraken.getTableKelp();
    
    TableInfo tableInfo = new TableInfo(tableKraken.getName());
    
    for (Column col : table.row().columns()) {
      if (col.name().startsWith(":") || col.name().equals("_row_state")) {
        continue;
      }

      tableInfo.column(new ColumnInfo(tableInfo, col));
    }

    result.ok(tableInfo);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[table " + _tableName + "]";
  }
}
