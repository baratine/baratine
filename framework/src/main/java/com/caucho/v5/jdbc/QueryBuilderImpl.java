/*
 * Copyright (c) 1998-2016 Caucho Technology -- all rights reserved
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
 * @author Nam Nguyen
 */

package com.caucho.v5.jdbc;

import java.sql.SQLException;
import java.util.function.Function;

import com.caucho.v5.jdbc.JdbcConnectionImpl.AutoCommitOffFunction;
import com.caucho.v5.jdbc.JdbcConnectionImpl.CommitFunction;

public class QueryBuilderImpl implements QueryBuilder
{
  private Function<JdbcResultSet,String> _queryFun;
  private Function<JdbcResultSet,Object[]> _paramFun;

  private QueryBuilderImpl _prev;
  private QueryBuilderImpl _next;

  private QueryBuilderImpl _current = this;

  protected QueryBuilderImpl()
  {
  }

  public QueryBuilderImpl(String sql)
  {
    _queryFun = rs -> sql;
  }

  public QueryBuilderImpl(Function<JdbcResultSet,String> fun)
  {
    _queryFun = fun;
  }

  protected QueryBuilderImpl prev()
  {
    return _prev;
  }

  protected QueryBuilderImpl prev(QueryBuilderImpl builder)
  {
    _prev = builder;

    return this;
  }

  protected QueryBuilderImpl next()
  {
    return _next;
  }

  protected QueryBuilderImpl next(QueryBuilderImpl builder)
  {
    _next = builder;

    return this;
  }

  public QueryBuilderImpl append(QueryBuilderImpl builder)
  {
    builder.prev(_current);
    _current.next(builder);
    _current = builder;

    return this;
  }

  @Override
  public QueryBuilderImpl then(String sql)
  {
    QueryBuilderImpl builder = new QueryBuilderImpl(sql);

    return append(builder);
  }

  @Override
  public QueryBuilderImpl then(Function<JdbcResultSet,String> fun)
  {
    QueryBuilderImpl builder = new QueryBuilderImpl(fun).prev(this);

    return append(builder);
  }

  @Override
  public QueryBuilderImpl withParams(Object ... params)
  {
    if (params.length > 0) {
      _paramFun = rs -> params;
    }

    return this;
  }

  @Override
  public QueryBuilderImpl withParams(Function<JdbcResultSet,Object[]> fun)
  {
    _paramFun = fun;

    return this;
  }

  @Override
  public QueryBuilderImpl commitThen(String sql)
  {
    QueryBuilderImpl commit = new QueryBuilderCommit();

    return append(commit).then(sql);
  }

  @Override
  public QueryBuilderImpl commitThen(Function<JdbcResultSet,String> fun)
  {
    QueryBuilderImpl commit = new QueryBuilderCommit().prev(this);

    return append(commit).then(fun);
  }

  public JdbcResultSet execute(JdbcConnectionImpl conn, JdbcResultSet rs) throws SQLException
  {
    String sql = _queryFun.apply(rs);
    Object[] params = null;

    if (_paramFun != null) {
      params = _paramFun.apply(rs);
    }

    return conn.doQuery(sql, params);
  }

  public JdbcResultSet executeAll(JdbcConnectionImpl conn) throws SQLException
  {
    QueryBuilderImpl current = this;

    JdbcResultSet rs = null;

    while (current != null) {
      rs = current.execute(conn, rs);

      current = current.next();
    }

    return rs;
  }

  static class QueryBuilderAutoCommitOff extends QueryBuilderImpl {
    public JdbcResultSet execute(JdbcConnectionImpl conn, JdbcResultSet rs) throws SQLException
    {
      conn.doQuery(new AutoCommitOffFunction());

      return rs;
    }
  }

  static class QueryBuilderCommit extends QueryBuilderImpl {
    public JdbcResultSet execute(JdbcConnectionImpl conn, JdbcResultSet rs) throws SQLException
    {
      conn.doQuery(new CommitFunction());

      return rs;
    }
  }
}
