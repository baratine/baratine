/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.util;

import java.lang.management.ThreadInfo;

import com.caucho.v5.http.protocol.RequestHttpBase;
import com.caucho.v5.network.NetworkSystemBartender;
import com.caucho.v5.network.port.ConnectionTcp;

public class ThreadDumpPro extends ThreadDump
{
  public ThreadDumpPro()
  {
  }
  
  protected void buildThread(StringBuilder sb, ThreadInfo info)
  {
    sb.append("\n\"");
    sb.append(info.getThreadName());
    sb.append("\" id=" + info.getThreadId());
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

    NetworkSystemBartender networkSystem = NetworkSystemBartender.current();

    if (networkSystem != null) {
      ConnectionTcp conn
        = networkSystem.findConnectionByThreadId(info.getThreadId());

      if (conn != null && conn.protocol() instanceof RequestHttpBase) {
        RequestHttpBase req = (RequestHttpBase) conn.protocol();

        if (true) throw new UnsupportedOperationException();
        /* XXX:
        if (req.getRequestURI() != null) {
          sb.append("   ").append(req.getRequestURI()).append("\n");
        }
        */
      }
    }

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
}
