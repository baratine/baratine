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

import io.baratine.service.Result;

import java.util.Objects;

import com.caucho.v5.kelp.Column;
import com.caucho.v5.kelp.query.EnvKelp;
import com.caucho.v5.kelp.query.ExprBuilderKelp;
import com.caucho.v5.kelp.query.QueryBuilderKelp;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.kraken.table.TableManagerKraken;
import com.caucho.v5.util.L10N;

public class WatchQueryBuilder extends QueryBuilderKraken
{
  private static final L10N L = new L10N(WatchQueryBuilder.class);
  
  private final TableManagerKraken _tableManager;
  
  private String _tableName;

  private ExprKraken _whereKraken;

  private EnvKelp _whereKelp;

  private TableKraken _table;

  private QueryBuilderKelp _builderKelp;
  
  public WatchQueryBuilder(TableManagerKraken tableManager,
                            String sql)
  {
    super(sql);
    
    _tableManager = tableManager;
  }
  
  /**
   * Checks if the table meta-data is available to the current server, i.e.
   * is in the local store.
   */
  @Override
  public boolean isTableLoaded()
  {
    return _tableManager.getTable(_tableName) != null;
  }

  @Override
  public TableKraken getTable()
  {
    return _table;
  }
  
  @Override
  public String getTableName()
  {
    return _tableName;
  }
  
  public QueryBuilderKelp getBuilderKelp()
  {
    return _builderKelp;
  }

  @Override
  public void build(Result<QueryKraken> result)
  {
    TableKraken table = _tableManager.getTable(_tableName);
    
    if (table != null) {
      result.ok(buildImpl(table));
    }
    else {
      _tableManager.loadTable(_tableName, 
                              result.of(t->buildImpl(t)));
      /*
      throw new QueryException(L.l("'{0}' is an unknown table\n  {1}",
                                   _tableName, getSql()));
                                   */
    }
    
  }
  
  private WatchQuery buildImpl(TableKraken table)
  {
    Objects.requireNonNull(table);
    
    _table = table;
    
    ExprKraken whereBuilder = _whereKraken;
    
    if (whereBuilder == null) {
      whereBuilder = new TrueExpr();
    }
    
    ExprKraken whereExpr = whereBuilder.bind(this);
    
    ExprKraken keyExpr = whereExpr.bindKey(this);
    
    _builderKelp = new QueryBuilderKelp(_table.getTableKelp());
    
    ExprBuilderKelp whereKelpBuilder = whereExpr.buildKelp(this);
    
    EnvKelp whereKelp = _builderKelp.build(whereKelpBuilder);
    
    return new WatchQuery(sql(), _table, keyExpr, whereExpr, whereKelp);
  }

  public void setTableName(String tableName)
  {
    _tableName = tableName;
  }

  @Override
  public ExprKraken bind(String tableName, String columnName)
  {
    Column column = _table.getTableKelp().getColumn(columnName);
    
    if (column == null) {
      throw new QueryException(L.l("'{0}' is an unknown column of '{1}'",
                                   columnName, _table.getName()));
    }
    
    return new ColumnExpr(column);
  }

  /**
   * @param whereExpr
   */
  public void setWhereExpr(ExprKraken whereExpr)
  {
    _whereKraken = whereExpr;
  }

  public void setKelpExpr(EnvKelp whereKelp)
  {
    _whereKelp = whereKelp;
  }
  
  public EnvKelp getKelpExpr()
  {
    return _whereKelp;
  }

  /**
   * @param params
   */
  public void setParams(ParamExpr[] params)
  {
    // TODO Auto-generated method stub
    
  }
}
