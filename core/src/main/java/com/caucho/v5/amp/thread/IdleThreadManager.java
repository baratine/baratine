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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.ModulePrivate;

@ModulePrivate
public class IdleThreadManager implements SpawnThreadManager
{
  private static final L10N L = new L10N(IdleThreadManager.class);
  private static final Logger log
    = Logger.getLogger(IdleThreadManager.class.getName());
  
  private static final int DEFAULT_THREAD_MAX = 8192;
  private static final int DEFAULT_IDLE_MIN = 2;
  private static final int DEFAULT_IDLE_MAX = Integer.MAX_VALUE / 2;

  private static final long DEFAULT_IDLE_TIMEOUT = 60000L;
  
  private final IdleThreadLauncher _launcher;
  
  // configuration items

  private int _threadMax = DEFAULT_THREAD_MAX;
  private int _idleMin = DEFAULT_IDLE_MIN;
  private int _idleMax = DEFAULT_IDLE_MAX;
  private long _idleTimeout = DEFAULT_IDLE_TIMEOUT;
  
  //
  // thread max and thread lifetime counts
  //

  private final AtomicInteger _threadCount = new AtomicInteger();
  private final AtomicInteger _idleCount = new AtomicInteger();
  
  // number of threads which are in the process of starting
  private final AtomicInteger _startingCount = new AtomicInteger();
  
  private final AtomicLong _createCountTotal = new AtomicLong();
  // private final AtomicLong _overflowCount = new AtomicLong();

  // next time when an idle thread can expire
  private final AtomicLong _threadIdleExpireTime = new AtomicLong();
  
  private final AtomicInteger _gId = new AtomicInteger();
  
  private final Lifecycle _lifecycle;

  public IdleThreadManager(IdleThreadLauncher launcher)
  {
    _launcher = launcher;
    _lifecycle = new Lifecycle();
  }
  

  //
  // Configuration properties
  //

  /**
   * Sets the maximum number of threads.
   */
  public void setThreadMax(int max)
  {
    if (max <= 0) {
      max = DEFAULT_THREAD_MAX;
    }
    
    if (max < _idleMin)
      throw new ConfigException(L.l("IdleMin ({0}) must be less than ThreadMax ({1})", _idleMin, max));
    if (max < 1)
      throw new ConfigException(L.l("ThreadMax ({0}) must be greater than zero", 
                                    max));

    _threadMax = max;

    update();
  }

  /**
   * Gets the maximum number of threads.
   */
  public int getThreadMax()
  {
    return _threadMax;
  }

  /**
   * Sets the minimum number of idle threads.
   */
  public void setIdleMin(int min)
  {
    if (min <= 0) {
      min = DEFAULT_IDLE_MIN;
    }
    
    if (_threadMax < min)
      throw new ConfigException(L.l("IdleMin ({0}) must be less than ThreadMax ({1})", min, _threadMax));
    if (min <= 0)
      throw new ConfigException(L.l("IdleMin ({0}) must be greater than 0.", min));

    _idleMin = min;
    
    update();
  }

  /**
   * Gets the minimum number of idle threads.
   */
  public int getIdleMin()
  {
    return _idleMin;
  }

  /**
   * Sets the maximum number of idle threads.
   */
  public void setIdleMax(int max)
  {
    if (max <= 0) {
      max = DEFAULT_IDLE_MAX;
    }
        if (_threadMax < max)
      throw new ConfigException(L.l("IdleMax ({0}) must be less than ThreadMax ({1})", max, _threadMax));
    if (max <= 0)
      throw new ConfigException(L.l("IdleMax ({0}) must be greater than 0.", max));

    _idleMax = max;

    update();
  }

  /**
   * Gets the maximum number of idle threads.
   */
  public int getIdleMax()
  {
    return _idleMax;
  }
  
  /**
   * Sets the idle timeout
   */
  public void setIdleTimeout(long timeout)
  {
    _idleTimeout = timeout;
  }
  
  /**
   * Returns the idle timeout.
   */
  public long getIdleTimeout()
  {
    return _idleTimeout;
  }
  
  protected boolean isEnable()
  {
    return _lifecycle.isActive();
  }
  
  //
  // lifecycle method
  //
  
  public void start()
  {
    _lifecycle.toActive();
    
    update();
  }
  
  public void close()
  {
    _lifecycle.toDestroy();
  }
  
  //
  // child thread callbacks
  //

  public boolean isThreadMax()
  {
    return _threadMax <= (_threadCount.get() + _startingCount.get());
  }
  
  public boolean isThreadHigh()
  {
    int threadCount = _threadCount.get();
    int startCount = _startingCount.get();
    
    return _threadMax < 2 * (threadCount + startCount);
  }
  
  /**
   * Callback from the launched thread's run().
   * 
   * Must _not_ be called by any other method, including other spawning.
   */
  @Override
  public void onThreadBegin()
  {
    _threadCount.incrementAndGet();
    
    int startCount = _startingCount.decrementAndGet();

    if (startCount < 0) {
      _startingCount.set(0);
      
      new IllegalStateException().printStackTrace();
    }

    _createCountTotal.incrementAndGet();
    
    update();
  }
  
