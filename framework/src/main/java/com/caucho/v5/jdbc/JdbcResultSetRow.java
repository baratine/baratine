package com.caucho.v5.jdbc;

import java.util.LinkedHashMap;

public class JdbcResultSetRow
{
  private LinkedHashMap<String,Object> _map;

  private Object[] _mapArray;

  protected JdbcResultSetRow(LinkedHashMap<String,Object> map)
  {
    _map = map;


  }

  public Object get(int index)
  {
    return _mapArray[index];
  }

  public Object get(String name)
  {
    return _map.get(name);
  }
}
