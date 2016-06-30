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

import com.caucho.v5.kelp.query.EnvKelp;
import com.caucho.v5.kraken.table.TableKraken;
import com.caucho.v5.kraken.table.KrakenImpl;
import com.caucho.v5.util.L10N;

public class QueryBuilderExplain extends QueryBuilderKraken
{
  private static final L10N L = new L10N(QueryBuilderExplain.class);
  
  private final KrakenImpl _tableManager;
  
  private SelectQueryBuilder _selectBuilder;
  
  public QueryBuilderExplain(KrakenImpl tableManager,
                            String sql)
  {
    super(sql);
    
    _tableManager = tableManager;
    
    _selectBuilder = new SelectQueryBuilder(tableManager, sql);
  }
  
  protected SelectQueryBuilder getDelegate()
  {
    return _selectBuilder;
  }
  
  /**
   * Checks if the table meta-data is available to the current server, i.e.
   * is in the local store.
   */
  @Override
  public boolean isTableLoaded()
  {
    return getDelegate().isTableLoaded();
  }

  @Override
  public TableKraken getTable()
  {
    return getDelegate().getTable();
  }
  
  @Override
  public String getTableName()
  {
    return getDelegate().getTableName();
  }

  /*
  @Override
  public QueryExplain build()
  {
    return new QueryExplain(getSql(), getDelegate().build());
  }
  */
  @Override
  public void build(Result<QueryKraken> result)
  {
    getDelegate().build(result.then(query->buildExplain(query)));
  }

  private QueryExplain buildExplain(QueryKraken query)
  {
    return new QueryExplain(sql(), (SelectQueryBase) query);
  }
  
  public void setTableName(String tableName)
  {
    getDelegate().setTableName(tableName);
  }

  @Override
  public ExprKraken bind(String tableName, String columnName)
  {
    return getDelegate().bind(tableName, columnName);
  }

  public void setResults(ExprKraken[] resultArray)
  {
    getDelegate().setResults(resultArray);
  }

  /**
   * @param whereExpr
   */
  public void setWhereExpr(ExprKraken whereExpr)
  {
    getDelegate().setWhereExpr(whereExpr);
  }

  public void setKelpExpr(EnvKelp whereKelp)
  {
    getDelegate().setKelpExpr(whereKelp);
  }
  
  public EnvKelp getKelpExpr()
  {
    return getDelegate().getKelpExpr();
  }

  /**
   * @param params
   */
  public void setParams(ParamExpr[] params)
  {
    getDelegate().setParams(params);
  }
}
