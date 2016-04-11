
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
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.IoUtil;

import io.baratine.service.OnDestroy;
import io.baratine.service.OnInit;
import io.baratine.service.Result;
import io.baratine.service.Service;

@Service
public class JdbcConnectionImpl implements JdbcService
{
  private Logger _logger = Logger.getLogger(JdbcConnectionImpl.class.toString());

  private String _url;
  private Properties _props;

  private Connection _conn;

  private int _id;
  private String _testQueryBefore;
  private String _testQueryAfter;

  protected JdbcConnectionImpl()
  {
    // zero-arg constructor for Baratine to instantiate proxies of this class
  }

  public JdbcConnectionImpl(int id, String url, Properties props,
                            String testQueryBefore, String testQueryAfter)
  {
    if (_logger.isLoggable(Level.FINE)) {
      _logger.log(Level.FINE, "constructor: id=" + id + ", url=" + toDebugSafe(url));
    }

    _id = id;
    _url = url;
    _props = props;

    _testQueryBefore = testQueryBefore;
    _testQueryBefore = testQueryAfter;
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

    _conn = DriverManager.getConnection(_url, _props);
  }

  @Override
  public void execute(Result<Integer> result, String sql, Object ... params)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "execute: id=" + _id + ", sql=" + toDebugSafe(sql));
    }

    testQueryBefore();

    try {
      int updateCount = execute(sql, params);

      result.ok(updateCount);

      testQueryAfter();
    }
    catch (SQLException e) {
      reconnect();

      result.fail(e);
    }
  }

  @Override
  public void executeBatch(Result<List<Integer>> result, String sql, List<Object> ... paramsList)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "executeBatch: id=" + _id + ", sql=" + toDebugSafe(sql));
    }

    testQueryBefore();

    ArrayList<Integer> updateCountList = new ArrayList<>();

    try {
      for (List<Object> params : paramsList) {
        int updateCount = execute(sql, params);

        updateCountList.add(updateCount);
      }

      result.ok(updateCountList);

      testQueryAfter();
    }
    catch (SQLException e) {
      reconnect();

      result.fail(e);
    }
  }

  @Override
  public void executeBatch(Result<List<Integer>> result, List<String> sqlList, List<Object> ... paramsList)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "executeBatch: id=" + _id);
    }

    testQueryBefore();

    ArrayList<Integer> updateCountList = new ArrayList<>();

    try {
      int i = 0;

      for (String sql : sqlList) {
        List<Object> params = paramsList[i++];

        int updateCount = execute(sql, params);

        updateCountList.add(updateCount);
      }

      result.ok(updateCountList);

      testQueryAfter();
    }
    catch (SQLException e) {
      reconnect();

      result.fail(e);
    }
  }

  private int execute(String sql)
    throws SQLException
  {
    Statement stmt = null;

    try {
      stmt = _conn.createStatement();

      stmt.execute(sql);

      return stmt.getUpdateCount();
    }
    finally {
      IoUtil.close(stmt);
    }
  }

  private int execute(String sql, Object... params)
    throws SQLException
  {
    if (params.length == 0) {
      return execute(sql);
    }
    else {
      ArrayList<Object> list = new ArrayList<>();

      for (Object param : params) {
        list.add(param);
      }

      return execute(sql, list);
    }
  }

  private int execute(String sql, List<Object> params)
    throws SQLException
  {
    PreparedStatement stmt = null;

    try {
      stmt = _conn.prepareStatement(sql);

      int i = 0;

      for (Object param : params) {
        stmt.setObject(++i, param);
      }

      stmt.execute();

      return stmt.getUpdateCount();
    }
    finally {
      IoUtil.close(stmt);
    }
  }

  @Override
  public void query(Result<ResultSetKraken> result, String sql, Object ... params)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "query: id=" + _id + ", sql=" + toDebugSafe(sql));
    }

    testQueryBefore();

    PreparedStatement stmt = null;

    try {
      stmt = _conn.prepareStatement(sql);

      for (int i = 0; i < params.length; i++) {
        stmt.setObject(i + 1, params[i]);
      }

      boolean isResultSet = stmt.execute();

      ResultSetKraken rs;

      if (isResultSet) {
        rs = new ResultSetKraken(stmt.getResultSet());
      }
      else {
        rs = new ResultSetKraken();
      }

      result.ok(rs);

      testQueryAfter();
    }
    catch (SQLException e) {
      reconnect();

      result.fail(e);
    }
    finally {
      IoUtil.close(stmt);
    }
  }

  @Override
  public void queryBatch(Result<List<ResultSetKraken>> result, String sql, List<Object> ... paramsList)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "queryBatch: id=" + _id + ", sql=" + toDebugSafe(sql));
    }

    testQueryBefore();

    PreparedStatement stmt = null;
    ArrayList<ResultSetKraken> list = new ArrayList<>();

    try {
      stmt = _conn.prepareStatement(sql);

      for (List<Object> params : paramsList) {
        ResultSetKraken rs = query(stmt, params);

        list.add(rs);
      }

      result.ok(list);

      testQueryAfter();
    }
    catch (SQLException e) {
      reconnect();

      result.fail(e);
    }
    finally {
      IoUtil.close(stmt);
    }
  }

  @Override
  public void queryBatch(Result<List<ResultSetKraken>> result, List<String> sqlList, List<Object> ... paramsList)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "queryBatch: id=" + _id + ", sql=" + sqlList.size());
    }

    testQueryBefore();

    PreparedStatement stmt = null;
    ArrayList<ResultSetKraken> list = new ArrayList<>();

    try {
      int i = 0;

      for (String sql : sqlList) {
        List<Object> params = paramsList[i++];

        stmt = _conn.prepareStatement(sql);

        ResultSetKraken rs = query(stmt, params);

        stmt.close();

        list.add(rs);
      }

      testQueryAfter();
    }
    catch (SQLException e) {
      reconnect();

      result.fail(e);
    }
    finally {
      IoUtil.close(stmt);
    }

    result.ok(list);
  }

  private ResultSetKraken query(PreparedStatement stmt, List<Object> params)
    throws SQLException
  {
    int i = 0;

    for (Object param : params) {
      stmt.setObject(++i, param);
    }

    boolean isResultSet = stmt.execute();

    if (isResultSet) {
      return new ResultSetKraken(stmt.getResultSet());
    }
    else {
      return new ResultSetKraken();
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
      execute(_testQueryBefore);
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
      execute(_testQueryAfter);
    }
    catch (SQLException e) {
      if (_logger.isLoggable(Level.FINER)) {
        _logger.log(Level.FINER, "testQueryAfter failed: id=" + _id + ", " + e.getMessage(), e);
      }

      reconnect();
    }
  }

  private String toDebugSafe(String str)
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
