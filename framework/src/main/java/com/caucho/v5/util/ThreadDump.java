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

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import com.caucho.v5.config.ConfigException;

/**
 * Generate a thread dump
 */
public class ThreadDump
{
  private static Logger log = Logger.getLogger(ThreadDump.class.getName());

  private static AtomicReference<ThreadDump> _threadDumpRef = 
    new AtomicReference<ThreadDump>();
  
  /**
   * Returns the singleton instance, creating if necessary.   An instance of 
   * com.caucho.server.admin.ProThreadDump will be returned if available and 
   * licensed.  ProThreadDump includes the URI of the request the thread is
   * processing, if applicable.
   */
  public static ThreadDump create()
  {
    ThreadDump threadDump = _threadDumpRef.get();
    
    if (threadDump == null) {
      threadDump = new ThreadDumpPro();
      
      _threadDumpRef.compareAndSet(null, threadDump);
      threadDump = _threadDumpRef.get();
    }
    
    return threadDump;
  }
  
  protected ThreadDump()
  {
    
  }
  
  /**
   * Log all threads to com.caucho.v5.util.ThreadDump at info level.  Uses cached 
   * dump if recent (30s).
   */
  public void dumpThreads()
  {
    log.info(getThreadDump());
  }
  
  /**
   * Returns dump of all threads.  Uses cached dump if recent (30s).
   */
  public String getThreadDump()
  {
    return getThreadDump(32, false);
  }

  /**
   * Log threads to com.caucho.v5.util.ThreadDump at info level.  Optionally 
   * uses cached dump.
   * @param onlyActive if true only running threads are logged
   */
  public void dumpThreads(int depth, boolean onlyActive)
  {
    log.info(getThreadDump(depth, onlyActive));
  }

  /**
   * Returns dump of threads.  Optionally uses cached dump.
   * @param onlyActive if true only running threads are logged
   */
  public String getThreadDump(int depth, boolean onlyActive)
  {
    ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

    long []ids = threadBean.getAllThreadIds();
    ThreadInfo []info = threadBean.getThreadInfo(ids, depth);

    StringBuilder sb = new StringBuilder();
    sb.append("Thread Dump generated " + new Date(CurrentTime.currentTime()));

    Arrays.sort(info, new ThreadCompare());
    
    buildThreads(sb, info, Thread.State.RUNNABLE, false);
    buildThreads(sb, info, Thread.State.RUNNABLE, true);
    
    if (! onlyActive) {
      buildThreads(sb, info, Thread.State.BLOCKED, false);
      buildThreads(sb, info, Thread.State.WAITING, false);
      buildThreads(sb, info, Thread.State.TIMED_WAITING, false);
      buildThreads(sb, info, null, false);
    }

    return sb.toString();
  }

  protected void buildThreads(StringBuilder sb,
                              ThreadInfo []infoArray,
                              Thread.State matchState,
                              boolean isNative)
  {
    for (int i = 0; i < infoArray.length; i++) {
      ThreadInfo info = infoArray[i];

      if (info == null)
        continue;
      
      ThreadInfo nextInfo = i + 1 < infoArray.length ? infoArray[i + 1] : null;

      Thread.State state = info.getThreadState();

      if (matchState == Thread.State.RUNNABLE
          && (isNative != info.isInNative())) {
        continue;
      }

      if (state == matchState) {
        buildThread(sb, info, nextInfo);
      }
      else if (state == null
               && matchState != Thread.State.RUNNABLE
               && matchState != Thread.State.BLOCKED
               && matchState != Thread.State.WAITING
               && matchState != Thread.State.TIMED_WAITING) {
        buildThread(sb, info, nextInfo);
      }
    }
  }

  protected void buildThread(StringBuilder sb, 
                             ThreadInfo info,
                             ThreadInfo nextInfo)
  {
    sb.append("\n\"");
    sb.append(info.getThreadName());
    sb.append(" " + info.getThreadState());

    if (info.isInNative())
      sb.append(" (in native)");

    if (info.isSuspended())
      sb.append(" (suspended)");

    String lockName = info.getLockName();
    if (lockName != null) {
      sb.append("\n    waiting on ");
      sb.append(lockName);

      if (info.getLockOwnerName() != null) {
        sb.append("\n    owned by \"");
        sb.append(info.getLockOwnerName());
        sb.append("\"");
      }
    }

    sb.append("\n");
    
    if (nextInfo != null
        && threadCmpString(info).equals(threadCmpString(nextInfo)))
      return;

    StackTraceElement []stackList = info.getStackTrace();
    if (stackList == null)
      return;

    for (StackTraceElement stack : stackList) {
      sb.append("  at ");
      sb.append(stack.getClassName());
      sb.append(".");
      sb.append(stack.getMethodName());

      if (stack.getFileName() != null) {
        sb.append(" (");
        sb.append(stack.getFileName());
        if (stack.getLineNumber() > 0) {
          sb.append(":");
          sb.append(stack.getLineNumber());
        }
        sb.append(")");
      }

      if (stack.isNativeMethod())
        sb.append(" (native)");

      sb.append("\n");
    }
  }

