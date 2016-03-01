/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
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

package io.baratine.timer;

/**
 * Time information about a scheduled task.
 */
public class TaskInfo
{
  private final Runnable _task;

  private final long _addTime;

  private long _nextRunTime;
  private long _lastRunTime;
  private long _lastCompletedTime;

  private Throwable _lastException;

  public TaskInfo(Runnable task, long addTime)
  {
    _task = task;
    _addTime = addTime;
  }

  public TaskInfo lastRunTime(long time)
  {
    _lastRunTime = time;

    return this;
  }

  public TaskInfo nextRunTime(long time)
  {
    _nextRunTime = time;

    return this;
  }

  public TaskInfo lastCompletedTime(long time)
  {
    _lastCompletedTime = time;

    return this;
  }

  public TaskInfo lastException(Throwable t)
  {
    _lastException = t;

    return this;
  }

  /**
   * Returns the <code>Runnable</code> for this task.
   *
   * @return task
   */
  public Runnable getTask()
  {
    return _task;
  }

  /**
   * Returns the time that this task was added.
   *
   * @return time
   */
  public long getAddTime()
  {
    return _addTime;
  }

  /**
   * Returns the next time that the task should run.
   *
   * @return time next time to run, or -1 if unscheduled or unknown
   */
  public long getNextRunTime()
  {
    return _nextRunTime;
  }

  /**
   * Returns the last time that this task was run.
   *
   * @return last run time, or -1 if never ran or unknown
   */
  public long getLastRunTime()
  {
    return _lastRunTime;
  }

  /**
   * Returns the last time that this task completed.
   *
   * @return last completed time, -1 if never completed
   */
  public long getLastCompletedTime()
  {
    return _lastCompletedTime;
  }

  /**
   * Returns the last exception that this task had thrown.
   *
   * @return throwable
   */
  public Throwable getLastException()
  {
    return _lastException;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _task
                                            + ",added=" + _addTime
                                            + ",next=" + _nextRunTime
                                            + ",last=" + _lastRunTime + "]";
  }
}
