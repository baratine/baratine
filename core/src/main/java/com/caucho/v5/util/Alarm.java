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

package com.caucho.v5.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.thread.WorkerThreadPoolBase;
import com.caucho.v5.loader.DynamicClassLoader;
import com.caucho.v5.loader.EnvLoaderListener;

/**
 * The alarm class provides a lightweight event scheduler.  This allows
 * an objects to schedule a timeout without creating a new thread.
 *
 * <p>A separate thread periodically tests the queue for alarms ready.
 */
public class Alarm implements EnvLoaderListener, Runnable
{
  private static final Logger log
    = Logger.getLogger(Alarm.class.getName());

  private static final ClassLoader _systemLoader;
  
  private static AtomicReferenceFieldUpdater<Alarm,Alarm> _nextUpdater;
  private static AtomicLongFieldUpdater<Alarm> _wakeTimeUpdater;

  // private static final AlarmThread _alarmThread;
  private static final CoordinatorThread _coordinatorThread;

  /*
  private static volatile long _currentTime = System.currentTimeMillis();

  private static volatile boolean _isCurrentTimeUsed;
  private static volatile boolean _isSlowTime;
  */

  // private static final AlarmHeap _heap = new AlarmHeap();
  private static final AlarmClock _clock = new AlarmClock();

  private static final AtomicInteger _runningAlarmCount
    = new AtomicInteger();

  private static final boolean _isStressTest;

  /*
  private static long _testTime;
  private static long _testNanoDelta;
  */
  
  private volatile Alarm _next;
  private volatile long _wakeTime;
  
  private AlarmListener _listener;
  private ClassLoader _contextLoader;
  private String _name;

  private boolean _isPriority = true;

  // private int _heapIndex = 0;
  private int _bucket = -1;

  private volatile boolean _isRunning;
  
  /**
   * Create a new wakeup alarm with a designated listener as a callback.
   * The alarm is not scheduled.
   */
  protected Alarm()
  {
    this("alarm");
  }

  protected Alarm(String name)
  {
    if (_coordinatorThread == null) {
      throw new IllegalStateException("Alarm cannot be instantiated because Baratine is running inside a foreign classloader."
                                      + "\n  " + Alarm.class.getClassLoader());
    }
    
    _name = name;

    addEnvironmentListener();
  }

  /**
   * Create a new wakeup alarm with a designated listener as a callback.
   * The alarm is not scheduled.
   */
  public Alarm(AlarmListener listener)
  {
    this("alarm[" + listener + "]", listener);
  }

  /**
   * Create a new wakeup alarm with a designated listener as a callback.
   * The alarm is not scheduled.
   */
  public Alarm(String name, AlarmListener listener)
  {
    this(name, listener, Thread.currentThread().getContextClassLoader());
  }

  /**
   * Create a new wakeup alarm with a designated listener as a callback.
   * The alarm is not scheduled.
   */
  public Alarm(String name, AlarmListener listener, ClassLoader loader)
  {
    this(name);

    setListener(listener);
    setContextLoader(loader);
  }

  /**
   * Create a new wakeup alarm with a designated listener as a callback.
   * The alarm is not scheduled.
   */
  public Alarm(String name,
               AlarmListener listener,
               long delta,
               ClassLoader loader)
  {
    this(name);

    setListener(listener);
    setContextLoader(loader);

    runAfter(delta);
  }

  /**
   * Creates a named alarm and schedules its wakeup.
   *
   * @param name the object prepared to receive the callback
   * @param listener the object prepared to receive the callback
   * @param delta the time in milliseconds to wake up
   */
  public Alarm(String name, AlarmListener listener, long delta)
  {
    this(name, listener);

    runAfter(delta);
  }

  /**
   * Creates a new alarm and schedules its wakeup.
   *
   * @param listener the object prepared to receive the callback
   * @param delta the time in milliseconds to wake up
   */
  public Alarm(AlarmListener listener, long delta)
  {
    this(listener);

    runAfter(delta);
  }

  /**
   * Returns the alarm name.
   */
  public String getName()
  {
    return _name;
  }

  /**
   * Sets the alarm name.
   */
  protected void setName(String name)
  {
    _name = name;
  }
  
  /*
  public static boolean isActive()
  {
    return _testTime == 0 && _alarmThread != null;
  }
  */
  
  /**
   * Returns the approximate current time in milliseconds.
   * Convenient for minimizing system calls.
   */
  /*
  public static long getCurrentTime()
  {
    // test avoids extra writes on multicore machines
    if (! _isCurrentTimeUsed) {
      if (_testTime > 0)
        return _testTime;
      
      if (_alarmThread == null)
        return System.currentTimeMillis();
      
      if (_isSlowTime) {
        _isSlowTime = false;
        _currentTime = System.currentTimeMillis();
        _isCurrentTimeUsed = true;
        
        LockSupport.unpark(_alarmThread);
      }
      else {
        _isCurrentTimeUsed = true;
      }
    }

    return _currentTime;
  }
  */

