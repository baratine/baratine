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

package io.baratine.jdbc;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

public class JdbcRowSet implements Iterable<JdbcRow>
{
  private static final String[] EMPTY = new String[0];

  private ArrayList<JdbcRow> _rowList = new ArrayList<>();

  private String[] _columnNames = EMPTY;

  private int _updateCount;
  private int _columnCount;

  public static JdbcRowSet create(ResultSet rs, int updateCount) throws SQLException
  {
    JdbcRowSet jdbcRs = new JdbcRowSet();

    if (rs != null) {
      ResultSetMetaData meta = rs.getMetaData();

      int columnCount = meta.getColumnCount();
      String[] columnNames = new String[columnCount];

      for (int i = 0; i < columnCount; i++) {
        columnNames[i] = meta.getColumnName(i + 1);
      }

      jdbcRs._columnNames = columnNames;
      jdbcRs._columnCount = columnCount;

      while (rs.next()) {
        Object[] values = new Object[columnCount];

        for (int i = 0; i < columnCount; i++) {
          values[i] = rs.getObject(i + 1);
        }

        JdbcRow row = new JdbcRow(values);

        jdbcRs._rowList.add(row);
      }
    }

    jdbcRs._updateCount = updateCount;

    return jdbcRs;
  }

  public String[] getColumnNames()
  {
    return _columnNames;
  }

  public int getColumnCount()
  {
    return _columnCount;
  }

  public int getUpdateCount()
  {
    return _updateCount;
  }

  public int getRowCount()
  {
    return _rowList.size();
  }

  public JdbcRow getFirstRow()
  {
    if (getRowCount() > 0) {
      return _rowList.get(0);
    }
    else {
      return null;
    }
  }

  @Override
  public Iterator<JdbcRow> iterator()
  {
    return _rowList.iterator();
  }

  @Override
  public String toString()
  {
    ArrayList<ArrayList<Map.Entry<String,Object>>> list = new ArrayList<>();

    for (int i = 0; i < _rowList.size(); i++) {
      ArrayList<Map.Entry<String,Object>> row = new ArrayList<>();

      JdbcRow rowSet = _rowList.get(i);

      for (int j = 1; j <= rowSet.getColumnCount(); j++) {
        String name = _columnNames[j];
        Object value = rowSet.getObject(j);

        row.add(new SimpleEntry<String,Object>(name, value));
      }

      list.add(row);
    }

    if (list.size() > 0) {
      return list.toString();
    }
    else {
      return String.valueOf(_updateCount);
    }
  }
}
