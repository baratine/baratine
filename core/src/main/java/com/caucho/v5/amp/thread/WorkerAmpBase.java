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

import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A generic pool of threads available for Alarms and Work tasks.
 */
abstract public class WorkerAmpBase
  implements Runnable, WorkerAmp, Closeable
{
  private static Logger _log;
  
  private static final long PERMANENT_TIMEOUT = 30000L;
  
  private static final AtomicLong _idGen = new AtomicLong();
  
  private final AtomicReference<State> _state
    = new AtomicReference<State>(State.IDLE);

  private final WeakReference<ClassLoader> _classLoaderRef;
  
  private String _threadName;
  
  // private long _workerIdleTimeout = 30000L;

  private long _workerIdleTimeout = 0; // 500;
  
  private volatile Thread _thread;

  protected WorkerAmpBase()
  {
    this(Thread.currentThread().getContextClassLoader());
  }
  
  protected WorkerAmpBase(ClassLoader classLoader)
  {
    _classLoaderRef = new WeakReference<ClassLoader>(classLoader);
    
    // Environment.addWeakCloseListener(this, classLoader);
  }

  protected boolean isPermanent()
  {
    return false;
  }
  
  protected void setWorkerIdleTimeout(long timeout)
  {
    if (timeout < 0)
      throw new IllegalArgumentException();
    
    _workerIdleTimeout = timeout;
  }
  
  public final boolean isTaskActive()
  {
    return _state.get().isActive() || _state.get().isPark();
  }
  
  public boolean isClosed()
  {
    return _state.get().isClosed();
  }
  
  public String getState()
  {
    return String.valueOf(_state.get());
  }
  
  protected ClassLoader getClassLoader()
  {
    return _classLoaderRef.get();
  }
  
  abstract public long runTask();

  @Override
  public void close()
  {
    _state.getAndSet(State.CLOSED);
    
    Thread thread = _thread;

    if (thread != null) {
      LockSupport.unpark(thread);
    }
  }

  @Override
  public final void wake()
  {
    if (isClosed()) {
      return;
    }
    
    State oldState;
    State newState;
    
    do {
      oldState = _state.get();
      newState = oldState.toWake();
    } while (! _state.compareAndSet(oldState, newState));

    if (oldState.isIdle()) {
      startWorkerThread();
    }
    else if (oldState.isPark()) {
      Thread thread = _thread;

      if (thread != null) {
        unpark(thread);
      }
    }
  }
  
  abstract protected void startWorkerThread();
  
  protected void unpark(Thread thread)
  {
    LockSupport.unpark(thread);
  }

  protected String getThreadName()
  {
    if (_threadName == null)
      _threadName = toString() + "-" + _idGen.incrementAndGet();

    return _threadName;
  }
  
  protected boolean isRetry()
  {
    return false;
  }
  
  protected void onThreadStart()
  {
  }
  
  protected void onThreadComplete()
  {
  }
  
  private long getIdleTimeout()
  {
    if (isPermanent())
      return PERMANENT_TIMEOUT;
    else
      return _workerIdleTimeout;
  }

  @Override
  public final void run()
  {
    Thread thread = Thread.currentThread(); 
    // String oldName = thread.getName();
    
    try {
      Thread oldThread = _thread;
      _thread = thread;
      
      if (oldThread != null) {
        System.out.println("DOUBLE_THREAD: " + oldThread);
      }
      
      thread.setContextClassLoader(getClassLoader());
      // thread.setName(getThreadName());
      
      onThreadStart();

      long now = getCurrentTimeActual();
      
      long idleTimeout = getIdleTimeout();
      
      long expires;
      
      if (idleTimeout > 0)
        expires = now + idleTimeout;
      else
        expires = 0;
      
      boolean isExpireRetry = false;
      
      do {
        isExpireRetry = false;
        
        State oldState;

        do {
          oldState = _state.get();
          
          if (oldState.isClosed()) {
            return;
          }
        } while (! _state.compareAndSet(oldState, State.ACTIVE));
        
        do {
          if (_state.get().isClosed()) {
            return;
          }
          
          thread.setContextClassLoader(getClassLoader());
          
          isExpireRetry = false;
          
          long delta = runTask();
          
          now = getCurrentTimeActual();
          
          if (delta > 0) {
            expires = now + delta;

            isExpireRetry = true;
          }
          else if (idleTimeout > 0) {
            expires = now + idleTimeout;
          }
          else {
            expires = 0;
          }
        } while (_state.compareAndSet(State.ACTIVE_WAKE, State.ACTIVE));

        if (expires > 0
            && _state.compareAndSet(State.ACTIVE, State.PARK)) {
          thread.setName(getThreadName());

          Thread.interrupted();
          
          if (! isRetry() && _state.get() == State.PARK) {
            LockSupport.parkUntil(expires);
          }
          
          _state.compareAndSet(State.PARK, State.ACTIVE);
        }
        
        now = getCurrentTimeActual();
        
        if (isPermanent() || isExpireRetry) {
          expires = now + idleTimeout;
        }
      } while (isPermanent()
               || isExpireRetry
               || now < expires
               || _state.get() == State.ACTIVE_WAKE
               || isRetry());
    } catch (Throwable e) {
      System.out.println("EXN: " + e);
      //WarningSystem.sendCurrentWarning(this, e);
      log().log(Level.WARNING, e.toString(), e);
    } finally {
      _thread = null;
      
      /*
      if (! _isActive.get()) {
        System.out.println("DUALALLOC:" + isClosed());
      }
      */

      onThreadComplete();
      
      State oldState;
      State newState;
      
      do {
        oldState = _state.get();
        newState = oldState.toIdle();
      } while (! _state.compareAndSet(oldState, newState));

      // thread.setName(oldName);

      if (newState.isWake()) {
        startWorkerThread();
      }
    }
  }
  
  protected long getCurrentTimeActual()
  {
    // return CurrentTime.getCurrentTimeActual();
    
    return System.currentTimeMillis();
  }
  
  private Logger log()
  {
    if (_log == null) {
      _log = Logger.getLogger(WorkerAmpBase.class.getName()); 
    }
    
    return _log;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
  
  enum State {
    IDLE {
      @Override
      boolean isIdle() { return true; }
      
      @Override
      State toWake() { return ACTIVE_WAKE; }
    },
    
    ACTIVE {
      @Override
      State toWake() { return ACTIVE_WAKE; }
      
      @Override
      boolean isActive() { return true; }
      
      @Override
      State toIdle() { return IDLE; }
    },
    
    ACTIVE_WAKE {
      @Override
      State toWake() { return this; }
      
      @Override
      boolean isActive() { return true; }
      
      @Override
      boolean isWake() { return true; }
      
      @Override
      State toIdle() { return this; }
    },
    
    PARK {
      @Override
      State toWake() { return ACTIVE_WAKE; }
      
      @Override
      State toIdle() { return IDLE; }
      
      @Override
      boolean isPark () { return true; }
    },
    
    CLOSED {
      @Override
      boolean isClosed() { return true; }
      
      @Override
      State toWake() { return CLOSED; }
      
      @Override
      State toIdle() { return CLOSED; }
    };
    
    boolean isIdle() { return false; }
    boolean isActive() { return false; }
    boolean isWake() { return false; }
    boolean isPark() { return false; }
    boolean isClosed() { return false; }
    
    State toWake() { return ACTIVE_WAKE; }
    State toIdle() { throw new UnsupportedOperationException(toString()); }
  }
}
