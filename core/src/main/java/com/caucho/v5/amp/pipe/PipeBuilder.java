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

package com.caucho.v5.amp.pipe;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.deliver.Deliver;
import com.caucho.v5.amp.deliver.Outbox;
import com.caucho.v5.amp.queue.QueueRingForPipe;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.util.L10N;

import io.baratine.pipe.Credits;
import io.baratine.pipe.Credits.OnAvailable;
import io.baratine.pipe.Pipe;

/**
 * pipe implementation
 */
class PipeBuilder<T>
{
  private Pipe<T> _inPipe;
  private ServiceRefAmp _inRef;
  private ServiceRefAmp _outRef;
  private int _prefetch = Pipe.PREFETCH_DEFAULT;
  private long _credits = Pipe.CREDIT_DISABLE;
  private int _capacity;
  
  Pipe<T> inPipe()
  {
    return _inPipe;
  }
  
  void inPipe(Pipe<T> inPipe)
  {
    Objects.requireNonNull(inPipe);
    
    _inPipe = inPipe;
  }
  
  ServiceRefAmp inRef()
  {
    return _inRef;
  }
  
  void inRef(ServiceRefAmp inRef)
  {
    Objects.requireNonNull(inRef);
    
    _inRef = inRef;
  }
  
  ServiceRefAmp outRef()
  {
    return _outRef;
  }
  
  void outRef(ServiceRefAmp outRef)
  {
    Objects.requireNonNull(outRef);
    
    _outRef = outRef;
  }
  
  int prefetch()
  {
    return _prefetch;
  }
  
  void prefetch(int prefetch)
  {
    _prefetch = prefetch;
  }
  
  long credits()
  {
    return _credits;
  }
  
  void credits(long credits)
  {
    _credits = credits;
  }
  
  int capacity()
  {
    return _capacity;
  }
  
  void capacity(int capacity)
  {
    _capacity = capacity;
  }
  
  PipeImpl<T> build()
  {
    return new PipeImpl<T>(this);
  }
}
