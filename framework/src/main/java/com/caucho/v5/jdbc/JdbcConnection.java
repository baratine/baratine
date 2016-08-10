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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.rowset.CachedRowSetImpl;

import com.caucho.v5.io.IoUtil;

import io.baratine.service.OnDestroy;
import io.baratine.service.OnInit;
import io.baratine.service.Result;

@SuppressWarnings("restriction")
public class JdbcConnection
{
  private static Logger _logger = Logger.getLogger(JdbcConnection.class.toString());

  private String _url;
  private Properties _props;

  private ConnectionWrapper _conn;

  private int _id;
  private String _testQueryBefore;
  private String _testQueryAfter;

  public static JdbcConnection create(int id, String url, Properties props,
                                      String testQueryBefore, String testQueryAfter)
  {
    JdbcConnection conn = new JdbcConnection();

    if (_logger.isLoggable(Level.FINE)) {
      _logger.log(Level.FINE, "create: id=" + id + ", url=" + toDebugSafe(url));
    }

    conn._id = id;
    conn._url = url;
    conn._props = props;

    conn._testQueryBefore = testQueryBefore;
    conn._testQueryAfter = testQueryAfter;

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

  public void execute(Result<WrappedValue<Integer>> result, String sql, Object ... params)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "query: id=" + _id + ", sql=" + toDebugSafe(sql));
    }

    ExecuteFunction fun = new ExecuteFunction(sql, params);

    queryImpl(result, fun);
  }

  public void query(Result<WrappedValue<ResultSet>> result, String sql, Object ... params)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "query: id=" + _id + ", sql=" + toDebugSafe(sql));
    }

    QueryFunction fun = new QueryFunction(sql, params);

    queryImpl(result, fun);
  }

  public <T> void query(Result<WrappedValue<T>> result, SqlFunction<T> fun)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "query: id=" + _id + ", sql=" + fun);
    }

    queryImpl(result, fun);
  }

  private <T> void queryImpl(Result<WrappedValue<T>> result, SqlFunction<T> fun)
  {
    WrappedValue<T> wrapper = new WrappedValue<>();
    wrapper.startTimeMs(System.currentTimeMillis());

    testQueryBefore();

    try {
      _conn.setAutoCommit(false);

      T value = doQuery(fun);

      _conn.commit();

      wrapper.value(value);

      fun.close();

      testQueryAfter();
    }
    catch (Exception e) {
      _logger.log(Level.FINER, e.getMessage(), e);

      reconnect();

      wrapper.exception(e);
    }

    wrapper.endTimeMs(System.currentTimeMillis());

    result.ok(wrapper);
  }

  public ResultSet doQuery(String sql, Object ... params) throws Exception
  {
    QueryFunction fun = new QueryFunction(sql, params);

    return doQuery(fun);
  }

  public <T> T doQuery(SqlFunction<T> fun) throws Exception
  {
    try {
      return fun.applyException(_conn);
    }
    finally {
      _conn.closeStatements();
    }
  }

  public static class ExecuteFunction implements SqlFunction<Integer> {
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

    public Integer applyException(Connection conn) throws SQLException
    {
      _stmt = conn.prepareStatement(_sql);

      if (_params != null) {
        for (int i = 0; i < _params.length; i++) {
          _stmt.setObject(i + 1, _params[i]);
        }
      }

      int updateCount = _stmt.executeUpdate();

      return updateCount;
    }

    @Override
    public void close()
    {
      IoUtil.close(_stmt);
    }
  }

  public static class QueryFunction implements SqlFunction<ResultSet> {
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

    public ResultSet applyException(Connection conn) throws SQLException
    {
      _stmt = conn.prepareStatement(_sql);

      if (_params != null) {
        for (int i = 0; i < _params.length; i++) {
          _stmt.setObject(i + 1, _params[i]);
        }
      }

      boolean isResultSet = _stmt.execute();

      if (isResultSet) {
        ResultSet rs = _stmt.getResultSet();

        CachedRowSetImpl cRs = new CachedRowSetImpl();
        cRs.populate(rs);

        return cRs;
      }
      else {
        return null;
      }
    }

    @Override
    public void close()
    {
      IoUtil.close(_stmt);
    }

    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + toDebugSafe(_sql) + "]";
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
    catch (Exception e) {
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
    catch (Exception e) {
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
  public void onDestroy()
  {
    try {
      _conn.close();
    }
    catch (Exception e) {
    }
  }
}
