package io.baratine.jdbc;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

public class JdbcResultSet implements Iterable<JdbcRowSet>
{
  private static final String[] EMPTY = new String[0];

  private ArrayList<JdbcRowSet> _rowList = new ArrayList<>();

  private String[] _columnNames = EMPTY;

  private int _updateCount;
  private int _columnCount;

  public static JdbcResultSet create(ResultSet rs, int updateCount) throws SQLException
  {
    JdbcResultSet jdbcRs = new JdbcResultSet();

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

        JdbcRowSet row = new JdbcRowSet(values);

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

  public JdbcRowSet getFirstRow()
  {
    if (getRowCount() > 0) {
      return _rowList.get(0);
    }
    else {
      return null;
    }
  }

  @Override
  public Iterator<JdbcRowSet> iterator()
  {
    return _rowList.iterator();
  }

  @Override
  public String toString()
  {
    ArrayList<ArrayList<Map.Entry<String,Object>>> list = new ArrayList<>();

    for (int i = 0; i < _rowList.size(); i++) {
      ArrayList<Map.Entry<String,Object>> row = new ArrayList<>();

      JdbcRowSet rowSet = _rowList.get(i);

      for (int j = 0; j < rowSet.getColumnCount(); j++) {
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
