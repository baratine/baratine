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

import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import io.baratine.config.Config;
import io.baratine.jdbc.JdbcConfig;
import io.baratine.jdbc.JdbcResultSet;
import io.baratine.jdbc.JdbcService;
import io.baratine.jdbc.JdbcStat;
import io.baratine.jdbc.QueryStat;
import io.baratine.jdbc.SqlBiFunction;
import io.baratine.jdbc.SqlFunction;
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
  private Config _config;
  private JdbcConfig _jdbcConfig;

  @Id
  private String _id;

  private JdbcConnection _conn;

  // stats
  private long _totalQueryCount;
  private long _totalFailedCount;

  private LinkedHashMap<Long,QueryResult<?>> _outstandingQueryMap = new LinkedHashMap<>();

  private LinkedList<QueryStat> _recentQueryList = new LinkedList<>();
  private LinkedList<QueryStat> _recentFailedList = new LinkedList<>();

  public JdbcServiceImpl()
  {
  }

  @OnInit
  public void onInit()
    throws Exception
  {
    String address = ServiceRef.current().address() + _id;

    _logger.log(Level.CONFIG, "onInit: id=" + _id + ", service address=" + address);

    _jdbcConfig = JdbcConfig.from(_config, address);

    _logger.log(Level.CONFIG, "onInit: config=" + _jdbcConfig);

    Properties props = new Properties();

    if (_jdbcConfig.user() != null) {
      props.setProperty("user", _jdbcConfig.user());

      if (_jdbcConfig.pass() != null) {
        props.setProperty("password", _jdbcConfig.pass());
      }
    }

    Supplier<JdbcConnection> supplier
      = new ConnectionSupplier(_jdbcConfig.url(), props, _jdbcConfig.testQueryBefore(), _jdbcConfig.testQueryAfter());

    ServiceBuilder builder = Services.current().newService(JdbcConnection.class, supplier);
    ServiceRef ref = builder.workers(_jdbcConfig.poolSize()).start();

    _conn = ref.as(JdbcConnection.class);
  }

  @Override
  public void execute(Result<Integer> result, String sql, Object ... params)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "query: " + toDebugSafe(sql));
    }

    QueryResult<Integer> qResult = new QueryResult<>(result, sql);

    _conn.execute(qResult, sql, params);
  }

  @Override
  public void query(Result<JdbcResultSet> result, String sql, Object ... params)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "query: " + toDebugSafe(sql));
    }

    QueryResult<JdbcResultSet> qResult = new QueryResult<>(result, sql);

    _conn.query(qResult, sql, params);
  }

  @Override
  public <T> void query(Result<T> result, SqlFunction<T> fun)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "query: " + fun);
    }

    QueryResult<T> qResult = new QueryResult<>(result, fun);

    _conn.query(qResult, fun);
  }

  @Override
  public <T> void query(Result<T> result, SqlBiFunction<T> fun, Object ... params)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "query: " + fun);
    }

    QueryResult<T> qResult = new QueryResult<>(result, fun);

    _conn.query(qResult, fun, params);
  }

  @Override
  public void stats(Result<JdbcStat> result)
  {
    JdbcStat stat = new JdbcStat();

    stat.totalQueryCount(_totalQueryCount);
    stat.totalFailedCount(_totalFailedCount);

    for (QueryResult<?> qResult : _outstandingQueryMap.values()) {
      QueryStat qStat = qResult.stat();

      stat.outstandingQuery(qStat);
    }

    for (QueryStat qStat : _recentQueryList) {
      stat.recentQuery(qStat);
    }

    for (QueryStat qStat : _recentFailedList) {
      stat.recentFailed(qStat);
    }

    result.ok(stat);
  }

  private String toDebugSafe(String str)
  {
    int len = Math.min(32, str.length());

    return str.substring(0, len) + "...";
  }

  static class ConnectionSupplier implements Supplier<JdbcConnection> {
    private JdbcServiceImpl _parentRef;

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

    public JdbcConnection get()
    {
      return JdbcConnection.create(_count++, _url, _props,
                                   _testQueryBefore, _testQueryAfter);
    }
  }

  class AfterQueryFun<T> implements Function<T,T> {
    public T apply(T value)
    {
      return value;
    }
  }

  class QueryResult<T> implements Result<WrappedValue<T>> {
    private Result<T> _result;
    private long _queryId;

    private QueryStat _stat;

    public QueryResult(Result<T> result, Object query)
    {
      _result = result;
      _queryId = _totalQueryCount++;
      _stat = new QueryStat(query.toString());

      _stat.enqueueStartTimeMs(System.currentTimeMillis());

      _outstandingQueryMap.put(_queryId, this);
    }

    @Override
    public void handle(WrappedValue<T> value, Throwable fail) throws Exception
    {
      _outstandingQueryMap.remove(_queryId);

      if (fail == null) {
        fail = value.exception();
      }

      try {
        if (value != null) {
          _stat.enqueueEndTimeMs(value.startTimeMs());
          _stat.startTimeMs(value.startTimeMs());
          _stat.endTimeMs(value.endTimeMs());
        }
        else {
          _stat.endTimeMs(System.currentTimeMillis());
        }

        _recentQueryList.addLast(_stat);

        if (_recentQueryList.size() > 512) {
          _recentQueryList.removeFirst();
        }

        if (fail != null) {
          if (_logger.isLoggable(Level.FINER)) {
            _logger.log(Level.FINER, "query failed: id=" + _queryId
                                                         + ", time=" + (_stat.endTimeMs() - _stat.startTimeMs()) + "ms"
                                                         + ", query=" + _stat.query()
                                                         + ", exception=" + fail);
          }

          _totalFailedCount++;
          _recentFailedList.addLast(_stat);

          if (_recentFailedList.size() > 512) {
            _recentFailedList.removeFirst();
          }

          _stat.exception(fail);

          _result.fail(fail);
        }
        else {
          _logger.log(Level.FINER, "query completed: id=" + _queryId
                                                          + ", time=" + (_stat.endTimeMs() - _stat.startTimeMs()) + "ms"
                                                          + ", query=" + _stat.query());
        }

        _result.ok(value.value());
      }
      catch (Throwable e) {
        e.printStackTrace();

        _result.fail(e);
      }
    }

    public long queryId()
    {
      return _queryId;
    }

    public QueryStat stat()
    {
      return _stat;
    }
  }
}
