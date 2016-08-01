
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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Properties;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.IoUtil;

import io.baratine.service.OnDestroy;
import io.baratine.service.OnInit;
import io.baratine.service.Result;
import io.baratine.service.Service;

public class JdbcConnectionImpl implements JdbcService
{
  private static Logger _logger = Logger.getLogger(JdbcConnectionImpl.class.toString());

  private String _url;
  private Properties _props;

  private Connection _conn;

  private int _id;
  private String _testQueryBefore;
  private String _testQueryAfter;

  public static JdbcConnectionImpl create(int id, String url, Properties props,
                                          String testQueryBefore, String testQueryAfter)
  {
    JdbcConnectionImpl conn = new JdbcConnectionImpl();

    if (_logger.isLoggable(Level.FINE)) {
      _logger.log(Level.FINE, "create: id=" + id + ", url=" + toDebugSafe(url));
    }

    conn._id = id;
    conn._url = url;
    conn._props = props;

    conn._testQueryBefore = testQueryBefore;
    conn._testQueryBefore = testQueryAfter;

    return conn;
  }

  @OnInit
  public void onInit(Result<Void> result)
  {
    if (_logger.isLoggable(Level.FINE)) {
      _logger.log(Level.FINE, "onInit: id=" + _id + ", url=" + toDebugSafe(_url));
    }

    try {
      connect();

      result.ok(null);
    }
    catch (SQLException e) {
      result.fail(e);
    }
  }

  private void reconnect()
  {
    _logger.log(Level.FINE, "reconnect: id=" + _id);

    try {
      IoUtil.close(_conn);

      connect();
    }
    catch (SQLException e) {
      _logger.log(Level.FINE, "failed to reconnect: id=" + _id + ", url=" + toDebugSafe(_url), e);
    }
  }

  private void connect()
    throws SQLException
  {
    _logger.log(Level.FINE, "connect: id=" + _id + ", url=" + toDebugSafe(_url));

    Connection conn = DriverManager.getConnection(_url, _props);

    _conn = new ConnectionWrapper(conn);
  }

