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

import java.util.Properties;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import io.baratine.config.Config;
import io.baratine.service.OnInit;
import io.baratine.service.Result;
import io.baratine.service.Services;
import io.baratine.vault.Id;
import io.baratine.service.ServiceRef;
import io.baratine.service.ServiceRef.ServiceBuilder;

public class JdbcServiceImpl implements JdbcService
{
  private static Logger _logger = Logger.getLogger(JdbcServiceImpl.class.toString());

  @Inject
  private Services _manager;

  @Inject
  private Config _config;

  @Id
  private String _id;
  private String _url;

  private JdbcConnectionImpl _conn;

  @OnInit
  public void onInit(Result<Void> result)
  {
    _url = ServiceRef.current().address() + _id;
    JdbcConfig c = JdbcConfig.from(_config, _url);

    _logger.log(Level.INFO, "onInit: id=" + _id + ", service url=" + _url + ", config=" + c);

    Properties props = new Properties();

    if (c.user() != null) {
      props.setProperty("user", c.user());

      if (c.pass() != null) {
        props.setProperty("password", c.pass());
      }
    }

    Supplier<JdbcConnectionImpl> supplier
      = new ConnectionSupplier(c.url(), props, c.testQueryBefore(), c.testQueryAfter());

    ServiceBuilder builder = _manager.newService(JdbcConnectionImpl.class, supplier);
    ServiceRef ref = builder.workers(c.poolSize()).start();

    _conn = ref.as(JdbcConnectionImpl.class);

    result.ok(null);
  }

  @Override
  public void execute(Result<Integer> result, String sql, Object ... params)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "execute: " + toDebugSafe(sql));
    }

    _conn.execute(result , sql, params);
  }

  @Override
  public void executeBatch(Result<Integer[]> result, String sql, Object[] ... params)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "executeBatch: " + toDebugSafe(sql));
    }

    _conn.executeBatch(result, sql, params);
  }

  @Override
  public void executeBatch(Result<Integer[]> result, String[] sqlList, Object[] ... params)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "executeBatch: " + sqlList.length);
    }

    _conn.executeBatch(result, sqlList, params);
  }

  @Override
  public void query(Result<JdbcResultSet> result, String sql, Object ... params)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "query: " + toDebugSafe(sql));
    }

    _conn.query(result, sql, params);
  }

  @Override
  public void queryBatch(Result<JdbcResultSet[]> result, String sql, Object[] ... paramsList)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "queryBatch: " + toDebugSafe(sql));
    }

    _conn.queryBatch(result, sql, paramsList);
  }

  @Override
  public void queryBatch(Result<JdbcResultSet[]> result, String[] sqlList, Object[] ... paramsList)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "queryBatch: " + sqlList.length);
    }

    _conn.queryBatch(result, sqlList, paramsList);
  }

  private String toDebugSafe(String str)
  {
    int len = Math.min(32, str.length());

    return str.substring(0, len);
  }

  static class ConnectionSupplier implements Supplier<JdbcConnectionImpl> {
    private String _url;
    private Properties _props;

    private String _testQueryBefore;
    private String _testQueryAfter;

    private int _count;

    public ConnectionSupplier(String url, Properties props,
                              String testQueryBefore, String testQueryAfter)
    {
      _url = url;
      _props = props;

      _testQueryBefore = testQueryBefore;
      _testQueryAfter = testQueryAfter;
    }

    public JdbcConnectionImpl get()
    {
      return JdbcConnectionImpl.create(_count++, _url, _props,
                                       _testQueryBefore, _testQueryAfter);
    }
  }
}