  /**
   * Callback from the launched thread's run().
   * 
   * Must _not_ be called by any other method, including other spawning.
   */
  @Override
  public void onThreadEnd()
  {
    _threadCount.getAndDecrement();
      
    wake();
  }
  
  /**
   * Start housekeeping for a child thread managed by the launcher's
   * housekeeping, but not spawned by the launcher itself, e.g. comet,
   * websocket, keepalive.
   */
  public void onChildThreadResumeBegin()
  {
    _threadCount.incrementAndGet();
  }
  
  /**
   * End housekeeping for a child thread managed by the launcher's
   * housekeeping, but not spawned by the launcher itself, e.g. comet,
   * websocket, keepalive.
   */
  public void onChildThreadResumeEnd()
  {
    _threadCount.getAndDecrement();
    
    wake();
  }
  
  //
  // idle management
  //
  
  /**
   * Returns true if the thread should expire instead of going to the idle state.
   */
  public boolean isIdleExpire()
  {
    if (! _lifecycle.isActive()) {
      return true;
    }
    
    long now = getCurrentTimeActual();
    
    long idleExpire = _threadIdleExpireTime.get();
    
    int idleCount = _idleCount.get();

    // if idle queue is full and the expire is set, return and exit
    if (_idleMin < idleCount) {
      long nextIdleExpire = now + _idleTimeout;

      if (_idleMax < idleCount 
          && _idleMin < _idleMax) {
        /*
          && _threadIdleExpireTime.compareAndSet(idleExpire, nextIdleExpire)) {
          */
        _threadIdleExpireTime.compareAndSet(idleExpire, nextIdleExpire);
        
        return true;
      }
      else if (idleExpire < now
               && _threadIdleExpireTime.compareAndSet(idleExpire,
                                                      nextIdleExpire)) {
        return true;
      }
    }
    
    return false;
  }
  
  protected long getCurrentTimeActual()
  {
    return CurrentTime.getCurrentTimeActual();
  }
  
  public final boolean isIdleLow()
  {
    return _idleCount.get() < _idleMin;
  }
  
  public boolean isIdleOverflow()
  {
    return _idleMax < _idleCount.get();
  }
  
  /**
   * Called by the thread before going into the idle state.
   */
  
  public void onIdleBegin()
  {
    _idleCount.incrementAndGet();
  }
  
  public boolean isIdleAllowed()
  {
    return _idleCount.get() < _idleMax;
  }
  
  /**
   * Called by the thread after exiting the idle state.
   */
  public void onIdleEnd()
  {
    _idleCount.decrementAndGet();

    wake();
  }
  
  /*
  private void wakeIfLowIdle()
  {
    int idleCount = _idleCount.get();
    int startingCount = _startingCount.get();
    
    if (idleCount + startingCount < _idleMin) {
      updateIdleExpireTime(getCurrentTimeActual());
      
      wake();
    }
  }
  */
   
  //
  // implementation methods
  //

  private void update()
  {
    long now = getCurrentTimeActual();
    
    updateIdleExpireTime(now);
    
    wake();
  }

  /**
   * updates the thread idle expire time.
   */
  private void updateIdleExpireTime(long now)
  {
    _threadIdleExpireTime.set(now + _idleTimeout);
  }
  
  public void wake()
  {
    while (doStart()) {
    }
  }

  @Override
  public boolean allocateThread()
  {
    int threadCount = _threadCount.get();
    int startingCount = _startingCount.get();
    
    if (threadCount + startingCount < _threadMax) {
      _startingCount.incrementAndGet();
      
      return true;
    }
    else {
      return false;
    }
  }

  @Override
  public int getSpawnCount()
  {
    return _startingCount.get();
  }

  /**
   * Checks if the launcher should start another thread.
   */
  private boolean doStart()
  {
    if (! _lifecycle.isActive()) {
      return false;
    }
    
    if (! isEnable()) {
      return false;
    }
    
    int startingCount = _startingCount.get();

    int threadCount = _threadCount.get() + startingCount;

    if (_threadMax < threadCount) {
      onThreadMax();
      
      return false;
    }
    else if (! isIdleTooLow(startingCount)) {
      return false;
    }
    
    _idleCount.incrementAndGet();

    startConnection();
      
    return true;
  }
  
  protected boolean isIdleTooLow(int startingCount)
  {
    // return (_idleCount.get() + startingCount < _idleMin);
    return (_idleCount.get() < _idleMin);
  }

  /**
   * Starts a new connection
   */
  private boolean startConnection()
  {
    long now = getCurrentTimeActual();

    updateIdleExpireTime(now);

    int id = _gId.incrementAndGet();

    _launcher.launchChildThread(id);
        
    return true;
  }
  
  protected void onThreadMax()
  {
  }
  
  protected void onThrottle(String msg)
  {
    log.warning(msg);
  }
  
  //
  // statistics
  //

  public int getThreadCount()
  {
    return _threadCount.get();
  }

  public int getIdleCount()
  {
    return _idleCount.get();
  }

  public int getStartingCount()
  {
    return _startingCount.get();
  }

  public long getCreateCountTotal()
  {
    return _createCountTotal.get();
  }
}
