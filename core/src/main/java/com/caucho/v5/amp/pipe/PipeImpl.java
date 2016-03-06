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
import java.util.concurrent.atomic.AtomicReference;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.message.PipeWakeMessage;
import com.caucho.v5.amp.queue.QueueRing;
import com.caucho.v5.amp.spi.OutboxAmp;

import io.baratine.io.InPipe;
import io.baratine.io.OutPipe;

/**
 * pipe implementation
 */
public class PipeImpl<T> implements OutPipe<T>
{
  private InPipe<T> _inPipe;
  private QueueRing<T> _queue;
  private boolean _isOk;
  
  private AtomicReference<StatePipe> _stateRef
    = new AtomicReference<>(StatePipe.IDLE);
  
  private ServiceRefAmp _subscriberRef;
  
  public PipeImpl(ServiceRefAmp serviceRef, 
                  InPipe<T> inPipe)
  {
    Objects.requireNonNull(serviceRef);
    Objects.requireNonNull(inPipe);
    
    _inPipe = inPipe;
    
    _queue = new QueueRing<>(32);
    
    _subscriberRef = serviceRef;
  }

  @Override
  public void next(T value)
  {
    Objects.requireNonNull(value);
    
    InPipe<T> inPipe = _inPipe;
    
    if (inPipe != null) {
      _queue.offer(value);
      wake();
    }
  }

  @Override
  public void ok()
  {
    _isOk = true;
  }

  @Override
  public void fail(Throwable exn)
  {
    InPipe<T> inPipe = _inPipe;
    _inPipe = null;

    if (inPipe != null) {
      inPipe.fail(exn);
    }
  }

  @Override
  public int available()
  {
    // TODO Auto-generated method stub
    return 0;
  }
  
  private void wake()
  {
    OutboxAmp outbox = OutboxAmp.current();
    Objects.requireNonNull(outbox);
    
    PipeWakeMessage<T> msg = new PipeWakeMessage<>(outbox, _subscriberRef, this);
    
    outbox.offer(msg);
  }
  
  public void read()
  {
    T msg;
    
    InPipe<T> inPipe = _inPipe;
    
    while ((msg = _queue.poll()) != null) {
      inPipe.next(msg);
    }
    
    if (_isOk) {
      inPipe.ok();
    }
  }
  
  enum StatePipe {
    IDLE,
    ACTIVE,
    WAKE;
  }
}