  @Override
  public void query(Result<JdbcResultSet> result, String sql, Object ... params)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "query: id=" + _id + ", sql=" + toDebugSafe(sql));
    }

    QueryFunction fun = new QueryFunction(sql, params);

    queryImpl(result, fun);
  }

  @Override
  public <T> void query(Result<T> result, SqlFunction<T> fun)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "query: id=" + _id + ", sql=" + fun);
    }

    queryImpl(result, fun);
  }

  @Override
  public void query(Result<JdbcResultSet> result, QueryBuilder builder)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "query: id=" + _id + ", sql=" + builder);
    }

    QueryBuilderImpl builderImpl = (QueryBuilderImpl) builder;

    testQueryBefore();

    try {
      JdbcResultSet rs = builderImpl.executeAll(this);

      result.ok(rs);
    }
    catch (SQLException e) {
      _logger.log(Level.FINER, e.getMessage(), e);

      reconnect();

      result.fail(e);
    }
  }

  private <T> void queryImpl(Result<T> result, SqlFunction<T> fun)
  {
    testQueryBefore();

    try {
      T value = doQuery(fun);

      result.ok(value);

      fun.close();

      testQueryAfter();
    }
    catch (SQLException e) {
      _logger.log(Level.FINER, e.getMessage(), e);

      reconnect();

      result.fail(e);
    }
  }

  public JdbcResultSet doQuery(String sql, Object ... params)
    throws SQLException
  {
    QueryFunction fun = new QueryFunction(sql, params);

    return doQuery(fun);
  }

  public <T> T doQuery(SqlFunction<T> fun)
    throws SQLException
  {
    return fun.applyException(_conn);
  }

  public static class ExecuteFunction implements SqlFunction<JdbcResultSet> {
    private String _sql;
    private Object[] _params;

    private PreparedStatement _stmt;

    public ExecuteFunction(String sql)
    {
      this(sql, null);
    }

    public ExecuteFunction(String sql, Object[] params)
    {
      _sql = sql;
      _params = params;
    }

    public JdbcResultSet applyException(Connection conn) throws SQLException
    {
      _stmt = conn.prepareStatement(_sql);

      if (_params != null) {
        for (int i = 0; i < _params.length; i++) {
          _stmt.setObject(i + 1, _params[i]);
        }
      }

      int updateCount = _stmt.executeUpdate();

      JdbcResultSet rs = new JdbcResultSet(updateCount);

      return rs;
    }

    @Override
    public void close()
    {
      IoUtil.close(_stmt);
    }
  }

  public static class QueryFunction implements SqlFunction<JdbcResultSet> {
    private String _sql;
    private Object[] _params;

    private PreparedStatement _stmt;

    public QueryFunction(String sql)
    {
      this(sql, null);
    }

    public QueryFunction(String sql, Object[] params)
    {
      _sql = sql;
      _params = params;
    }

    public JdbcResultSet applyException(Connection conn) throws SQLException
    {
      _stmt = conn.prepareStatement(_sql);

      if (_params != null) {
        for (int i = 0; i < _params.length; i++) {
          _stmt.setObject(i + 1, _params[i]);
        }
      }

      boolean isResultSet = _stmt.execute();

      JdbcResultSet rs;

      if (isResultSet) {
        rs = new JdbcResultSet(_stmt.getUpdateCount(), _stmt.getResultSet());
      }
      else {
        rs = new JdbcResultSet(_stmt.getUpdateCount());
      }

      return rs;
    }

    @Override
    public void close()
    {
      IoUtil.close(_stmt);
    }
  }

  public static class AutoCommitOffFunction implements SqlFunction<Void> {
    public Void applyException(Connection conn) throws SQLException
    {
      conn.setAutoCommit(false);

      return null;
    }
  }

  public static class CommitFunction implements SqlFunction<Void> {
    public Void applyException(Connection conn) throws SQLException
    {
      conn.commit();

      return null;
    }
  }

  /*
  private JdbcResultSet query(PreparedStatement stmt, Object[] params)
    throws SQLException
  {
    int i = 0;

    for (Object param : params) {
      stmt.setObject(++i, param);
    }

    boolean isResultSet = stmt.execute();

    if (isResultSet) {
      return new JdbcResultSet(stmt.getResultSet());
    }
    else {
      return new JdbcResultSet();
    }
  }
  */

  private void testQueryBefore()
  {
    if (_testQueryBefore == null) {
      return;
    }

    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "testQueryBefore: id=" + _id + ", sql=" + toDebugSafe(_testQueryBefore));
    }

    try {
      QueryFunction fun = new QueryFunction(_testQueryBefore);

      doQuery(fun);
    }
    catch (SQLException e) {
      if (_logger.isLoggable(Level.FINER)) {
        _logger.log(Level.FINER, "testQueryBefore failed: id=" + _id + ", " + e.getMessage(), e);
      }

      reconnect();
    }
  }

  private void testQueryAfter()
  {
    if (_testQueryAfter == null) {
      return;
    }

    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "testQueryAfter: id=" + _id + ", sql=" + toDebugSafe(_testQueryAfter));
    }

    try {
      QueryFunction fun = new QueryFunction(_testQueryAfter);

      doQuery(fun);
    }
    catch (SQLException e) {
      if (_logger.isLoggable(Level.FINER)) {
        _logger.log(Level.FINER, "testQueryAfter failed: id=" + _id + ", " + e.getMessage(), e);
      }

      reconnect();
    }
  }

  private static String toDebugSafe(String str)
  {
    int len = Math.min(32, str.length());

    return str.substring(0, len);
  }

  @OnDestroy
  public void onDestroy(Result<Void> result)
  {
    try {
      _conn.close();

      result.ok(null);
    }
    catch (SQLException e) {
      result.fail(e);
    }
  }
}
