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

package com.caucho.v5.amp.deliver;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import com.caucho.v5.health.shutdown.ExitCode;
import com.caucho.v5.health.shutdown.Shutdown;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;

import io.baratine.service.QueueFullHandler;
import io.baratine.service.ServiceExceptionQueueFull;
import io.baratine.service.ServiceRef;
import io.baratine.spi.MessageApi;

/**
 * Handler for queue full messages.
 */
public class QueueFullHandlerAmp implements QueueFullHandler
{
  private static final Logger log
    = Logger.getLogger(QueueFullHandlerAmp.class.getName());
  
  private static final L10N L = new L10N(QueueFullHandlerAmp.class);
  
  private final long _duplicateTimeout = 60 * 1000L;
  private final long _fatalTimeout = 180 * 1000L;
  
  private final AtomicLong _lastExceptionTime = new AtomicLong();
  private final AtomicLong _firstSequenceTime = new AtomicLong();
  private final AtomicInteger _repeatCount = new AtomicInteger();
  
  public QueueFullHandlerAmp()
  {
  }

  @Override
  public void onQueueFull(ServiceRef service, 
                          int queueSize, 
                          long timeout,
                          TimeUnit unit, 
                          MessageApi message)
  {
    long lastExceptionTime = _lastExceptionTime.get();
    long firstSequenceTime = _firstSequenceTime.get();
    int repeatCount = _repeatCount.get();
    
    long now = CurrentTime.currentTime();
    
    _lastExceptionTime.set(now);
    
    if (now - lastExceptionTime < _duplicateTimeout) {
      _repeatCount.incrementAndGet();
      
    }
    else {
      _repeatCount.set(0);
      _firstSequenceTime.set(now);
      
      firstSequenceTime = now;
    }
    
    _lastExceptionTime.set(now);
    
    String msg;
    
    msg = L.l("Queue full in service {0} with queue-size {1} timeout {2}ms message {3}",
              service,
              queueSize,
              unit.toMillis(timeout),
              message);
    
    if (repeatCount > 0) {
      msg += L.l(" repeats {0} times.", repeatCount);
    }
    
    if (_fatalTimeout < now - firstSequenceTime) {
      Shutdown.shutdownActive(ExitCode.NETWORK, msg); 
    }
    
    if (repeatCount % 100 == 0 || _repeatCount.get() == 0) {
      log.warning(msg);
    }
    
    RuntimeException exn;
    
    exn = new ServiceExceptionQueueFull(msg);
    
    throw exn;
  }
}
