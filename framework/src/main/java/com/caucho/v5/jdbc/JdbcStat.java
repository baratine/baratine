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

import java.util.ArrayList;
import java.util.List;

public class JdbcStat
{
  private long _totalQueryCount;
  private long _totalFailedCount;

  private List<QueryStat> _outstandingQueryList = new ArrayList<>();
  private List<QueryStat> _recentQueryList = new ArrayList<>();
  private List<QueryStat> _recentFailedList = new ArrayList<>();

  public long totalQueryCount()
  {
    return _totalQueryCount;
  }

  public JdbcStat totalQueryCount(long count)
  {
    _totalQueryCount = count;

    return this;
  }

  public long totalFailedCount()
  {
    return _totalFailedCount;
  }

  public JdbcStat totalFailedCount(long count)
  {
    _totalFailedCount = count;

    return this;
  }

  /**
   * Returns queries that are still executing.
   *
   * @return
   */
  public List<QueryStat> outstandingQueries()
  {
    return _outstandingQueryList;
  }

  public JdbcStat outstandingQuery(QueryStat stat)
  {
    _outstandingQueryList.add(stat);

    return this;
  }

  /**
   * Returns recent queries, included failed ones.
   *
   * @return
   */
  public List<QueryStat> recentQueries()
  {
    return _recentQueryList;
  }

  public JdbcStat recentQuery(QueryStat stat)
  {
    _recentQueryList.add(stat);

    return this;
  }

  /**
   * Returns recent failed queries.
   *
   * @return
   */
  public List<QueryStat> recentFailed()
  {
    return _recentFailedList;
  }

  public JdbcStat recentFailed(QueryStat stat)
  {
    _recentFailedList.add(stat);

    return this;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[total=" + totalQueryCount()
                                      + ", failed=" + totalFailedCount()
                                      + ", active=" + _outstandingQueryList.size()
                                      + "]";
  }
}
