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

import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.deliver.Outbox;
import com.caucho.v5.amp.deliver.OutboxProvider;
import com.caucho.v5.util.CurrentTime;


/**
 * A generic pool of threads available for Alarms and Work tasks.
 */
public final class ThreadAmp extends Thread
{
  private static final Logger log 
    = Logger.getLogger(ThreadAmp.class.getName());
  
  private final String _name;
  
  private final ThreadPoolBase _pool;
  private final ThreadLauncher _launcher;
  
  private final Outbox _outbox;

  private long _activeSlowExpireTime;
  private ExecutorThrottle _executor;
  
  private boolean _isClose;

  private volatile WakeState _wakeState;

  ThreadAmp(int id, ThreadPoolBase pool, ThreadLauncher launcher)
  {
    _name = "baratine-" + getId();

    _pool = pool;
    _launcher = launcher;

    setDaemon(true);
    
    _outbox = OutboxProvider.getProvider().get();
  }

  /**
   * Returns the name.
   */
  public String debugName()
  {
    return _name;
  }

  /**
   * Returns the thread id.
   */
  public long threadId()
  {
    return getId();
  }

  @Override
  public final void interrupt()
  {
  }

  @Override
  public boolean isInterrupted()
  {
    return false;
  }

  public final Outbox outbox()
  {
    return _outbox;
  }
  
  /**
   * Sets timeouts.
   */
  final void executorTimeout(ExecutorThrottle executor, long timeout)
  {
    _executor = executor;
    _activeSlowExpireTime = CurrentTime.getCurrentTimeActual() + timeout;
  }
  
  final void clearExecutorTimeout()
  {
    _executor = null;
    _activeSlowExpireTime = 0;
  }

  public boolean isSlow(ExecutorThrottle executor, long now)
  {
    long expireTime = _activeSlowExpireTime;
    
    return _executor == executor && expireTime < now && expireTime > 0;
  }

  /**
   * Wake the thread.  Called outside of _idleLock
   */
  final void close()
  {
    _isClose = true;
    LockSupport.unpark(this);
  }
  
  final boolean isClosed()
  {
    return _isClose;
  }

  /**
   * The main thread execution method.
   */
  @Override
  public void run()
  {
    try {
      _launcher.onChildIdleBegin();
      _launcher.onChildThreadLaunchBegin();
      _pool.addThread(this);
      
      runTasks();
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
    } finally {
      _pool.removeThread(this);
      _launcher.onChildIdleEnd();
      _launcher.onChildThreadLaunchEnd();
    }
  }

  /**
   * Main thread loop.
   */
  private void runTasks()
  {
    ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
    
    ThreadPoolBase pool = _pool;
    Thread thread = this;
    Outbox outbox = outbox();
    
    setName(_name);
    
    while (! _isClose) {
      RunnableItem taskItem = pool.poll();
      
      if (taskItem != null) {
        try {
          _launcher.onChildIdleEnd();
          
          outbox.open();

          do {
            // if the task is available, run it in the proper context
            thread.setContextClassLoader(taskItem.getClassLoader());

            taskItem.getTask().run();
            
            outbox.flushAndExecuteAll();
          } while ((taskItem = pool.poll()) != null);
        } catch (Throwable e) {
          log.log(Level.WARNING, e.toString(), e);
        } finally {
          try {
            outbox.close();
          } catch (Throwable e) {
            e.printStackTrace();
          }
          
          _launcher.onChildIdleBegin();
          thread.setContextClassLoader(systemClassLoader);
          if (thread.getName() != _name) {
            setName(_name);
          }
        }
      }
      else if (_launcher.isIdleExpire()) {
        return;
      }
      else {
        park();
      }
    }
  }
  
  void setWakeThread()
  {
    _wakeState = WakeState.THREAD;
  }
  
  private void park()
  {
    if (_isClose) {
      return;
    }
    
    _wakeState = WakeState.IDLE;
    // setName(_name);
    
    _pool.offerIdle(this);

    while (_wakeState == WakeState.IDLE && ! _isClose) {
      Thread.interrupted();
      LockSupport.park();
    }
    
    if (_isClose) {
    }
    else if (_wakeState == WakeState.THREAD) {
      _pool.onWakeThread();
    }
    else {
      log.warning("Illegal State: " + _wakeState + " " + this);
    }
  }
  
  static enum WakeState {
    IDLE,
    THREAD;
  }
}
