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
import java.util.concurrent.atomic.AtomicInteger;

import com.caucho.v5.amp.queue.QueueRingResizing;

/**
 * A generic pool of threads available for Alarms and Work tasks.
 */
class ExecutorSpin implements Executor
{
  private final ThreadPool _threadPool;
  private final int _threadMax;
  
  private final AtomicInteger _taskCount = new AtomicInteger();
  private final AtomicInteger _threadCount = new AtomicInteger();

  private final BlockingQueue<Runnable> _ringQueue;
  
  private final SpinTask _spinTask = new SpinTask();
  private final ClassLoader _classLoader;
  
  ExecutorSpin(ThreadPool threadPool, int threadMax)
  {
    if (threadPool == null) {
      throw new NullPointerException();
    }
    
    if (threadMax <= 0) {
      throw new IllegalArgumentException();
    }
    
    _threadPool = threadPool;
    _threadMax = threadMax;
    
    _ringQueue = new QueueRingResizing<>(256, 128 * 1024);
    
    _classLoader = Thread.currentThread().getContextClassLoader();
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
    
    _ringQueue.offer(task);
    _taskCount.incrementAndGet();
    
    launchThread();
  }
  

  private void processTask()
  {
    Runnable task;
    
    long count = 1000;
    
    for (long i = count; i >= 0; i--) {
      while ((task = _ringQueue.poll()) != null) {
        i = count + 1;

        try {
          task.run();
        } finally {
          _taskCount.decrementAndGet();
        }
      }
    }
  }
  
  private void launchThread()
  {
    int threadCount;

    do {
      threadCount = _threadCount.get();
      int taskCount = _taskCount.get();
      
      if (taskCount <= threadCount) {
        return;
      }
      
      if (_threadMax <= threadCount) {
        return;
      }
    } while (! _threadCount.compareAndSet(threadCount, threadCount + 1));
    
    getThreadPool().schedule(_spinTask, _classLoader);
  }
  
  private class SpinTask implements Runnable {
    @Override
    public void run()
    {
      try {
        processTask();
      } finally {
        _threadCount.decrementAndGet();
      }
      
      launchThread();
    }
  }
}
