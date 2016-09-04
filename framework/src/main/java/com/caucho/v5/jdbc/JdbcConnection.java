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

import com.caucho.v5.io.IoUtil;

import io.baratine.jdbc.JdbcResultSet;
import io.baratine.jdbc.SqlBiFunction;
import io.baratine.jdbc.SqlFunction;
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

    ExecuteBiFunction fun = new ExecuteBiFunction(sql);

    queryImpl(result, fun, params);
  }

  public void query(Result<WrappedValue<JdbcResultSet>> result, String sql, Object ... params)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "query: id=" + _id + ", sql=" + toDebugSafe(sql));
    }

    QueryBiFunction fun = new QueryBiFunction(sql);

    queryImpl(result, fun, params);
  }

  public <T> void query(Result<WrappedValue<T>> result, SqlFunction<T> fun)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "query: id=" + _id + ", sql=" + fun);
    }

    queryImpl(result, fun);
  }

  public <T> void query(Result<WrappedValue<T>> result, SqlBiFunction<T> fun, Object ... params)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "query: id=" + _id + ", sql=" + fun);
    }

    queryImpl(result, fun, params);
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

  private <T> void queryImpl(Result<WrappedValue<T>> result, SqlBiFunction<T> fun, Object ... params)
  {
    WrappedValue<T> wrapper = new WrappedValue<>();
    wrapper.startTimeMs(System.currentTimeMillis());

    testQueryBefore();

    try {
      _conn.setAutoCommit(false);

      T value = doQuery(fun, params);

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

  public JdbcResultSet doQuery(String sql, Object ... params) throws Exception
  {
    QueryBiFunction fun = new QueryBiFunction(sql);

    return doQuery(fun, params);
  }

  public <T> T doQuery(SqlFunction<T> fun) throws Exception
  {
    try {
      return fun.applyWithException(_conn);
    }
    finally {
      _conn.closeStatements();
    }
  }

  public <T> T doQuery(SqlBiFunction<T> fun, Object ... params) throws Exception
  {
    try {
      return fun.applyWithException(_conn, params);
    }
    finally {
      _conn.closeStatements();
    }
  }

  public static class ExecuteBiFunction implements SqlBiFunction<Integer> {
    private String _sql;

    private PreparedStatement _stmt;

    public ExecuteBiFunction(String sql)
    {
      _sql = sql;
    }

    public Integer applyWithException(Connection conn, Object ... params) throws SQLException
    {
      _stmt = conn.prepareStatement(_sql);

      if (params != null) {
        for (int i = 0; i < params.length; i++) {
          _stmt.setObject(i + 1, params[i]);
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

  public static class QueryBiFunction implements SqlBiFunction<JdbcResultSet> {
    private String _sql;

    private PreparedStatement _stmt;

    public QueryBiFunction(String sql)
    {
      _sql = sql;
    }

    public JdbcResultSet applyWithException(Connection conn, Object ... params) throws SQLException
    {
      _stmt = conn.prepareStatement(_sql);

      if (params != null) {
        for (int i = 0; i < params.length; i++) {
          _stmt.setObject(i + 1, params[i]);
        }
      }

      boolean isResultSet = _stmt.execute();
      int updateCount = _stmt.getUpdateCount();

      ResultSet rs = _stmt.getResultSet();

      JdbcResultSet jdbcRs = JdbcResultSet.create(rs, updateCount);

      return jdbcRs;
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

  private void testQueryBefore()
  {
    if (_testQueryBefore == null) {
      testIsValid();

      return;
    }

    try {
      if (_logger.isLoggable(Level.FINER)) {
        _logger.log(Level.FINER, "testQueryBefore: id=" + _id + ", sql=" + toDebugSafe(_testQueryBefore));
      }

      QueryBiFunction fun = new QueryBiFunction(_testQueryBefore);

      doQuery(fun);
    }
    catch (Exception e) {
      if (_logger.isLoggable(Level.FINER)) {
        _logger.log(Level.FINER, "testQueryBefore failed: id=" + _id + ", " + e.getMessage(), e);
      }

      reconnect();
    }
  }

  private void testIsValid()
  {
    boolean isValid = false;
    Exception exception = null;

    try {
      isValid = _conn.isValid(1000 * 10);
    }
    catch (Exception e) {
      exception = e;
    }

    if (! isValid) {
      if (_logger.isLoggable(Level.FINER)) {
        if (exception != null) {
          _logger.log(Level.FINER, "connection is not valid: id=" + _id + "," + exception.getMessage(), exception);
        }
        else {
          _logger.log(Level.FINER, "connection is not valid: id=" + _id);
        }
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
      QueryBiFunction fun = new QueryBiFunction(_testQueryAfter);

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
