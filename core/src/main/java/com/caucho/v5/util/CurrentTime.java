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

import java.util.concurrent.locks.LockSupport;
import java.util.logging.Logger;

import com.caucho.v5.loader.DynamicClassLoader;

/**
 * The CurrentTime class returns the current system time.
 */
public class CurrentTime
{
  private static Logger _log;

  private static final CurrentTimeThread _currentTimeThread;
  
  private static volatile long _currentTime = System.currentTimeMillis();

  private static volatile boolean _isCurrentTimeUsed;
  private static volatile boolean _isSlowTime;

  private static final boolean _isStressTest;

  private static long _testTime;
  private static long _testNanoDelta;
  
  private CurrentTime()
  {
  }
  
  public static boolean isActive()
  {
    return _testTime == 0 && _currentTimeThread != null;
  }
  
  /**
   * Returns the approximate current time in milliseconds.
   * Convenient for minimizing system calls.
   */
  public static long getCurrentTime()
  {
    if (_testTime > 0)
      return _testTime;
    else
      return System.currentTimeMillis();

    /*
    if (_testTime > 0)
      return _testTime;
    else
      return System.currentTimeMillis();
      */
/*
    // test avoids extra writes on multicore machines
    if (! _isCurrentTimeUsed) {
      if (_testTime > 0)
        return _testTime;
      
      if (_currentTimeThread == null)
        return System.currentTimeMillis();
      
      if (_isSlowTime) {
        _isSlowTime = false;
        _currentTime = System.currentTimeMillis();
        _isCurrentTimeUsed = true;
        
        LockSupport.unpark(_currentTimeThread);
      }
      else {
        _isCurrentTimeUsed = true;
      }
    }

    return _currentTime;
    */
  }

  /**
   * Gets current time, handling test
   */
  public static long getCurrentTimeActual()
  {
    if (_testTime > 0)
      return System.currentTimeMillis();
    else
      return getCurrentTime();
  }

  /**
   * Returns the exact current time in milliseconds.
   */
  public static long getExactTime()
  {
    if (_testTime > 0)
      return _testTime;
    else {
      return System.currentTimeMillis();
    }
  }

  /**
   * Returns the exact current time in nanoseconds.
   */
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

  /**
   * Returns true for testing.
   */
  public static boolean isTest()
  {
    return _testTime > 0;
  }

  /**
   * Yield if in test mode to maintain ordering
   */
  public static void yieldIfTest()
  {
    if (_testTime > 0) {
      // Thread.yield();
    }
  }
  
  static void setTestTime(long time)
  {
    _testTime = time;

    if (_testTime > 0) {
      if (time < _currentTime) {
        Alarm.testClear();
      }

      _currentTime = time;
    }
    else {
      _currentTime = System.currentTimeMillis();
    }
    
    Alarm.setAlarmTestTime(time);
  }
  
  static void testClear()
  {
    Alarm.testClear();
  }

  static void setTestNanoDelta(long delta)
  {
    _testNanoDelta = delta;
  }

  static class CurrentTimeThread extends Thread {
    CurrentTimeThread()
    {
      super("baratine-timer");

      setDaemon(true);
      setPriority(Thread.MAX_PRIORITY);
    }

    @Override
    public void run()
    {
      int idleCount = 0;

      while (true) {
        try {
          if (_testTime > 0) {
            _currentTime = _testTime;

            LockSupport.park();
            
            continue;
          }
          
          long now = System.currentTimeMillis();
            
          _currentTime = now;
            
          /*
          boolean isCurrentTimeUsed = _isCurrentTimeUsed;
          _isCurrentTimeUsed = false;
          
          if (isCurrentTimeUsed) {
            idleCount = 0;
            _isSlowTime = false;
          }
          else {
            idleCount++;

            if (idleCount >= 10) {
              _isSlowTime = true;
            }
          }

          if (_isSlowTime) {
            long sleepTime = 1000L;
              
            LockSupport.parkNanos(sleepTime * 1000000L);
          }
          else {
            Thread.sleep(20);
          }
          */
          Thread.sleep(10);
        } catch (Throwable e) {
          e.printStackTrace();
        }
      }
    }
  }
  
  private static Logger log()
  {
    if (_log == null) 
      _log = Logger.getLogger(CurrentTime.class.getName());
    
    return _log;
  }

  static {
    ClassLoader systemLoader = null;
    CurrentTimeThread currentTimeThread = null;
    ClassLoader loader = CurrentTime.class.getClassLoader();

    try {
      systemLoader = ClassLoader.getSystemClassLoader();
    } catch (Throwable e) {
    }

    try {
      boolean isAlarmStart = System.getProperty("caucho.alarm.enable") != null;

      if (isAlarmStart
          || loader == null
          || loader instanceof DynamicClassLoader
          || loader == systemLoader
          || systemLoader != null && loader == systemLoader.getParent()) {
        //currentTimeThread = new CurrentTimeThread();
        //currentTimeThread.start();
      }
    } catch (Throwable e) {
      // should display for security manager issues
      log().fine("Alarm not started: " + e);
    }

    // _systemLoader = systemLoader;
    _currentTimeThread = currentTimeThread;

    _isStressTest = System.getProperty("caucho.stress.test") != null;
  }
}
