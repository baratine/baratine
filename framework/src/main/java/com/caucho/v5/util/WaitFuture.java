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
<<<<<<< HEAD
 *
 *   Free Software Foundation, Inc.
=======
 *   Free Software Foundation, Inc.
>>>>>>> d4d8795c5fb4e568513d63a15ba74044e5790968
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.util;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;

import com.caucho.v5.amp.thread.ThreadPool;

/**
 * Simple future implementation
 */
public class WaitFuture<T> implements Future<T> {

  private T _value;
  private boolean _isDone;
  private ExecutionException _exn;
  
  @Override
  public boolean isDone()
  {
    return _isDone;
  }
  
  @Override
  public T get() throws InterruptedException, ExecutionException
  {
    try {
      return get(Integer.MAX_VALUE, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public T get(long timeout, TimeUnit unit)
      throws InterruptedException,
             ExecutionException, 
             TimeoutException
  {
    long expires = System.currentTimeMillis() + unit.toMillis(timeout);

    synchronized (this) {
      while (! _isDone && System.currentTimeMillis() < expires) {
        wait(expires - System.currentTimeMillis());
      }
    }
    
    if (! _isDone) {
      throw new TimeoutException("timeout: " + timeout + " " + unit);
    }
    
    if (_exn != null) {
      throw _exn;
    }
    else {
      return _value;
    }
  }
  
  public void completed(T value)
  {
    _value = value;
    _isDone = true;

    synchronized (this) {
      notifyAll();
    }
  }
  
  public void failed(Throwable exn)
  {
    _exn = new ExecutionException(exn);
    
    _isDone = true;
    
    synchronized (this) {
      notifyAll();
    }
  }
  
  @Override
  public boolean cancel(boolean value)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public boolean isCancelled()
  {
    return false;
  }
}
