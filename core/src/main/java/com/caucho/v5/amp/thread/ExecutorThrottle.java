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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.caucho.v5.amp.queue.QueueRing;

/**
 * Executor throttling threads to the cpu max, allowing extras to be
 * created slowly as timeouts.
 */
public class ExecutorThrottle implements Executor, RunnableItemScheduler {
  private final ThreadPool _threadPool;
  private final int _threadMax;
  
  private volatile int _slowThreadCount;
  
  private final AtomicInteger _taskCount = new AtomicInteger();
  private final AtomicInteger _threadCount = new AtomicInteger();

  private final BlockingQueue<RunnableItem> _ringQueue;
  
  private final SpinTask _spinTask = new SpinTask();
  private final RunnableItem _spinTaskItem;
  private final ThrottleTimeoutWorker _timeoutWorker;
  
  private final long _timeout;
  
  public ExecutorThrottle(ThreadPool threadPool, 
                          int threadMax,
                          long timeout)
  {
    if (threadPool == null) {
      throw new NullPointerException();
    }
    
    if (threadMax <= 0) {
      throw new IllegalArgumentException();
    }
    
    _threadPool = threadPool;
    _threadMax = threadMax;
    _timeout = timeout;

    // _ringQueue = new RingResizingBlockingQueue<>(64, 256 * 1024);
    _ringQueue = new QueueRing<>(16 * 1024);
    
    _spinTaskItem = new RunnableItem(_spinTask, getClass().getClassLoader());
    
    _timeoutWorker = new ThrottleTimeoutWorker(threadPool);
  }

  private ThreadPool getThreadPool()
  {
    return _threadPool;
  }

  @Override
  public void execute(Runnable task)
  {
    if (task == null) {
      throw new NullPointerException();
    }

    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    long timeout = _timeout;
    
    RunnableItem item = new RunnableItem(task, loader, timeout);
    
    schedule(item);
  }
    
  @Override
  public boolean schedule(RunnableItem item)
  {
    try {
      if (_ringQueue.offer(item, 10, TimeUnit.MILLISECONDS)) {
        _taskCount.incrementAndGet();
        launchThread();
        return true;
      }
      
      System.out.println("Executor Schedule Throttle: " + item.getTask());
      return false;
    } catch (InterruptedException e) {
      e.printStackTrace();
      throw new IllegalStateException(e);
    } catch (Throwable e) {
      e.printStackTrace();
      throw e;
    }
  }
  
  BlockingQueue<RunnableItem> getTaskQueue()
  {
    return _ringQueue;
  }
  
  void launchThread()
  {
    int threadCount;

    while (true) {
      do {
        threadCount = _threadCount.get();
        int taskCount = _taskCount.get();
      
        if (taskCount <= threadCount) {
          return;
        }
      
        if (_threadMax + _slowThreadCount <= threadCount) {
          _timeoutWorker.wake();
          return;
        }
      } while (! _threadCount.compareAndSet(threadCount, threadCount + 1));

      getThreadPool().schedule(_spinTaskItem);
    }
  }

  //
  // initialization
  //

  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[max=" + _threadMax
            + ",task=" + _taskCount.get()
            + ",thread=" + _threadCount.get()
            + ",queue=" + _ringQueue.size()
            + "]");
  }
  
  private class SpinTask implements Runnable {
    @Override
    public void run()
    {
      ThreadAmp thread = (ThreadAmp) Thread.currentThread();

      try {
        BlockingQueue<RunnableItem> taskQueue = getTaskQueue();
        ExecutorThrottle executor = ExecutorThrottle.this;
        
        RunnableItem taskItem;
        
        while ((taskItem = taskQueue.poll()) != null) {
          try {
            Runnable task = taskItem.getTask();
            ClassLoader loader = taskItem.getClassLoader();
            long timeout = taskItem.getTimeout();

            thread.setExecutorTimeout(executor, timeout);
            thread.setContextClassLoader(loader);
          
            task.run();
          } finally {
            _taskCount.decrementAndGet();
          }
        }
      } catch (Throwable e) {
        e.printStackTrace();
        throw e;
      } finally {
        _threadCount.decrementAndGet();
        
        thread.setContextClassLoader(null);
        thread.clearExecutorTimeout();
        
        if (_slowThreadCount > 0) {
          _timeoutWorker.wake();
        }
        
        launchThread();
      }
    }
  }
  
  private class ThrottleTimeoutWorker extends WorkerThreadPoolBase {
    ThrottleTimeoutWorker(ThreadPool threadPool)
    {
      super(threadPool, threadPool);
    }
    
    @Override
    public long runTask()
    {
      while (true) {
        try {
          ExecutorThrottle executor = ExecutorThrottle.this;

          // long oldSlowThreadCount = _slowThreadCount;

          _slowThreadCount = _threadPool.countSlowThreads(executor);

          launchThread();

          if (_taskCount.get() <= _slowThreadCount) {
            return 0;
          }

          Thread.interrupted();
          Thread.sleep(Math.max(_timeout >> 3, 1));
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }
}