  /**
   * Gets current time, handling test
   */
  /*
  public static long getCurrentTimeActual()
  {
    if (_testTime > 0)
      return System.currentTimeMillis();
    else
      return getCurrentTime();
  }
  */

  /**
   * Returns the exact current time in milliseconds.
   */
  /*
  public static long getExactTime()
  {
    if (_testTime > 0)
      return _testTime;
    else {
      return System.currentTimeMillis();
    }
  }
  */

  /**
   * Returns the exact current time in nanoseconds.
   */
  /*
  public static long getExactTimeNanoseconds()
  {
    if (_testTime > 0) {
      // php/190u
      // System.nanoTime() is not related to currentTimeMillis(), so return
      // a different offset.  See System.nanoTime() javadoc

      return (_testTime - 10000000) * 1000000L + _testNanoDelta;
    }

    return System.nanoTime();
  }
  */

  /**
   * Returns true for testing.
   */
  /*
  public static boolean isTest()
  {
    return _testTime > 0;
  }
  */

  /**
   * Yield if in test mode to maintain ordering
   */
  /*
  public static void yieldIfTest()
  {
    if (_testTime > 0) {
      // Thread.yield();
    }
  }
  */

  /**
   * Returns the wake time of this alarm.
   */
  public long getWakeTime()
  {
    return _wakeTime;
  }
  
  public void setWakeTime(long wakeTime)
  {
    _wakeTime = wakeTime;
  }
  boolean setWakeTime(long prevWakeTime, long wakeTime)
  {
    return _wakeTimeUpdater.compareAndSet(this, prevWakeTime, wakeTime);
  }
  
  long getAndSetWakeTime(long wakeTime)
  {
    return _wakeTimeUpdater.getAndSet(this, wakeTime);
  }
  
  int getHeapIndex()
  {
    // return _heapIndex;
    return 0;
  }
  
  int getBucket()
  {
    return _bucket;
  }
  
  void setBucket(int bucket)
  {
    _bucket = bucket;
  }
  
  void setHeapIndex(int index)
  {
    // _heapIndex = index;
  }
  
  Alarm getNext()
  {
    return _next;
  }
  
  boolean setNext(Alarm prevNext, Alarm next)
  {
    return _nextUpdater.compareAndSet(this, prevNext, next);
  }
  
  void setNext(Alarm next)
  {
    _next = next;
  }

  /**
   * Return the alarm's listener.
   */
  public AlarmListener getListener()
  {
    return _listener;
  }

  /**
   * Sets the alarm's listener.
   */
  public void setListener(AlarmListener listener)
  {
    _listener = listener;
  }

  /**
   * Sets the alarm's context loader
   */
  public void setContextLoader(ClassLoader loader)
  {
    _contextLoader = loader;
  }

  /**
   * Sets the alarm's context loader
   */
  public ClassLoader getContextLoader()
  {
    return _contextLoader;
  }

  /**
   * Returns true if the alarm is currently queued.
   */
  public boolean isQueued()
  {
    // return _heapIndex != 0;
    // return _wakeTime != 0;
    return _bucket >= 0;
  }

  /**
   * Returns true if the alarm is currently running
   */
  boolean isRunning()
  {
    return _isRunning;
  }

  /**
   * True for a priority alarm (default)
   */
  public void setPriority(boolean isPriority)
  {
    _isPriority = isPriority;
  }

  /**
   * True for a priority alarm (default)
   */
  public boolean isPriority()
  {
    return _isPriority;
  }

  /**
   * Registers the alarm with the environment listener for auto-close
   */
  protected void addEnvironmentListener()
  {
    // Environment.addClassLoaderListener(this);
  }

  /**
   * Queue the alarm for wakeup.
   *
   * @param delta time in milliseconds to wake
   */
  public void runAfter(long delta)
  {
    long now = CurrentTime.getExactTime();
    
    // boolean isNotify = _heap.queueAt(this, now + delta);
    
    boolean isNotify = _clock.queueAt(this, now + delta);

    if (isNotify) {
      _coordinatorThread.wake();
    }
  }

  /**
   * Queue the alarm for wakeup.
   *
   * @param delta time in milliseconds to wake
   */
  public void queueAt(long wakeTime)
  {
    // boolean isNotify = _heap.queueAt(this, wakeTime);
    
    boolean isNotify = _clock.queueAt(this, wakeTime);

    if (isNotify) {
      _coordinatorThread.wake();
    }
  }

