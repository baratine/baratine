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

public class QueryStat
{
  private String _query;

  private long _enqueueStartTimeMs;
  private long _enqueueEndTimeMs;

  private long _startTimeMs;
  private long _endTimeMs;

  private Throwable _exception;

  public QueryStat(String query)
  {
    _query = query;
  }

  public String query()
  {
    return _query;
  }

  /**
   * Returns the time this query started waiting in line to be executed.
   *
   * @return
   */
  public long enqueueStartTimeMs()
  {
    return _enqueueStartTimeMs;
  }

  public void enqueueStartTimeMs(long time)
  {
    _enqueueStartTimeMs = time;
  }

  /**
   * Returns the time this query finished waiting in line to be executed and is
   * ready to be executed immediately.
   *
   * @return
   */
  public long enqueueEndTimeMs()
  {
    return _enqueueEndTimeMs;
  }

  public QueryStat enqueueEndTimeMs(long time)
  {
    _enqueueEndTimeMs = time;

    return this;
  }

  /**
   * Returns the time this query starting executing.
   *
   * @return
   */
  public long startTimeMs()
  {
    return _startTimeMs;
  }

  public QueryStat startTimeMs(long time)
  {
    _startTimeMs = time;

    return this;
  }

  /**
   * Returns the time this query finished or failed executing.
   *
   * @return
   */
  public long endTimeMs()
  {
    return _endTimeMs;
  }

  public QueryStat endTimeMs(long time)
  {
    _endTimeMs = time;

    return this;
  }

  /**
   * Returns the Exception throw by this query, if any.
   *
   * @return
   */
  public Throwable exception()
  {
    return _exception;
  }

  public QueryStat exception(Throwable e)
  {
    _exception = e;

    return this;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _query
                                      + ", queue=" + (_enqueueEndTimeMs - _enqueueStartTimeMs) + "ms"
                                      + ", execute=" + (_endTimeMs - _startTimeMs) + "ms"
                                      + "]";
  }
}