  public String jsonThreadDump()
  {
    StringBuilder sb = new StringBuilder();
    
    long timestamp = CurrentTime.currentTime();
    
    sb.append("{\n");
    sb.append("  \"create_time\": \"" + new Date(timestamp) + "\",\n");
    sb.append("  \"timestamp\": " + timestamp + ",\n");
    sb.append("  \"thread_dump\" : {\n");
    
    ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

    long []ids = threadBean.getAllThreadIds();
    ThreadInfo []infoList = threadBean.getThreadInfo(ids, true, true);
    
    boolean isFirst = true;

    for (ThreadInfo threadInfo : infoList) {
      if (threadInfo == null)
        continue;
      
      if (! isFirst) {
        sb.append(",\n");
      }
      isFirst = false;
      
      jsonDumpThread(sb, threadInfo);
    }
    
    sb.append("\n  }");
    sb.append("\n}");

    return sb.toString();
  }
  
  private void jsonDumpThread(StringBuilder sb, ThreadInfo info)
  {
    sb.append("\"" + info.getThreadId() + "\" : {");
    
    sb.append("\n  \"id\" : " + info.getThreadId());
    
    sb.append(",\n  \"name\" : \"");
    escapeString(sb, info.getThreadName());
    sb.append("\"");
    
    /*
    sb.append(",\n  \"description\" : \"");
    escapeString(sb, info.getDescription());
    sb.append("\"");
    */
    
    sb.append(",\n  \"state\" : \"" + info.getThreadState() + "\"");
    
    if (info.isInNative()) {
      sb.append(",\n  \"native\" : true");
    }
    
    if (info.getLockName() != null) {
      sb.append(",\n  \"lock\" : {");
      
      sb.append("\n    \"name\" : \"");
      escapeString(sb, info.getLockName());
      sb.append("\"");
      
      sb.append(",\n    \"owner_id\" : " + info.getLockOwnerId());
      
      if (info.getLockOwnerName() != null) {
        sb.append(",\n    \"owner_name\" : \"");
        escapeString(sb, info.getLockOwnerName());
        sb.append("\"");
      }
      
      sb.append("\n  }");
    }
    
    jsonDumpStackTrace(sb, info.getStackTrace());
    jsonDumpMonitors(sb, info.getLockedMonitors())
    ;
    sb.append("\n}");
  }

  private void jsonDumpStackTrace(StringBuilder sb,
                                  StackTraceElement []stackTrace)
  {
    if (stackTrace == null)
      return;
    
    sb.append(",\n  \"stack\" : [\n");
    
    for (int i = 0; i < stackTrace.length; i++) {
      StackTraceElement elt = stackTrace[i];
      
      if (i != 0)
        sb.append(",\n");
      
      sb.append("  {");
      
      sb.append("\n    \"class\" : \"" + elt.getClassName() + "\"");
      sb.append(",\n    \"method\" : \"" + elt.getMethodName() + "\"");
      
      if (elt.getFileName() != null) {
        sb.append(",\n    \"file\" : \"" + elt.getFileName() + "\"");
        sb.append(",\n    \"line\" : \"" + elt.getLineNumber() + "\"");
      }
      
      if (elt.isNativeMethod())
        sb.append(",\n    \"native\" : true");
      
      sb.append("\n  }");
    }
    
    sb.append("]");
  }
  
  private void jsonDumpMonitors(StringBuilder sb, 
                                MonitorInfo[] lockedMonitors)
  {
    if (lockedMonitors == null || lockedMonitors.length == 0)
      return;
    
    sb.append(",\n  \"monitors\" : [\n");
    
    for (int i = 0; i < lockedMonitors.length; i++) {
      MonitorInfo info = lockedMonitors[i];
      
      if (i != 0)
        sb.append(",\n");
      
      sb.append("  {\n");
      
      sb.append("    \"depth\" : " + info.getLockedStackDepth());
      sb.append(",\n    \"class\" : \"" + info.getClassName() + "\"");
      sb.append(",\n    \"hash\" : \"" + info.getIdentityHashCode() + "\"");
      
      sb.append("  }");
    }
    
    sb.append("\n  ]");
  }
  
  private void escapeString(StringBuilder sb, String value)
  {
    int len = value.length();
    
    for (int i = 0; i < len; i++) {
      char ch = value.charAt(i);
      
      switch (ch) {
      case '"':
        sb.append("\\\"");
        break;
      case '\\':
        sb.append("\\\\");
        break;
      default:
        sb.append(ch);
      }
    }
  }
  
  static String threadCmpString(ThreadInfo info)
  {
    if (info == null)
      return "";
    
    StackTraceElement []stackList = info.getStackTrace();
    
    if (stackList == null)
      return "";
    
    StringBuilder sb = new StringBuilder();
    
    if (info.getThreadState() == Thread.State.RUNNABLE)
      sb.append("A-RUNNABLE");
    else
      sb.append(info.getThreadState());
    
    sb.append(" " + info.isInNative());
    
    for (int i = stackList.length - 1; i >= 0; i--) {
      sb.append("\n").append(stackList[i].getClassName());
      sb.append(".").append(stackList[i].getMethodName());
      
      if (stackList[i].getFileName() != null) {
        sb.append("(").append(stackList[i].getFileName());
        sb.append(".").append(stackList[i].getLineNumber());
        sb.append(")");
      }
    }
    
    return sb.toString();
    
  }

  static class ThreadCompare implements Comparator<ThreadInfo> {
    public int compare(ThreadInfo a, ThreadInfo b)
    {
      if (a == b)
        return 0;
      else if (a == null)
        return -1;
      else if (b == null)
        return 1;
      
      String cmpA = threadCmpString(a);
      String cmpB = threadCmpString(b);
      
      int cmp = cmpA.compareTo(cmpB);
      
      if (cmp != 0)
        return cmp;
      
      return a.getThreadName().compareTo(b.getThreadName());
    }
  }
}
