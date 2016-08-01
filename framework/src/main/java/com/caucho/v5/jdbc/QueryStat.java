package com.caucho.v5.jdbc;

public class QueryStat
{
  private String _query;
  private long _startTimeMs;

  public QueryStat(String query, long startTimeMs)
  {
    _query = query;
    _startTimeMs= startTimeMs;
  }

  public String query()
  {
    return _query;
  }

  public long startTimeMs()
  {
    return _startTimeMs;
  }
}
