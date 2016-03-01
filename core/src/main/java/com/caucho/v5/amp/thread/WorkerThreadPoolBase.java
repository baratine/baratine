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

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * A generic pool of threads available for Alarms and Work tasks.
 */
abstract public class WorkerThreadPoolBase 
  extends WorkerAmpBase
  implements WorkerAmp
{
  private final ThreadPool _threadPool;
  private final Executor _executor;
  
  protected WorkerThreadPoolBase()
  {
    this(ThreadPool.current());
  }

  protected WorkerThreadPoolBase(ThreadPool threadPool)
  {
    this(Thread.currentThread().getContextClassLoader(), 
         threadPool,
         threadPool);
  }

  protected WorkerThreadPoolBase(ClassLoader loader, ThreadPool threadPool)
  {
    this(loader,
         threadPool,
         threadPool);
  }

  protected WorkerThreadPoolBase(ThreadPool threadPool, Executor executor)
  {
    this(Thread.currentThread().getContextClassLoader(), 
         threadPool,
         executor);
  }

  protected WorkerThreadPoolBase(ClassLoader classLoader,
                               ThreadPool threadPool,
                               Executor executor)
  {
    super(classLoader);
    
    _threadPool = threadPool;
    _executor = executor;
    
    Objects.requireNonNull(executor);
  }

  @Override
  protected void startWorkerThread()
  {
    // _threadPool.schedulePriority(this);
    _executor.execute(this);
  }

  @Override
  protected void unpark(Thread thread)
  {
    _threadPool.scheduleUnpark(thread);
  }
}
