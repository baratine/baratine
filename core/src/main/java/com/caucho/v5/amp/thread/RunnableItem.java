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

import java.util.concurrent.TimeUnit;


/**
 * Executor throttling threads to the cpu max, allowing extras to be
 * created slowly as timeouts.
 */
public final class RunnableItem
{
  private final Runnable _task;
  private final ClassLoader _classLoader;
  private final long _timeout;
    
  public RunnableItem(Runnable task,
                  ClassLoader classLoader,
                  long timeout)
  {
    _task = task;
    _classLoader = classLoader;
    _timeout = timeout;
  }
  
  public RunnableItem(Runnable task,
                  ClassLoader classLoader)
  {
    this(task, classLoader, TimeUnit.MILLISECONDS.toMillis(100));
  }
    
  final Runnable getTask()
  {
    return _task;
  }
   
  final ClassLoader getClassLoader()
  {
    return _classLoader;
  }
    
  final long getTimeout()
  {
    return _timeout;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _task + "]";
  }
}
