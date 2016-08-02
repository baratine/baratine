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

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import com.caucho.v5.io.IoUtil;

public class ConnectionWrapper implements Connection
{
  private Connection _conn;

  private List<Statement> _openStatementList = new ArrayList<Statement>();

  public ConnectionWrapper(Connection conn)
  {
    _conn = conn;
  }

  public void closeStatements()
  {
    for (Statement stmt : _openStatementList) {
      IoUtil.close(stmt);
    }
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException
  {
    return _conn.unwrap(iface);
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException
  {
    return _conn.isWrapperFor(iface);
  }

  @Override
  public Statement createStatement() throws SQLException
  {
    Statement stmt = _conn.createStatement();

    return new StatementWrapper(this, stmt);
  }

  @Override
  public PreparedStatement prepareStatement(String sql) throws SQLException
  {
    PreparedStatement stmt = _conn.prepareStatement(sql);

    return new PreparedStatementWrapper(this, stmt);
  }

  @Override
  public CallableStatement prepareCall(String sql) throws SQLException
  {
    CallableStatement stmt = _conn.prepareCall(sql);

    return new CallableStatementWrapper(this, stmt);
  }

  @Override
  public String nativeSQL(String sql) throws SQLException
  {
    return _conn.nativeSQL(sql);
  }

  @Override
  public void setAutoCommit(boolean autoCommit) throws SQLException
  {
    _conn.setAutoCommit(autoCommit);
  }

  @Override
  public boolean getAutoCommit() throws SQLException
  {
    return _conn.getAutoCommit();
  }

  @Override
  public void commit() throws SQLException
  {
    _conn.commit();
  }

  @Override
  public void rollback() throws SQLException
  {
    _conn.rollback();
  }

  @Override
  public void close() throws SQLException
  {
    _conn.close();
  }

  @Override
  public boolean isClosed() throws SQLException
  {
    return _conn.isClosed();
  }

  @Override
  public DatabaseMetaData getMetaData() throws SQLException
  {
    return _conn.getMetaData();
  }

  @Override
  public void setReadOnly(boolean readOnly) throws SQLException
  {
    _conn.setReadOnly(readOnly);
  }

  @Override
  public boolean isReadOnly() throws SQLException
  {
    return _conn.isReadOnly();
  }

  @Override
  public void setCatalog(String catalog) throws SQLException
  {
    _conn.setCatalog(catalog);
  }

  @Override
  public String getCatalog() throws SQLException
  {
    return _conn.getCatalog();
  }

  @Override
  public void setTransactionIsolation(int level) throws SQLException
  {
    _conn.setTransactionIsolation(level);
  }

  @Override
  public int getTransactionIsolation() throws SQLException
  {
    return _conn.getTransactionIsolation();
  }

  @Override
  public SQLWarning getWarnings() throws SQLException
  {
    return _conn.getWarnings();
  }

  @Override
  public void clearWarnings() throws SQLException
  {
    _conn.clearWarnings();
  }

  @Override
  public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException
  {
    Statement stmt = _conn.createStatement(resultSetType, resultSetConcurrency);

    return new StatementWrapper(this, stmt);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException
  {
    PreparedStatement stmt = _conn.prepareStatement(sql, resultSetType, resultSetConcurrency);

    return new PreparedStatementWrapper(this, stmt);
  }

  @Override
  public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException
  {
    CallableStatement stmt = _conn.prepareCall(sql, resultSetType, resultSetConcurrency);

    return new CallableStatementWrapper(this, stmt);
  }

  @Override
  public Map<String, Class<?>> getTypeMap() throws SQLException
  {
    return _conn.getTypeMap();
  }

  @Override
  public void setTypeMap(Map<String, Class<?>> map) throws SQLException
  {
    _conn.setTypeMap(map);
  }

  @Override
  public void setHoldability(int holdability) throws SQLException
  {
    _conn.setHoldability(holdability);
  }

  @Override
  public int getHoldability() throws SQLException
  {
    return _conn.getHoldability();
  }

  @Override
  public Savepoint setSavepoint() throws SQLException
  {
    return _conn.setSavepoint();
  }

  @Override
  public Savepoint setSavepoint(String name) throws SQLException
  {
    return _conn.setSavepoint(name);
  }

  @Override
  public void rollback(Savepoint savepoint) throws SQLException
  {
    _conn.rollback(savepoint);
  }

  @Override
  public void releaseSavepoint(Savepoint savepoint) throws SQLException
  {
    _conn.releaseSavepoint(savepoint);
  }

  @Override
  public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
      throws SQLException
  {
    Statement stmt = _conn.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);

    return new StatementWrapper(this, stmt);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
                                            int resultSetHoldability) throws SQLException
  {
    PreparedStatement stmt = _conn.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);

    return new PreparedStatementWrapper(this, stmt);
  }

  @Override
  public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
                                       int resultSetHoldability) throws SQLException
  {
    CallableStatement stmt = _conn.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);

    return new CallableStatementWrapper(this, stmt);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException
  {
    PreparedStatement stmt = _conn.prepareStatement(sql, autoGeneratedKeys);

    return new PreparedStatementWrapper(this, stmt);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException
  {
    PreparedStatement stmt = _conn.prepareStatement(sql, columnIndexes);

    return new PreparedStatementWrapper(this, stmt);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException
  {
    PreparedStatement stmt = _conn.prepareStatement(sql, columnNames);

    return new PreparedStatementWrapper(this, stmt);
  }

  @Override
  public Clob createClob() throws SQLException
  {
    return _conn.createClob();
  }

  @Override
  public Blob createBlob() throws SQLException
  {
    return _conn.createBlob();
  }

  @Override
  public NClob createNClob() throws SQLException
  {
    return _conn.createNClob();
  }

  @Override
  public SQLXML createSQLXML() throws SQLException
  {
    return _conn.createSQLXML();
  }

  @Override
  public boolean isValid(int timeout) throws SQLException
  {
    return _conn.isValid(timeout);
  }

  @Override
  public void setClientInfo(String name, String value) throws SQLClientInfoException
  {
    _conn.setClientInfo(name, value);
  }

  @Override
  public void setClientInfo(Properties properties) throws SQLClientInfoException
  {
    _conn.setClientInfo(properties);
  }

  @Override
  public String getClientInfo(String name) throws SQLException
  {
    return _conn.getClientInfo(name);
  }

  @Override
  public Properties getClientInfo() throws SQLException
  {
    return _conn.getClientInfo();
  }

  @Override
  public Array createArrayOf(String typeName, Object[] elements) throws SQLException
  {
    return _conn.createArrayOf(typeName, elements);
  }

  @Override
  public Struct createStruct(String typeName, Object[] attributes) throws SQLException
  {
    return _conn.createStruct(typeName, attributes);
  }

  @Override
  public void setSchema(String schema) throws SQLException
  {
    _conn.setSchema(schema);
  }

  @Override
  public String getSchema() throws SQLException
  {
    return _conn.getSchema();
  }

  @Override
  public void abort(Executor executor) throws SQLException
  {
    _conn.abort(executor);
  }

  @Override
  public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException
  {
    _conn.setNetworkTimeout(executor, milliseconds);
  }

  @Override
  public int getNetworkTimeout() throws SQLException
  {
    return _conn.getNetworkTimeout();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _conn + "]";
  }
}
