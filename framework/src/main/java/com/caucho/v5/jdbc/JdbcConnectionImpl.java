
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

import io.baratine.service.OnInit;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.service.ServiceRef;

@Service
public class JdbcConnectionImpl implements JdbcConnection
{
  private Logger _logger = Logger.getLogger(JdbcConnectionImpl.class.toString());

  private JdbcConnection _self;
  private JdbcConnectionSync _selfSync;

  private Connection _conn;

  @OnInit
  public void onInit()
  {
    _self = ServiceRef.current().as(JdbcConnection.class);
    _selfSync = ServiceRef.current().as(JdbcConnectionSync.class);
  }

  public void connect(String url, Properties props, Result<JdbcConnection> result)
  {
    try {
      _conn = DriverManager.getConnection(url, props);

      result.ok(_self);
    }
    catch (SQLException e) {
      e.printStackTrace();

      result.fail(e);
    }
  }

  public JdbcConnectionSync connectSync(String url, Properties props)
    throws SQLException
  {
    _conn = DriverManager.getConnection(url, props);

    return _selfSync;
  }

  @Override
  public void execute(String sql, Result<Integer> result)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "execute: " + sql.substring(0, 16) + "...");
    }

    try {
      int updateCount = execute(sql);

      result.ok(updateCount);
    }
    catch (SQLException e) {
      e.printStackTrace();

      result.fail(e);
    }
  }

  @Override
  public void executeBatch(String[] sqls, Result<Integer[]> result)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "executeBatch: " + sqls.length);
    }

    Integer[] updateCounts = new Integer[sqls.length];

    try {
      for (int i = 0; i < sqls.length; i++) {
        String sql = sqls[i];

        int updateCount = execute(sql);

        updateCounts[i] = updateCount;
      }

      result.ok(updateCounts);
    }
    catch (SQLException e) {
      e.printStackTrace();

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

  @Override
  public void query(String sql, Result<JdbcResultSet> result)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "query: " + sql.substring(0, 16) + "...");
    }

    Statement stmt = null;

    try {
      stmt = _conn.createStatement();

      boolean isResultSet = stmt.execute(sql);

      if (isResultSet) {
        JdbcResultSet rs = new JdbcResultSet(stmt.getResultSet());

        result.ok(rs);
      }
      else {
        result.ok(null);
      }
    }
    catch (SQLException e) {
      e.printStackTrace();

      result.fail(e);
    }
    finally {
      IoUtil.close(stmt);
    }
  }

  @Override
  public void queryParam(String sql, Object[] params, Result<JdbcResultSet> result)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "queryParam: " + sql);
    }

    PreparedStatement stmt = null;

    try {
      stmt = _conn.prepareStatement(sql);

      JdbcResultSet rs = query(stmt, params);

      result.ok(rs);
    }
    catch (SQLException e) {
      e.printStackTrace();

      result.fail(e);
    }
    finally {
      IoUtil.close(stmt);
    }
  }

  @Override
  public void queryBatch(String sql, Object[][] paramsList, Result<List<JdbcResultSet>> result)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "queryBatch: " + sql);
    }

    PreparedStatement stmt = null;
    ArrayList<JdbcResultSet> list = new ArrayList<>();

    try {
      stmt = _conn.prepareStatement(sql);

      for (Object[] params : paramsList) {
        JdbcResultSet rs = query(stmt, params);

        list.add(rs);
      }

      result.ok(list);
    }
    catch (SQLException e) {
      e.printStackTrace();

      result.fail(e);
    }
    finally {
      IoUtil.close(stmt);
    }
  }

  @Override
  public void queryBatch(String[] sqls, Object[][] paramsList, Result<List<JdbcResultSet>> result)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "queryBatch: " + sqls.length);
    }

    PreparedStatement stmt = null;
    ArrayList<JdbcResultSet> list = new ArrayList<>();

    try {
      for (int i = 0; i < sqls.length; i++) {
        String sql = sqls[i];
        Object[] params = paramsList[i];

        stmt = _conn.prepareStatement(sql);

        JdbcResultSet rs = query(stmt, params);

        stmt.close();

        list.add(rs);
      }
    }
    catch (SQLException e) {
      e.printStackTrace();

      result.fail(e);
    }
    finally {
      IoUtil.close(stmt);
    }

    result.ok(list);
  }

  private JdbcResultSet query(PreparedStatement stmt, Object[] params)
    throws SQLException
  {
    for (int i = 0; i < params.length; i++) {
      stmt.setObject(i + 1, params[i]);
    }

    boolean isResultSet = stmt.execute();

    if (isResultSet) {
      JdbcResultSet rs = new JdbcResultSet(stmt.getResultSet());

      return rs;
    }
    else {
      return null;
    }
  }

  @Override
  public void close(Result<Void> result)
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
