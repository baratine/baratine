/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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
 * @author Scott Ferguson
 */

package com.caucho.v5.amp.thread;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.util.L10N;

/**
 * A generic pool of threads available for Alarms and Work tasks.
 */
public final class ThreadPool extends ThreadPoolBase
{
  private static final L10N L = new L10N(ThreadPool.class);
  
  private static final AtomicReference<ThreadPool> _globalThreadPool
    = new AtomicReference<>();
  
  private static final int DEFAULT_EXECUTOR_TASK_MAX = 16;
  private static final long MAX_EXPIRE = Long.MAX_VALUE / 2;
  
  private final ExecutorSpin _spinExecutor;
  private final ExecutorThrottle _throttleExecutor;
  
  //
  // executor
  //
  private int _executorTaskMax = DEFAULT_EXECUTOR_TASK_MAX;

  private final Object _executorLock = new Object();
  // number of executor tasks running
  private int _executorTaskCount;
  // queue for waiting executor tasks
  private ExecutorQueueItem _executorQueueHead;
  private ExecutorQueueItem _executorQueueTail;
  
  public ThreadPool()
  {
    this("default");
  }

  public ThreadPool(String name)
  {
    super(name);
    
    int processors = Runtime.getRuntime().availableProcessors();
    
    _spinExecutor = new ExecutorSpin(this, processors);
    
    int threadMax = 2 * processors;
    long timeout = 1;
    
    _throttleExecutor = new ExecutorThrottle(this, threadMax, timeout);
  }

  public static ThreadPool current()
  {
    ThreadPool pool = _globalThreadPool.get();
    
    if (pool == null) {
      pool = new ThreadPool();
      
      pool = pool.asGlobal();
      
      if (_globalThreadPool.compareAndSet(null, pool)) {
        pool.start();
      }
      else {
        pool = _globalThreadPool.get();
      }
    }
    
    return pool;
  }
  
  protected ThreadPool asGlobal()
  {
    if (_globalThreadPool.compareAndSet(null, this)) {
      start();
      
      setAsGlobal(this);
      
      return this;
    }
    else {
      return _globalThreadPool.get();
    }
  }
  
  public ExecutorSpin spinExecutor()
  {
    return _spinExecutor;
  }
  
  public Executor throttleExecutor()
  {
    return _throttleExecutor;
  }

  /**
   * Sets the maximum number of executor threads.
   */
  public void setExecutorTaskMax(int max)
  {
    if (getThreadMax() < max)
      throw new ConfigException(L.l("<thread-executor-max> ({0}) must be less than <thread-max> ({1})",
                                    max, getThreadMax()));

    if (max == 0)
      throw new ConfigException(L.l("<thread-executor-max> must not be zero."));

    _executorTaskMax = max;
  }

  /**
   * Gets the maximum number of executor threads.
   */
  public int getExecutorTaskMax()
  {
    return _executorTaskMax;
  }

  /**
   * Schedules an executor task.
   */
  public boolean scheduleExecutorTask(Runnable task)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    synchronized (_executorLock) {
      _executorTaskCount++;

      if (_executorTaskCount <= _executorTaskMax || _executorTaskMax < 0) {
        boolean isPriority = false;
        boolean isQueue = true;
        boolean isWake = true;

        return scheduleImpl(task, loader, MAX_EXPIRE, isPriority, isQueue, isWake);
      }
      else {
        ExecutorQueueItem item = new ExecutorQueueItem(task, loader);

        if (_executorQueueTail != null)
          _executorQueueTail._next = item;
        else
          _executorQueueHead = item;

        _executorQueueTail = item;

        return false;
      }
    }
  }

  /**
   * Called when an executor task completes
   */
  public void completeExecutorTask()
  {
    ExecutorQueueItem item = null;

    synchronized (_executorLock) {
      _executorTaskCount--;

      assert(_executorTaskCount >= 0);

      if (_executorQueueHead != null) {
        item = _executorQueueHead;

        _executorQueueHead = item._next;

        if (_executorQueueHead == null)
          _executorQueueTail = null;
      }
    }

    if (item != null) {
      Runnable task = item.getRunnable();
      ClassLoader loader = item.getLoader();

      boolean isPriority = false;
      boolean isQueue = true;
      boolean isWake = true;

      scheduleImpl(task, loader, MAX_EXPIRE, isPriority, isQueue, isWake);
    }
  }
 
  static class ExecutorQueueItem
  {
    Runnable _runnable;
    ClassLoader _loader;

    ExecutorQueueItem _next;

    ExecutorQueueItem(Runnable runnable, ClassLoader loader)
    {
      _runnable = runnable;
      _loader = loader;
    }

    Runnable getRunnable()
    {
      return _runnable;
    }

    ClassLoader getLoader()
    {
      return _loader;
    }
  }
}