  /**
   * Remove the alarm from the wakeup queue.
   */
  public void dequeue()
  {
    /*
    if (_heapIndex > 0)
      _heap.dequeue(this);
      */
    _clock.dequeue(this);
  }

  /**
   * Runs the alarm.  This is only called from the worker thread.
   */
  @Override
  public void run()
  {
    try {
      handleAlarm();
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
    } finally {
      _isRunning = false;
      _runningAlarmCount.decrementAndGet();
    }
  }

  /**
   * Handles the alarm.
   */
  private void handleAlarm()
  {
    AlarmListener listener = getListener();

    if (listener == null) {
      return;
    }

    Thread thread = Thread.currentThread();
    ClassLoader loader = getContextLoader();

    if (loader != null) {
      thread.setContextClassLoader(loader);
    }
    else {
      thread.setContextClassLoader(_systemLoader);
    }

    try {
      listener.handleAlarm(this);
    } finally {
      thread.setContextClassLoader(_systemLoader);
    }
  }

  /**
   * Handles the case where a class loader has completed initialization
   */
  @Override
  public void classLoaderInit(DynamicClassLoader loader)
  {
  }

  /**
   * Handles the case where a class loader is dropped.
   */
  @Override
  public void classLoaderDestroy(DynamicClassLoader loader)
  {
    close();
  }

  /**
   * Closes the alarm instance
   */
  public void close()
  {
    dequeue();

    // server/16a{0,1}
    /*
    _listener = null;
    _contextLoader = null;
    */
  }

  // test

  static void testClear()
  {
    // _heap.testClear();
    _clock.testClear();
  }
  
  static void setAlarmTestTime(long time)
  {
    // Alarm alarm;

    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();

    try {
      long now = CurrentTime.currentTime();
      
      _clock.extractAlarm(now, true);
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }

  public String toString()
  {
    return "Alarm[" + _name + "]";
  }

  static class CoordinatorThread extends WorkerThreadPoolBase
  {
    private long _lastTime;
    
    @Override
    protected boolean isPermanent()
    {
      return true;
    }
    
    /**
     * Runs the coordinator task.
     */
    @Override
    public long runTask()
    {
      if (CurrentTime.isTest()) {
        return -1;
      }
      
      _lastTime = CurrentTime.getExactTime();

      while (true) {
        long now = CurrentTime.getExactTime();
        
        long next = _clock.extractAlarm(now, false);
        
        // long next = _heap.nextAlarmTime();
        // #3548 - getCurrentTime for consistency
        // long now = getCurrentTime();

        if (next < 0) {
          return 120000L;
        }
        else if (now < next) {
          return Math.min(next - now, 120000L);
        }
      }
    }

    /*
    private void dispatchAlarm()
    {
      try {
        Alarm alarm;
        
        if ((alarm = _heap.extractAlarm(getCurrentTime())) != null) {
          _runningAlarmCount.incrementAndGet();

          long now;

          if (_isStressTest)
            now = Alarm.getExactTime();
          else
            now = CurrentTime.getCurrentTime();

          long delta = now - alarm._wakeTime;

          if (delta > 10000) {
            log.warning(this + " slow alarm " + alarm + " " + delta + "ms"
                        + " coordinator-delta " + (now - _lastTime) + "ms");
          }
          else if (_isStressTest && delta > 100) {
            System.out.println(this + " slow alarm " + alarm + " " + delta
                               + " coordinator-delta " + (now - _lastTime) + "ms");
          }
          
          _lastTime = now;

          if (alarm.isPriority())
            ThreadPool.getThreadPool().schedulePriority(alarm);
          else
            ThreadPool.getThreadPool().schedule(alarm);
        }
      } catch (Exception e) {
        log.log(Level.WARNING, e.toString(), e);
      }
    }
    */
  }

  static {
    // AlarmThread alarmThread = null;
    CoordinatorThread coordinator = null;
    ClassLoader loader = Alarm.class.getClassLoader();

    try {
      coordinator = new CoordinatorThread();
      coordinator.wake();
    } catch (Throwable e) {
      e.printStackTrace();
      // should display for security manager issues
      log.fine("Alarm not started: " + e);
    }
    
    _nextUpdater
      = AtomicReferenceFieldUpdater.newUpdater(Alarm.class, Alarm.class, "_next");
    _wakeTimeUpdater
      = AtomicLongFieldUpdater.newUpdater(Alarm.class, "_wakeTime");

    // _systemLoader = systemLoader;
    _systemLoader = loader;
    // _alarmThread = alarmThread;
    _coordinatorThread = coordinator;

    _isStressTest = System.getProperty("caucho.stress.test") != null;
  }
}
