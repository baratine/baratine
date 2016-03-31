package com.caucho.v5.jdbc;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import io.baratine.service.OnInit;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.service.Services;
import io.baratine.service.ServiceRef;

@Service
public class JdbcConnectionPoolImpl //implements JdbcConnection
{
  private Logger _logger = Logger.getLogger(JdbcConnectionPoolImpl.class.toString());

  @Inject
  private Services _manager;

  @Inject
  private JdbcServiceImpl _jdbcService;

  private TreeSet<Entry> _idleList = new TreeSet<>();
  private int _busyCount = 0;

  private LinkedList<Result<JdbcConnection>> _waitingList = new LinkedList<>();

  private int _minCapacity = 4;
  private int _maxCapacity = 128;

  private long _timeoutMs = 1000 * 60 * 5;

  private JdbcConnection _self;
  private JdbcConnectionSync _selfSync;

  private String _url;
  private Properties _props;

  private boolean _isClosing;

  @OnInit
  public void onInit()
  {
    _self = ServiceRef.current().as(JdbcConnection.class);
    _selfSync = ServiceRef.current().as(JdbcConnectionSync.class);
  }

  /*
  @Override
  public void execute(String sql, Result<Integer> result)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "execute: " + sql.substring(0, 16) + "...");
    }

    getConnection((conn, e) -> {
      conn.execute(sql, new ConnResult<Integer>(conn, result));
    });
  }

  @Override
  public void executeBatch(String[] sqls, Result<Integer[]> result)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "executeBatch: " + sqls.length);
    }

    getConnection((conn, e) -> {
      conn.executeBatch(sqls, new ConnResult<Integer[]>(conn, result));
    });
  }

  @Override
  public void query(String sql, Result<JdbcResultSet> result)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "query: " + sql.substring(0, 16) + "...");
    }

    getConnection((conn, e) -> {
      conn.query(sql, new ConnResult<JdbcResultSet>(conn, result));
    });
  }

  @Override
  public void queryParam(String sql, Object[] params, Result<JdbcResultSet> result)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "queryParam: " + sql);
    }

    getConnection((conn, e) -> {
      conn.queryParam(sql, params, new ConnResult<JdbcResultSet>(conn, result));
    });
  }

  @Override
  public void queryBatch(String sql, Object[][] paramsList, Result<List<JdbcResultSet>> result)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "queryBatch: " + sql);
    }

    getConnection((conn, e) -> {
      conn.queryBatch(sql, paramsList, new ConnResult<List<JdbcResultSet>>(conn, result));
    });
  }

  @Override
  public void queryBatch(String[] sqls, Object[][] paramsList, Result<List<JdbcResultSet>> result)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "queryBatch: " + sqls.length);
    }

    getConnection((conn, e) -> {
      conn.queryBatch(sqls, paramsList, new ConnResult<List<JdbcResultSet>>(conn, result));
    });
  }

  @Override
  public void close(Result<Void> result)
  {
    if (_logger.isLoggable(Level.FINE)) {
      _logger.log(Level.FINE, "connection pool is closing: " + this);
    }

    _isClosing = true;

    for (Entry e : _idleList) {
      JdbcConnection conn = e.getConnection();

      ServiceRef.toRef(conn).close();
    }

    for (Result<JdbcConnection> r : _waitingList) {
      r.fail(new SQLException("database pool is closing"));
    }
  }
  */

  private void checkPool()
  {
    if (_isClosing) {
      return;
    }

    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "connection pool state: idle=" + _idleList.size() + ",busy=" + _busyCount);
    }

    Iterator<Entry> iter = _idleList.iterator();

    while (iter.hasNext()) {
      Entry entry = iter.next();

      if (entry.isExpired(_timeoutMs)) {
        iter.remove();

        JdbcConnection conn = entry.getConnection();

        if (_logger.isLoggable(Level.FINE)) {
          _logger.log(Level.FINE, "closing expired connection service: " + conn);
        }

        ServiceRef.toRef(conn).close();
      }
      else {
        break;
      }
    }

    int totalSize = _idleList.size() + _busyCount;
    int newCount;

    if (totalSize < _minCapacity) {
      newCount = _minCapacity - totalSize;
    }
    else if (_idleList.size() > 0) {
      newCount = 0;
    }
    else if (_busyCount < _maxCapacity) {
      newCount = 1;
    }
    else {
      newCount = 0;
    }

    _busyCount += newCount;

    if (newCount > 0) {
      if (_logger.isLoggable(Level.FINE)) {
        _logger.log(Level.FINE, "creating " + newCount + " new connections");
      }
    }

    /*
    for (int i = 0; i < newCount; i++) {
      ServiceRef ref = _manager.newService(JdbcConnectionImpl.class).start();

      if (_logger.isLoggable(Level.FINE)) {
        _logger.log(Level.FINE, "creating new connection service: " + ref);
      }

      JdbcConnection conn = ref.as(JdbcConnection.class);

      conn.connect(_url, _props, (c, e) -> {
        if (_logger.isLoggable(Level.FINER)) {
          _logger.log(Level.FINER, "connection established: idle=" + _idleList.size() + ",busy=" + _busyCount);
        }

        if (e != null) {
          e.printStackTrace();
        }
        else {
          addIdle(c);
        }
      });
    }
    */
  }

  private void addIdle(JdbcConnection conn)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "adding idle connection service: idle=" + _idleList.size() + ",busy=" + _busyCount);
    }

    _busyCount--;

    if (! _isClosing) {
      _idleList.add(new Entry(conn));

      notifyWaitingList();
    }
  }

  private void failIdle(Throwable e, JdbcConnection conn)
  {
    if (_logger.isLoggable(Level.FINER)) {
      _logger.log(Level.FINER, "fail idle connection service: idle=" + _idleList.size() + ",busy=" + _busyCount);
      _logger.log(Level.FINER, e.getMessage(), e);
    }

    _busyCount--;

    checkPool();
  }

  private void notifyWaitingList()
  {
    if (_isClosing) {
      return;
    }

    Result<JdbcConnection> result = _waitingList.pollFirst();

    if (result != null) {
      Entry entry = _idleList.pollFirst();

      if (entry != null) {
        _busyCount++;

        result.ok(entry.getConnection());
      }
      else {
        _waitingList.offerFirst(result);
      }
    }
  }

  private void getConnection(Result<JdbcConnection> result)
  {
    _waitingList.add(result);

    checkPool();
  }

  class ConnResult<T> implements Result<T> {
    private JdbcConnection _conn;
    private Result<T> _result;

    public ConnResult(JdbcConnection conn, Result<T> result)
    {
      _conn = conn;
      _result = result;
    }

    @Override
    public void handle(T value, Throwable fail) throws Exception
    {
      if (_logger.isLoggable(Level.FINER)) {
        _logger.log(Level.FINER, "query completed: idle=" + _idleList.size() + ",busy=" + _busyCount);
      }

      _result.handle(value, fail);

      if (fail != null) {
        failIdle(fail, _conn);
      }
      else {
        addIdle(_conn);
      }
    }
  }

  static class Entry implements Comparable<Entry> {
    private JdbcConnection _conn;
    private long _lastActivityTime;

    public Entry(JdbcConnection conn)
    {
      _conn = conn;
      _lastActivityTime = System.currentTimeMillis();
    }

    public JdbcConnection getConnection()
    {
      return _conn;
    }

    public boolean isExpired(long timeoutMs)
    {
      return System.currentTimeMillis() > _lastActivityTime + timeoutMs;
    }

    @Override
    public int compareTo(Entry e)
    {
      return (int) (_lastActivityTime - e._lastActivityTime);
    }
  }
}
