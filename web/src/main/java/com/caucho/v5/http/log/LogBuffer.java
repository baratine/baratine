/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
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

package com.caucho.v5.http.log;

import java.util.concurrent.locks.LockSupport;

/**
 * Holds the HTTP buffers for keepalive reuse.  Because a request needs a
 * large number of buffers, but a keepalive doesn't need those buffers,
 * Resin can recycle the buffers during keepalives to keep the memory
 * consumption low.
 */
public final class LogBuffer // implements MessageOutbox<LogBuffer>
{
  private final boolean _isPrivate;
  private final byte []_logBuffer;
  private volatile int _length;
  
  private volatile Thread _thread;

  public LogBuffer(int size)
  {
    this(size, false);
  }

  public LogBuffer(int size, boolean isPrivate)
  {
    _logBuffer = new byte[size];
    _isPrivate = isPrivate;
  }

  public boolean isPrivate()
  {
    return _isPrivate;
  }

  public final byte []getBuffer()
  {
    return _logBuffer;
  }

  public final void setLength(int length)
  {
    _length = length;
  }

  public final int getLength()
  {
    return _length;
  }
  
  public final boolean allocate(AccessLogWriter logWriter)
  {
    /*
    if (true)
      return _length == 0;
      */
    
    if (_length == 0) {
      return true;
    }
    
    if (logWriter.isBufferAvailable()) {
      return false;
    }
    
    logWriter.wake();
    
    _thread = Thread.currentThread();
    try {
      if (_length != 0) {
        LockSupport.parkNanos(250 * 1000L);
      }
    } finally {
      _thread = null;
    }
    
    return _length == 0;
  }
  
  public final void clear()
  {
    _length = 0;

    Thread thread = _thread;
    
    if (thread != null) {
      LockSupport.unpark(thread);
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
