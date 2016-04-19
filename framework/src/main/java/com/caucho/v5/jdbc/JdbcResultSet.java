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
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JdbcResultSet implements Iterable<Map<String,Object>>
{
  private List<String> _nameList = new ArrayList<>();
  private List<Map<String,Object>> _rows = new ArrayList<>();

  public JdbcResultSet()
  {
  }

  public JdbcResultSet(ResultSet rs)
    throws SQLException
  {
    ResultSetMetaData md = rs.getMetaData();

    int count = md.getColumnCount();

    for (int i = 0; i < count; i++) {
      String name = md.getColumnName(i + 1);

      _nameList.add(name);
    }

    while (rs.next()) {
      LinkedHashMap<String,Object> map = new LinkedHashMap<>();

      for (int i = 0; i < count; i++) {
        Object obj = rs.getObject(i + 1);

        map.put(_nameList.get(i), obj);
      }

      _rows.add(map);
    }
  }

  public List<String> getColumnNames()
  {
    return _nameList;
  }

  public int getRowCount()
  {
    return _rows.size();
  }

  public Map<String,Object> getFirstRow()
  {
    if (_rows.size() > 0) {
      return _rows.get(0);
    }
    else {
      return null;
    }
  }

  public List<Map<String,Object>> getRows()
  {
    return _rows;
  }

  @Override
  public Iterator<Map<String, Object>> iterator()
  {
    return _rows.iterator();
  }
}
