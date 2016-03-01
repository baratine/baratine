/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.profile;

import java.util.*;

/**
 * A profile entry
 */
public class ProfileEntry
{
  private static final int THREAD_ALIVE = 0x0001;
  private static final int THREAD_TERMINATED = 0x0002;
  public static final int THREAD_RUNNABLE = 0x0004;
  private static final int THREAD_BLOCKED = 0x0400;
  private static final int THREAD_WAITING = 0x0080;
  private static final int THREAD_WAITING_INDEFINITELY = 0x0010;
  private static final int THREAD_WAITING_WITH_TIMEOUT = 0x0020;
  private static final int THREAD_IN_OBJECT_WAIT = 0x0040;
  private static final int THREAD_SLEEPING = 0x0040;
  private static final int THREAD_PARKED = 0x0200;
  private static final int THREAD_JNI = 0x400000;
  
  private final int _state;
  private final long _count;
  private final long _size;

  private ArrayList<StackEntry> _stack = new ArrayList<StackEntry>();

  public ProfileEntry(int state, long count, long size)
  {
    _state = state;
    _count = count;
    _size = size;
  }

  public void addStack(String className,
                       String methodName,
                       String argString)
  {
    _stack.add(new StackEntry(className, methodName, argString));
  }
  
  public boolean isBlocked()
  {
    return (_state & THREAD_BLOCKED) != 0;
  }

  public boolean isWaiting()
  {
    return (_state & (THREAD_WAITING
                      |THREAD_WAITING_INDEFINITELY
                      |THREAD_WAITING_WITH_TIMEOUT)) != 0;
  }
  
  public boolean isRunnable()
  {
    return (_state & THREAD_RUNNABLE) != 0;
  }
  
  public boolean isJni()
  {
    return (_state & THREAD_JNI) != 0;
  }
  
  public boolean isParked()
  {
    return (_state & THREAD_PARKED) != 0;
  }
  
  public boolean isSleeping()
  {
    return (_state & THREAD_SLEEPING) != 0;
  }

  public String getState()
  {
    if (isRunnable()) {
      if (isJni())
        return "RUNNABLE (JNI)";
      else
        return "RUNNABLE";
    }
    else if (isBlocked())
      return "BLOCKED";
    else if (isWaiting())
      return "WAITING";
    else if (isParked())
      return "PARKED";
    else if (isSleeping())
      return "SLEEPING";
    else
      return "UNKNOWN(" + Integer.toHexString(_state) + ")";
  }

  public long getCount()
  {
    return _count;
  }

  public ArrayList<StackEntry> getStackTrace()
  {
    return _stack;
  }

  public String getDescription()
  {
    for (int i = 0; i < _stack.size(); i++) {
      StackEntry entry = _stack.get(i);

      if (! "".equals(entry.getArg())
          && "com.caucho.sql.UserStatement".equals(entry.getClassName())
          && "execute".equals(entry.getMethodName())) {
        return "SQL-query: '"  + entry.getArg() + "'";
      }
    }
    
    for (int i = 0; i < _stack.size(); i++) {
      StackEntry entry = _stack.get(i);

      String className = entry.getClassName();
      String methodName = entry.getMethodName();

      if ("sun.misc.Unsafe".equals(className)) {
        continue;
      }

      if ("java.util.concurrent.locks.LockSupport".equals(className)) {
        continue;
      }
      
      if ("java.lang.Object".equals(className)
          && "wait".equals(methodName)) {
        continue;
      }

      return entry.getDescription();
    }
    
    if (_stack.size() > 0)
      return _stack.get(0).getDescription();
    else
      return "unknown";
  }

  public String getOverview(long ms)
  {
    StringBuilder sb = new StringBuilder();

    sb.append((_count * ms) + "ms " + getDescription());
    sb.append("\n");

    for (StackEntry entry : _stack) {
      sb.append("  " + entry.getDescription() + "\n");
    }

    return sb.toString();
  }

  public String toString()
  {
    return getClass().getSimpleName() + "[" + _count + "," + _size + "]" + _stack;
  }
}
