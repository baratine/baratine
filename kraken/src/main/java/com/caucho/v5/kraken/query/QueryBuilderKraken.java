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
import io.baratine.service.ResultFuture;

import java.util.concurrent.TimeUnit;

import com.caucho.v5.kelp.query.QueryBuilderKelp;
import com.caucho.v5.kraken.table.TableKraken;

public class QueryBuilderKraken
{
  private String _sql;
  
  protected QueryBuilderKraken(String sql)
  {
    _sql = sql;
  }
  
  public String sql()
  {
    return _sql;
  }
  
  /**
   * Checks if the table meta-data is available to the current server, i.e.
   * is in the local store.
   */
  public boolean isTableLoaded()
  {
    return true;
  }
  
  public void build(Result<QueryKraken> result)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  public QueryKraken build()
  {
    ResultFuture<QueryKraken> future = new ResultFuture<>();
    
    build(future);
    
    return future.get(10, TimeUnit.SECONDS);
  }
  
  public void bind()
  {
  }

  public QueryBuilderKelp getBuilderKelp()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  public ExprKraken bind(String name, String column)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public String getTableName()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public TableKraken getTable()
  {
    throw new IllegalStateException(getClass().getName());
  }

  public void setTableName(String tableName)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void setWhereExpr(ExprKraken whereExpr)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void setParams(ParamExpr[] params)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void setResults(ExprKraken[] resultArray)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  

  public void setLocal(int node)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + sql() + "]";
  }
}
