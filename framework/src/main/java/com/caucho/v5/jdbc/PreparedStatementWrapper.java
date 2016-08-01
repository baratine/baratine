package com.caucho.v5.jdbc;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;

public class PreparedStatementWrapper extends StatementWrapper implements PreparedStatement
{
  private PreparedStatement _stmt;

  public PreparedStatementWrapper(ConnectionWrapper conn, PreparedStatement stmt)
  {
    super(conn, stmt);

    _stmt = stmt;
  }

  @Override
  public ResultSet executeQuery() throws SQLException
  {
    return _stmt.executeQuery();
  }

  @Override
  public int executeUpdate() throws SQLException
  {
    return _stmt.executeUpdate();
  }

  @Override
  public void setNull(int parameterIndex, int sqlType) throws SQLException
  {
    _stmt.setNull(parameterIndex, sqlType);
  }

  @Override
  public void setBoolean(int parameterIndex, boolean x) throws SQLException
  {
    _stmt.setBoolean(parameterIndex, x);
  }

  @Override
  public void setByte(int parameterIndex, byte x) throws SQLException
  {
    _stmt.setByte(parameterIndex, x);
  }

  @Override
  public void setShort(int parameterIndex, short x) throws SQLException
  {
    _stmt.setShort(parameterIndex, x);
  }

  @Override
  public void setInt(int parameterIndex, int x) throws SQLException
  {
    _stmt.setInt(parameterIndex, x);
  }

  @Override
  public void setLong(int parameterIndex, long x) throws SQLException
  {
    _stmt.setLong(parameterIndex, x);
  }

  @Override
  public void setFloat(int parameterIndex, float x) throws SQLException
  {
    _stmt.setFloat(parameterIndex, x);
  }

  @Override
  public void setDouble(int parameterIndex, double x) throws SQLException
  {
    _stmt.setDouble(parameterIndex, x);
  }

  @Override
  public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException
  {
    _stmt.setBigDecimal(parameterIndex, x);
  }

  @Override
  public void setString(int parameterIndex, String x) throws SQLException
  {
    _stmt.setString(parameterIndex, x);
  }

  @Override
  public void setBytes(int parameterIndex, byte[] x) throws SQLException
  {
    _stmt.setBytes(parameterIndex, x);
  }

  @Override
  public void setDate(int parameterIndex, Date x) throws SQLException
  {
    _stmt.setDate(parameterIndex, x);
  }

  @Override
  public void setTime(int parameterIndex, Time x) throws SQLException
  {
    _stmt.setTime(parameterIndex, x);
  }

  @Override
  public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException
  {
    _stmt.setTimestamp(parameterIndex, x);
  }

  @Override
  public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException
  {
    _stmt.setAsciiStream(parameterIndex, x, length);
  }

  @Override
  public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException
  {
    _stmt.setUnicodeStream(parameterIndex, x, length);
  }

  @Override
  public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException
  {
    _stmt.setBinaryStream(parameterIndex, x, length);
  }

  @Override
  public void clearParameters() throws SQLException
  {
    _stmt.clearParameters();
  }

  @Override
  public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException
  {
    _stmt.setObject(parameterIndex, x, targetSqlType);
  }

  @Override
  public void setObject(int parameterIndex, Object x) throws SQLException
  {
    _stmt.setObject(parameterIndex, x);
  }

  @Override
  public boolean execute() throws SQLException
  {
    return _stmt.execute();
  }

  @Override
  public void addBatch() throws SQLException
  {
    _stmt.addBatch();
  }

  @Override
  public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException
  {
    _stmt.setCharacterStream(parameterIndex, reader, length);
  }

  @Override
  public void setRef(int parameterIndex, Ref x) throws SQLException
  {
    _stmt.setRef(parameterIndex, x);
  }

  @Override
  public void setBlob(int parameterIndex, Blob x) throws SQLException
  {
    _stmt.setBlob(parameterIndex, x);
  }

  @Override
  public void setClob(int parameterIndex, Clob x) throws SQLException
  {
    _stmt.setClob(parameterIndex, x);
  }

  @Override
  public void setArray(int parameterIndex, Array x) throws SQLException
  {
    _stmt.setArray(parameterIndex, x);
  }

  @Override
  public ResultSetMetaData getMetaData() throws SQLException
  {
    return _stmt.getMetaData();
  }

  @Override
  public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException
  {
    _stmt.setDate(parameterIndex, x, cal);
  }

  @Override
  public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException
  {
    _stmt.setTime(parameterIndex, x, cal);
  }

  @Override
  public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException
  {
    _stmt.setTimestamp(parameterIndex, x, cal);
  }

  @Override
  public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException
  {
    _stmt.setNull(parameterIndex, sqlType, typeName);
  }

  @Override
  public void setURL(int parameterIndex, URL x) throws SQLException
  {
    _stmt.setURL(parameterIndex, x);
  }

  @Override
  public ParameterMetaData getParameterMetaData() throws SQLException
  {
    return _stmt.getParameterMetaData();
  }

  @Override
  public void setRowId(int parameterIndex, RowId x) throws SQLException
  {
    _stmt.setRowId(parameterIndex, x);
  }

  @Override
  public void setNString(int parameterIndex, String value) throws SQLException
  {
    _stmt.setNString(parameterIndex, value);
  }

  @Override
  public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException
  {
    _stmt.setNCharacterStream(parameterIndex, value, length);
  }

  @Override
  public void setNClob(int parameterIndex, NClob value) throws SQLException
  {
    _stmt.setNClob(parameterIndex, value);
  }

  @Override
  public void setClob(int parameterIndex, Reader reader, long length) throws SQLException
  {
    _stmt.setClob(parameterIndex, reader, length);
  }

  @Override
  public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException
  {
    _stmt.setBlob(parameterIndex, inputStream, length);
  }

  @Override
  public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException
  {
    _stmt.setNClob(parameterIndex, reader, length);
  }

  @Override
  public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException
  {
    _stmt.setSQLXML(parameterIndex, xmlObject);
  }

  @Override
  public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException
  {
    _stmt.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
  }

  @Override
  public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException
  {
    _stmt.setAsciiStream(parameterIndex, x, length);
  }

  @Override
  public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException
  {
    _stmt.setBinaryStream(parameterIndex, x, length);
  }

  @Override
  public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException
  {
    _stmt.setCharacterStream(parameterIndex, reader, length);
  }

  @Override
  public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException
  {
    _stmt.setAsciiStream(parameterIndex, x);
  }

  @Override
  public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException
  {
    _stmt.setBinaryStream(parameterIndex, x);
  }

  @Override
  public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException
  {
    _stmt.setCharacterStream(parameterIndex, reader);
  }

  @Override
  public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException
  {
    _stmt.setNCharacterStream(parameterIndex, value);
  }

  @Override
  public void setClob(int parameterIndex, Reader reader) throws SQLException
  {
    _stmt.setClob(parameterIndex, reader);
  }

  @Override
  public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException
  {
    _stmt.setBlob(parameterIndex, inputStream);
  }

  @Override
  public void setNClob(int parameterIndex, Reader reader) throws SQLException
  {
    _stmt.setNClob(parameterIndex, reader);
  }
}
