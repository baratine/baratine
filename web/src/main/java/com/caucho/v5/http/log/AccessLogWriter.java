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

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.deliver.Deliver;
import com.caucho.v5.amp.deliver.Outbox;
import com.caucho.v5.amp.deliver.QueueDeliver;
import com.caucho.v5.config.types.Bytes;
import com.caucho.v5.log.impl.RolloverLogBase;
import com.caucho.v5.store.temp.TempFileSystem;
import com.caucho.v5.store.temp.TempWriter;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.FreeRing;
import com.caucho.v5.util.L10N;

/**
 * Represents an log of every top-level request to the server.
 */
public class AccessLogWriter extends RolloverLogBase
{
  protected static final L10N L = new L10N(AccessLogWriter.class);
  protected static final Logger log
    = Logger.getLogger(AccessLogWriter.class.getName());

  //private final AccessLogBase _log;

  // private boolean _isAutoFlush;
  private final Object _bufferLock = new Object();
  
  private int _logBufferSize = 1024;

  private final FreeRing<LogBuffer> _freeList
    = new FreeRing<LogBuffer>(512);

  //private final LogWriterTask _logWriterTask = new LogWriterTask();
  private final QueueDeliver<LogBuffer> _logWriterQueue;
  
  private TempFileSystem _tempService;

  AccessLogWriter(AccessLogBase log)
  {
    //_log = log;

    // LogWriterTask task = new LogWriterTask();
    
    _logWriterQueue = QueueDeliver.<LogBuffer>newQueue()
                                  .size(16 * 1024)
                                  .build(new LogWriterTask());
    /*
    _logBuffer = getLogBuffer();
    _buffer = _logBuffer.getBuffer();
    _length = 0;
    */

    // _semaphoreProbe = MeterService.createSemaphoreMeter("Resin|Log|Semaphore");
    
    _tempService = TempFileSystem.getCurrent();
    
    if (_tempService == null)
      throw new IllegalStateException(L.l("'{0}' is required for AccessLog",
                                          TempFileSystem.class.getSimpleName()));
  }

  void setBufferSize(Bytes bytes)
  {
    _logBufferSize = (int) bytes.getBytes();
  }

  int getBufferSize()
  {
    return _logBufferSize;
  }

  @Override
  public void init()
    throws IOException
  {
    super.init();

    // _isAutoFlush = _log.isAutoFlush();
    
    for (int i = 0; i < 64; i++) {
      _freeList.free(new LogBuffer(_logBufferSize));
    }
  }
  
  boolean isBufferAvailable()
  {
    return _freeList.getSize() >= 16;
  }

  void writeThrough(byte []buffer, int offset, int length)
    throws IOException
  {
    synchronized (_bufferLock) {
      write(buffer, offset, length);
    }

    flushStream();
  }

  void writeBuffer(LogBuffer buffer)
  {
    _logWriterQueue.offer(buffer);
    _logWriterQueue.wake();
  }

  // must be synchronized by _bufferLock.
  @Override
  public void flush()
  {
    // server/021g
    _logWriterQueue.wake();
    
    waitForFlush(10);
    
    try {
      super.flush();
    } catch (IOException e) {
      log.log(Level.FINER, e.toString(), e);
    }
  }
  
  protected void wake()
  {
    _logWriterQueue.wake();
  }

  protected void waitForFlush(long timeout)
  {
    long expire;

    expire = CurrentTime.getCurrentTimeActual() + timeout;

    while (true) {
      if (_logWriterQueue.isEmpty()) {
        return;
      }

      long delta;
      delta = expire - CurrentTime.getCurrentTimeActual();

      if (delta < 0)
        return;

      if (delta > 50) {
        delta = 50;
      }

      try {
        _logWriterQueue.wake();

        Thread.sleep(delta);
      } catch (Exception e) {
      }
    }
  }

  LogBuffer allocateBuffer()
  {
    LogBuffer buffer = _freeList.allocate();

    if (buffer == null) {
      buffer = new LogBuffer(_logBufferSize);
    }

    return buffer;
  }

  void freeBuffer(LogBuffer logBuffer)
  {
    logBuffer.clear();
    
    if (! logBuffer.isPrivate()) {
      _freeList.free(logBuffer);
    }
  }

  @Override
  protected TempWriter createTempStream()
  {
    return _tempService.getManager().openWriter();
  }

  @Override
  public void close()
    throws IOException
  {
    try {
      flush();
    } finally {
      super.close();
    }
  }
  /**
   * Closes the log, flushing the results.
   */
  public void destroy()
    throws IOException
  {
    _logWriterQueue.close();
  }

  class LogWriterTask implements Deliver<LogBuffer>
  {
    LogWriterTask()
    {
      //super(16 * 1024);
    }

    @Override
    public void deliver(LogBuffer value, Outbox outbox)
    {
      if (value == null) {
        return;
        
      }
      try {
        write(value.getBuffer(), 0, value.getLength());
      } catch (Throwable e) {
        log.log(Level.WARNING, e.toString(), e);
      } finally {
        freeBuffer(value);
      }
    }

    @Override
    public void afterBatch()
    {
      try {
        flushStream();
      } catch (IOException e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }
    
    public void close()
    {
      wake();
    }
    
    @Override
    public String toString()
    {
      Path path = getPath();
      
      return getClass().getSimpleName() + "[" + path + "]";
    }
  }
}
