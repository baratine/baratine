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

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;

public class CallableStatementWrapper
  extends PreparedStatementWrapper
  implements CallableStatement
{
  private CallableStatement _stmt;

  public CallableStatementWrapper(ConnectionWrapper conn, CallableStatement stmt)
  {
    super(conn, stmt);

    _stmt = stmt;
  }

  @Override
  public void registerOutParameter(int parameterIndex, int sqlType) throws SQLException
  {
    _stmt.registerOutParameter(parameterIndex, sqlType);
  }

  @Override
  public void registerOutParameter(int parameterIndex, int sqlType, int scale) throws SQLException
  {
    _stmt.registerOutParameter(parameterIndex, sqlType, scale);
  }

  @Override
  public boolean wasNull() throws SQLException
  {
    return _stmt.wasNull();
  }

  @Override
  public String getString(int parameterIndex) throws SQLException
  {
    return _stmt.getString(parameterIndex);
  }

  @Override
  public boolean getBoolean(int parameterIndex) throws SQLException
  {
    return _stmt.getBoolean(parameterIndex);
  }

  @Override
  public byte getByte(int parameterIndex) throws SQLException
  {
    return _stmt.getByte(parameterIndex);
  }

  @Override
  public short getShort(int parameterIndex) throws SQLException
  {
    return _stmt.getShort(parameterIndex);
  }

  @Override
  public int getInt(int parameterIndex) throws SQLException
  {
    return _stmt.getInt(parameterIndex);
  }

  @Override
  public long getLong(int parameterIndex) throws SQLException
  {
    return _stmt.getLong(parameterIndex);
  }

  @Override
  public float getFloat(int parameterIndex) throws SQLException
  {
    return _stmt.getFloat(parameterIndex);
  }

  @Override
  public double getDouble(int parameterIndex) throws SQLException
  {
    return _stmt.getDouble(parameterIndex);
  }

  @Override
  public BigDecimal getBigDecimal(int parameterIndex, int scale) throws SQLException
  {
    return _stmt.getBigDecimal(parameterIndex, scale);
  }

  @Override
  public byte[] getBytes(int parameterIndex) throws SQLException
  {
    return _stmt.getBytes(parameterIndex);
  }

  @Override
  public Date getDate(int parameterIndex) throws SQLException
  {
    return _stmt.getDate(parameterIndex);
  }

  @Override
  public Time getTime(int parameterIndex) throws SQLException
  {
    return _stmt.getTime(parameterIndex);
  }

  @Override
  public Timestamp getTimestamp(int parameterIndex) throws SQLException
  {
    return _stmt.getTimestamp(parameterIndex);
  }

  @Override
  public Object getObject(int parameterIndex) throws SQLException
  {
    return _stmt.getObject(parameterIndex);
  }

  @Override
  public BigDecimal getBigDecimal(int parameterIndex) throws SQLException
  {
    return _stmt.getBigDecimal(parameterIndex);
  }

  @Override
  public Object getObject(int parameterIndex, Map<String, Class<?>> map) throws SQLException
  {
    return _stmt.getObject(parameterIndex, map);
  }

  @Override
  public Ref getRef(int parameterIndex) throws SQLException
  {
    return _stmt.getRef(parameterIndex);
  }

  @Override
  public Blob getBlob(int parameterIndex) throws SQLException
  {
    return _stmt.getBlob(parameterIndex);
  }

  @Override
  public Clob getClob(int parameterIndex) throws SQLException
  {
    return _stmt.getClob(parameterIndex);
  }

  @Override
  public Array getArray(int parameterIndex) throws SQLException
  {
    return _stmt.getArray(parameterIndex);
  }

  @Override
  public Date getDate(int parameterIndex, Calendar cal) throws SQLException
  {
    return _stmt.getDate(parameterIndex, cal);
  }

  @Override
  public Time getTime(int parameterIndex, Calendar cal) throws SQLException
  {
    return _stmt.getTime(parameterIndex, cal);
  }

  @Override
  public Timestamp getTimestamp(int parameterIndex, Calendar cal) throws SQLException
  {
    return _stmt.getTimestamp(parameterIndex, cal);
  }

  @Override
  public void registerOutParameter(int parameterIndex, int sqlType, String typeName) throws SQLException
  {
    _stmt.registerOutParameter(parameterIndex, sqlType, typeName);
  }

  @Override
  public void registerOutParameter(String parameterName, int sqlType) throws SQLException
  {
    _stmt.registerOutParameter(parameterName, sqlType);
  }

  @Override
  public void registerOutParameter(String parameterName, int sqlType, int scale) throws SQLException
  {
    _stmt.registerOutParameter(parameterName, sqlType, scale);
  }

  @Override
  public void registerOutParameter(String parameterName, int sqlType, String typeName) throws SQLException
  {
    _stmt.registerOutParameter(parameterName, sqlType, typeName);
  }

  @Override
  public URL getURL(int parameterIndex) throws SQLException
  {
    return _stmt.getURL(parameterIndex);
  }

  @Override
  public void setURL(String parameterName, URL val) throws SQLException
  {
    _stmt.setURL(parameterName, val);
  }

  @Override
  public void setNull(String parameterName, int sqlType) throws SQLException
  {
    _stmt.setNull(parameterName, sqlType);
  }

  @Override
  public void setBoolean(String parameterName, boolean x) throws SQLException
  {
    _stmt.setBoolean(parameterName, x);
  }

  @Override
  public void setByte(String parameterName, byte x) throws SQLException
  {
    _stmt.setByte(parameterName, x);
  }

  @Override
  public void setShort(String parameterName, short x) throws SQLException
  {
    _stmt.setShort(parameterName, x);
  }

  @Override
  public void setInt(String parameterName, int x) throws SQLException
  {
    _stmt.setInt(parameterName, x);
  }

  @Override
  public void setLong(String parameterName, long x) throws SQLException
  {
    _stmt.setLong(parameterName, x);
  }

  @Override
  public void setFloat(String parameterName, float x) throws SQLException
  {
    _stmt.setFloat(parameterName, x);
  }

  @Override
  public void setDouble(String parameterName, double x) throws SQLException
  {
    _stmt.setDouble(parameterName, x);
  }

  @Override
  public void setBigDecimal(String parameterName, BigDecimal x) throws SQLException
  {
    _stmt.setBigDecimal(parameterName, x);
  }

  @Override
  public void setString(String parameterName, String x) throws SQLException
  {
    _stmt.setString(parameterName, x);
  }

  @Override
  public void setBytes(String parameterName, byte[] x) throws SQLException
  {
    _stmt.setBytes(parameterName, x);
  }

  @Override
  public void setDate(String parameterName, Date x) throws SQLException
  {
    _stmt.setDate(parameterName, x);
  }

  @Override
  public void setTime(String parameterName, Time x) throws SQLException
  {
    _stmt.setTime(parameterName, x);
  }

  @Override
  public void setTimestamp(String parameterName, Timestamp x) throws SQLException
  {
    _stmt.setTimestamp(parameterName, x);
  }

  @Override
  public void setAsciiStream(String parameterName, InputStream x, int length) throws SQLException
  {
    _stmt.setAsciiStream(parameterName, x, length);
  }

  @Override
  public void setBinaryStream(String parameterName, InputStream x, int length) throws SQLException
  {
    _stmt.setBinaryStream(parameterName, x, length);
  }

  @Override
  public void setObject(String parameterName, Object x, int targetSqlType, int scale) throws SQLException
  {
    _stmt.setObject(parameterName, x, targetSqlType, scale);
  }

  @Override
  public void setObject(String parameterName, Object x, int targetSqlType) throws SQLException
  {
    _stmt.setObject(parameterName, x, targetSqlType);
  }

  @Override
  public void setObject(String parameterName, Object x) throws SQLException
  {
    _stmt.setObject(parameterName, x);
  }

  @Override
  public void setCharacterStream(String parameterName, Reader reader, int length) throws SQLException
  {
    _stmt.setCharacterStream(parameterName, reader, length);
  }

  @Override
  public void setDate(String parameterName, Date x, Calendar cal) throws SQLException
  {
    _stmt.setDate(parameterName, x, cal);
  }

  @Override
  public void setTime(String parameterName, Time x, Calendar cal) throws SQLException
  {
    _stmt.setTime(parameterName, x, cal);
  }

  @Override
  public void setTimestamp(String parameterName, Timestamp x, Calendar cal) throws SQLException
  {
    _stmt.setTimestamp(parameterName, x, cal);
  }

  @Override
  public void setNull(String parameterName, int sqlType, String typeName) throws SQLException
  {
    _stmt.setNull(parameterName, sqlType, typeName);
  }

  @Override
  public String getString(String parameterName) throws SQLException
  {
    return _stmt.getString(parameterName);
  }

  @Override
  public boolean getBoolean(String parameterName) throws SQLException
  {
    return _stmt.getBoolean(parameterName);
  }

  @Override
  public byte getByte(String parameterName) throws SQLException
  {
    return _stmt.getByte(parameterName);
  }

  @Override
  public short getShort(String parameterName) throws SQLException
  {
    return _stmt.getShort(parameterName);
  }

  @Override
  public int getInt(String parameterName) throws SQLException
  {
    return _stmt.getInt(parameterName);
  }

  @Override
  public long getLong(String parameterName) throws SQLException
  {
    return _stmt.getLong(parameterName);
  }

  @Override
  public float getFloat(String parameterName) throws SQLException
  {
    return _stmt.getFloat(parameterName);
  }

  @Override
  public double getDouble(String parameterName) throws SQLException
  {
    return _stmt.getDouble(parameterName);
  }

  @Override
  public byte[] getBytes(String parameterName) throws SQLException
  {
    return _stmt.getBytes(parameterName);
  }

  @Override
  public Date getDate(String parameterName) throws SQLException
  {
    return _stmt.getDate(parameterName);
  }

  @Override
  public Time getTime(String parameterName) throws SQLException
  {
    return _stmt.getTime(parameterName);
  }

  @Override
  public Timestamp getTimestamp(String parameterName) throws SQLException
  {
    return _stmt.getTimestamp(parameterName);
  }

  @Override
  public Object getObject(String parameterName) throws SQLException
  {
    return _stmt.getObject(parameterName);
  }

  @Override
  public BigDecimal getBigDecimal(String parameterName) throws SQLException
  {
    return _stmt.getBigDecimal(parameterName);
  }

  @Override
  public Object getObject(String parameterName, Map<String, Class<?>> map) throws SQLException
  {
    return _stmt.getObject(parameterName, map);
  }

  @Override
  public Ref getRef(String parameterName) throws SQLException
  {
    return _stmt.getRef(parameterName);
  }

  @Override
  public Blob getBlob(String parameterName) throws SQLException
  {
    return _stmt.getBlob(parameterName);
  }

  @Override
  public Clob getClob(String parameterName) throws SQLException
  {
    return _stmt.getClob(parameterName);
  }

  @Override
  public Array getArray(String parameterName) throws SQLException
  {
    return _stmt.getArray(parameterName);
  }

  @Override
  public Date getDate(String parameterName, Calendar cal) throws SQLException
  {
    return _stmt.getDate(parameterName, cal);
  }

  @Override
  public Time getTime(String parameterName, Calendar cal) throws SQLException
  {
    return _stmt.getTime(parameterName, cal);
  }

  @Override
  public Timestamp getTimestamp(String parameterName, Calendar cal) throws SQLException
  {
    return _stmt.getTimestamp(parameterName, cal);
  }

  @Override
  public URL getURL(String parameterName) throws SQLException
  {
    return _stmt.getURL(parameterName);
  }

  @Override
  public RowId getRowId(int parameterIndex) throws SQLException
  {
    return _stmt.getRowId(parameterIndex);
  }

  @Override
  public RowId getRowId(String parameterName) throws SQLException
  {
    return _stmt.getRowId(parameterName);
  }

  @Override
  public void setRowId(String parameterName, RowId x) throws SQLException
  {
    _stmt.setRowId(parameterName, x);
  }

  @Override
  public void setNString(String parameterName, String value) throws SQLException
  {
    _stmt.setNString(parameterName, value);
  }

  @Override
  public void setNCharacterStream(String parameterName, Reader value, long length) throws SQLException
  {
    _stmt.setNCharacterStream(parameterName, value, length);
  }

  @Override
  public void setNClob(String parameterName, NClob value) throws SQLException
  {
    _stmt.setNClob(parameterName, value);
  }

  @Override
  public void setClob(String parameterName, Reader reader, long length) throws SQLException
  {
    _stmt.setClob(parameterName, reader, length);
  }

  @Override
  public void setBlob(String parameterName, InputStream inputStream, long length) throws SQLException
  {
    _stmt.setBlob(parameterName, inputStream, length);
  }

  @Override
  public void setNClob(String parameterName, Reader reader, long length) throws SQLException
  {
    _stmt.setNClob(parameterName, reader, length);
  }

  @Override
  public NClob getNClob(int parameterIndex) throws SQLException
  {
    return _stmt.getNClob(parameterIndex);
  }

  @Override
  public NClob getNClob(String parameterName) throws SQLException
  {
    return _stmt.getNClob(parameterName);
  }

  @Override
  public void setSQLXML(String parameterName, SQLXML xmlObject) throws SQLException
  {
    _stmt.setSQLXML(parameterName, xmlObject);
  }

  @Override
  public SQLXML getSQLXML(int parameterIndex) throws SQLException
  {
    return _stmt.getSQLXML(parameterIndex);
  }

  @Override
  public SQLXML getSQLXML(String parameterName) throws SQLException
  {
    return _stmt.getSQLXML(parameterName);
  }

  @Override
  public String getNString(int parameterIndex) throws SQLException
  {
    return _stmt.getNString(parameterIndex);
  }

  @Override
  public String getNString(String parameterName) throws SQLException
  {
    return _stmt.getNString(parameterName);
  }

  @Override
  public Reader getNCharacterStream(int parameterIndex) throws SQLException
  {
    return _stmt.getNCharacterStream(parameterIndex);
  }

  @Override
  public Reader getNCharacterStream(String parameterName) throws SQLException
  {
    return _stmt.getNCharacterStream(parameterName);
  }

  @Override
  public Reader getCharacterStream(int parameterIndex) throws SQLException
  {
    return _stmt.getCharacterStream(parameterIndex);
  }

  @Override
  public Reader getCharacterStream(String parameterName) throws SQLException
  {
    return _stmt.getCharacterStream(parameterName);
  }

  @Override
  public void setBlob(String parameterName, Blob x) throws SQLException
  {
    _stmt.setBlob(parameterName, x);
  }

  @Override
  public void setClob(String parameterName, Clob x) throws SQLException
  {
    _stmt.setClob(parameterName, x);
  }

  @Override
  public void setAsciiStream(String parameterName, InputStream x, long length) throws SQLException
  {
    _stmt.setAsciiStream(parameterName, x, length);
  }

  @Override
  public void setBinaryStream(String parameterName, InputStream x, long length) throws SQLException
  {
    _stmt.setBinaryStream(parameterName, x, length);
  }

  @Override
  public void setCharacterStream(String parameterName, Reader reader, long length) throws SQLException
  {
    _stmt.setCharacterStream(parameterName, reader, length);
  }

  @Override
  public void setAsciiStream(String parameterName, InputStream x) throws SQLException
  {
    _stmt.setAsciiStream(parameterName, x);
  }

  @Override
  public void setBinaryStream(String parameterName, InputStream x) throws SQLException
  {
    _stmt.setBinaryStream(parameterName, x);
  }

  @Override
  public void setCharacterStream(String parameterName, Reader reader) throws SQLException
  {
    _stmt.setCharacterStream(parameterName, reader);
  }

  @Override
  public void setNCharacterStream(String parameterName, Reader value) throws SQLException
  {
    _stmt.setNCharacterStream(parameterName, value);
  }

  @Override
  public void setClob(String parameterName, Reader reader) throws SQLException
  {
    _stmt.setClob(parameterName, reader);
  }

  @Override
  public void setBlob(String parameterName, InputStream inputStream) throws SQLException
  {
    _stmt.setBlob(parameterName, inputStream);
  }

  @Override
  public void setNClob(String parameterName, Reader reader) throws SQLException
  {
    _stmt.setNClob(parameterName, reader);
  }

  @Override
  public <T> T getObject(int parameterIndex, Class<T> type) throws SQLException
  {
    return _stmt.getObject(parameterIndex, type);
  }

  @Override
  public <T> T getObject(String parameterName, Class<T> type) throws SQLException
  {
    return _stmt.getObject(parameterName, type);
  }
}
