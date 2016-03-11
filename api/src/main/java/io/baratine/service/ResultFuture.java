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

package io.baratine.service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Future for a blocking client call to a Result.
 * 
 * In general, futures should be avoided in Baratine except for QA or specific
 * client needs.
 */
public final class ResultFuture<T> implements Result<T>
{
  private volatile T _value;
  
  private volatile Throwable _exn;
  private volatile FutureState _state = FutureState.INIT;
  private volatile Thread _thread;
  
  private volatile Result<Object> _chain;
  private volatile Object _chainValue;
  
  public final boolean isDone()
  {
    return _state.isDone();
  }
  
  public T get()
  {
    return get(30, TimeUnit.DAYS);
  }
  
  public T get(long timeout, TimeUnit unit)
  {
    if (isDone()) {
      return getResultValue();
    }
    
    Thread thread = Thread.currentThread();
    
    //_thread = thread;
      
    long expires = unit.toMillis(timeout) + System.currentTimeMillis();
    
    while (true) {
      if (isDone()) {
        return getResultValue();
      }
      else if (_state == FutureState.ASYNC) {
        Result<Object> chain = _chain;
        Object chainValue = _chainValue;
        _chain = null;
        _chainValue = null;
        
        _state = FutureState.INIT;
          
        //_thread = null;
        
        chain.completeFuture(chainValue);
        
        /*
        if (isDone()) {
          return getResultValue();
        }
        */
        
        // _thread = thread;
      }
      else {
        if (ServiceRef.flushOutboxAndExecuteLast()) {
          // if pending messages, continue to process them
          continue;
        }
        
        // ServiceRef.flushOutbox();
        
        _thread = thread;
      
        if (_state.isParkRequired()) {
          if (expires < System.currentTimeMillis()) {
            _thread = null;
            
            throw new ServiceExceptionFutureTimeout("future timeout " + timeout + " " + unit);
          }
          
          LockSupport.parkUntil(expires);
        }
        
        _thread = null;
      }
    }
  }
  
  private T getResultValue()
  {
    if (_exn != null) {
      throw ServiceException.createAndRethrow(_exn);
    }
    else {
      return _value;
    }
  }
  
  public T peek(T defaultValue)
  {
    // flush any pending messages
    // ServiceRef.flushOutbox();
    
    if (! isDone()) {
      return defaultValue;
    }
    else if (_exn != null) {
      throw ServiceException.createAndRethrow(_exn);
    }
    else {
      return _value;
    }
  }

  @Override
  public void ok(T result)
  {
    _value = result;
    
    _state = FutureState.COMPLETE;
    Thread thread = _thread;
    
    if (thread != null) {
      LockSupport.unpark(thread);
    }
  }

  @Override
  public void fail(Throwable exn)
  {
    Objects.requireNonNull(exn);
    
    try {
      //_exn = ServiceException.createAndRethrow(exn);
      _exn = exn;
    } finally {
      _state = FutureState.FAIL;
    
      Thread thread = _thread;
    
      if (thread != null) {
        LockSupport.unpark(thread);
      }
    }
  }

  @Override
  public final void handle(T result, Throwable exn)
  {
    throw new IllegalStateException(getClass().getName());
    /*
    if (exn != null) {
      fail(exn);
    }
    else {
      ok(result);
    }
    */
  }
  
  @Override
  public boolean isFuture()
  {
    return true;
  }

  @Override
  public <U> void completeFuture(Result<U> chain, U chainValue)
  {
    _chain = (Result) chain;
    _chainValue = chainValue;
    
    _state = FutureState.ASYNC;
    Thread thread = _thread;
    
    if (thread != null) {
      LockSupport.unpark(thread);
    }
  }

  @Override
  public void completeFuture(T value)
  {
    _value = value;
    
    _state = FutureState.COMPLETE;
    Thread thread = _thread;
    
    if (thread != null) {
      LockSupport.unpark(thread);
    }
  }
  
  private enum FutureState {
    INIT {
      @Override
      public boolean isParkRequired()
      {
        return true;
      }
    },
    ASYNC,
    COMPLETE {
      public boolean isDone() { return true; }
    },
    FAIL {
      public boolean isDone() { return true; }
    };
    
    public boolean isParkRequired()
    {
      return false;
    }
    
    public boolean isDone()
    {
      return false;
    }
  }
}
