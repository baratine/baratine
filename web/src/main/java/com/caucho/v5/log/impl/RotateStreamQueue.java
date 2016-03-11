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

package com.caucho.v5.log.impl;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.caucho.v5.amp.outbox.DeliverOutbox;
import com.caucho.v5.amp.outbox.Outbox;
import com.caucho.v5.amp.outbox.QueueService;
import com.caucho.v5.amp.queue.QueueServiceBuilderImpl;
import com.caucho.v5.util.ConcurrentArrayList;

import io.baratine.service.ResultFuture;

/**
 * Queue for the rotating stream.
 */
public class RotateStreamQueue //extends DeliverAmpBase<LogItem<?>>
  implements LogItemBinaryHandler, LogItemStringHandler, DeliverOutbox<LogItem<?>>
{
  private final ConcurrentArrayList<RotateStream> _streamList
    = new ConcurrentArrayList<>(RotateStream.class);

  private final QueueService<LogItem<?>> _queue;

  private RotateStream _lastStream;

  /**
   * Create rotate stream.
   *
   * @param path underlying log path
   */
  RotateStreamQueue()
  {
    _queue = buildQueue();
  }
  
  RotateStreamQueue(RotateStream stream)
  {
    this();
    
    addStream(stream);
  }
  
  void addStream(RotateStream stream)
  {
    _streamList.add(stream);
  }
  
  private QueueService<LogItem<?>> buildQueue()
  {
    QueueServiceBuilderImpl builder
      = new QueueServiceBuilderImpl<>();
    builder.initial(256);
    builder.capacity(16 * 1024);
    
    //return builder.build(new RotateLogQueue(stream));
    return builder.build(this);
  }

  /**
   * Writes to the stream
   */
  public void write(RotateStream stream, byte []buffer, int offset, int length)
    throws IOException
  {
    try {
      _queue.offer(new LogItemBinary(stream, buffer, offset, length), 
                   10, TimeUnit.SECONDS);
      _queue.wake();
    } catch (Throwable e) {
      e.printStackTrace(EnvironmentStream.getOriginalSystemErr());
    }
  }
  
  public void waitForFlush(ResultFuture<Boolean> future)
  {
    _queue.offer(new LogItemFuture(future));
    _queue.wake();
  }

  @Override
  public void deliver(LogItem<?> value, Outbox outbox)
    throws Exception
  {
    ((LogItem) value).deliver(this);
  }
    
  @Override
  public void onString(RotateStream stream, String msg)
  {
    stream.onString(msg);
    
    onWrite(stream);
  }
    
  @Override
  public void onBinary(RotateStream stream, byte []buffer, int offset, int length)
  {
    stream.onBinary(buffer, offset, length);
    
    onWrite(stream);
  }
  
  private void onWrite(RotateStream stream)
  {
    RotateStream lastStream = _lastStream;
    
    if (lastStream == stream) {
      
    }
    else if (lastStream == null) {
      _lastStream = stream;
    }
    else {
      lastStream.onAfterBatch();
      _lastStream = stream;
    }
  }
    
  @Override
  public void flush(RotateStream stream)
  {
    stream.onFlush();
  }

  @Override
  public void flush()
  {
    for (RotateStream stream : _streamList) {
      stream.onFlush();
    }
  }

  @Override
  public void afterBatch()
  {
    RotateStream lastStream = _lastStream;
    
    if (lastStream != null) {
      _lastStream = null;
      lastStream.onAfterBatch();
    }
    /*
    for (RotateStream stream : _streamList) {
      stream.onAfterBatch();
    }
    */
  }
}
