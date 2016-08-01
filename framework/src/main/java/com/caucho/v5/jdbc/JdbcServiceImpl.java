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
import java.util.LinkedHashMap;
import java.util.Properties;
import java.util.function.Function;
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
  private JdbcConfig _jdbcConfig;

  @Id
  private String _id;
  private String _url;

  private JdbcConnectionImpl _conn;

  // stats
  private long _totalQueryCount;
  private long _failedQueries;

  private LinkedHashMap<Long,QueryResult> _outstandingQueryMap = new LinkedHashMap<>();

  @OnInit
  public void onInit()
    throws Exception
  {
    String address = ServiceRef.current().address() + _id;

    _jdbcConfig = JdbcConfig.from(_config, address);

    _logger.log(Level.INFO, "onInit: id=" + _id + ", service url=" + _url + ", config=" + _jdbcConfig);

    Properties props = new Properties();

    if (_jdbcConfig.user() != null) {
      props.setProperty("user", _jdbcConfig.user());

      if (_jdbcConfig.pass() != null) {
        props.setProperty("password", _jdbcConfig.pass());
      }
    }

    Supplier<JdbcConnectionImpl> supplier
      = new ConnectionSupplier(_jdbcConfig.url(), props, _jdbcConfig.testQueryBefore(), _jdbcConfig.testQueryAfter());

    ServiceBuilder builder = _manager.newService(JdbcConnectionImpl.class, supplier);
    ServiceRef ref = builder.workers(_jdbcConfig.poolSize()).start();

    _conn = ref.as(JdbcConnectionImpl.class);
  }

  @Override
  public void query(Result<JdbcResultSet> result, String sql, Object ... params)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "query: " + toDebugSafe(sql));
    }

    result = new QueryResult<>(result, sql);

    _conn.query(result, sql, params);
  }

  @Override
  public <T> void query(Result<T> result, SqlFunction<T> fun)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "query: " + fun);
    }

    result = new QueryResult<>(result, fun);

    _conn.query(result, fun);
  }

  @Override
  public void query(Result<JdbcResultSet> result, QueryBuilder builder)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "query: " + builder);
    }

    result = new QueryResult<>(result, builder);

    _conn.query(result, builder);
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

  class AfterQueryFun<T> implements Function<T,T> {
    public T apply(T value)
    {
      return value;
    }
  }

  class QueryResult<T> implements Result<T> {
    private Result<T> _result;
    private long _id;

    private QueryStat _stat;

    public QueryResult(Result<T> result, Object query)
    {
      _result = result;
      _id = _totalQueryCount++;
      _stat = new QueryStat(query.toString(), System.currentTimeMillis());

      _outstandingQueryMap.put(_id, this);
    }

    @Override
    public void handle(T value, Throwable fail) throws Exception
    {
      if (fail != null) {
        _failedQueries++;
      }

      _outstandingQueryMap.remove(_id);

      _result.handle(value, fail);
    }

    public QueryStat stat()
    {
      return _stat;
    }
  }
}
