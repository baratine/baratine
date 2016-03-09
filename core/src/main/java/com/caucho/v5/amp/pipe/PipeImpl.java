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
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.message.PipeWakeInMessage;
import com.caucho.v5.amp.message.PipeWakeOutMessage;
import com.caucho.v5.amp.queue.Deliver;
import com.caucho.v5.amp.queue.Outbox;
import com.caucho.v5.amp.queue.QueueRing;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.util.L10N;

import io.baratine.io.OutFlow;
import io.baratine.io.PipeIn;
import io.baratine.io.PipeOut;

/**
 * pipe implementation
 */
public class PipeImpl<T> implements PipeOut<T>, Deliver<T>
{
  private static final L10N L = new L10N(PipeImpl.class);
  private static final Logger log = Logger.getLogger(PipeImpl.class.getName());
  
  private PipeIn<T> _inPipe;
  private QueueRing<T> _queue;
  
  private volatile boolean _isOk;
  private volatile Throwable _fail;
  
  private AtomicReference<StateInPipe> _stateInRef
    = new AtomicReference<>(StateInPipe.IDLE);
  
  private AtomicReference<StateOutPipe> _stateOutRef
    = new AtomicReference<>(StateOutPipe.IDLE);
  
  private ServiceRefAmp _inRef;
  
  private PipeInFlowImpl _inFlow = new PipeInFlowImpl();

  private int _prefetch;

  private ServiceRefAmp _outRef;

  private OutFlow _outFlow;
  
  public PipeImpl(ServiceRefAmp inRef, 
                  PipeIn<T> inPipe,
                  ServiceRefAmp outRef,
                  OutFlow outFlow)
  {
    Objects.requireNonNull(inRef);
    Objects.requireNonNull(inPipe);
    
    _inRef = inRef;
    _inPipe = inPipe;
    
    _outRef = outRef;
    _outFlow = outFlow;
    
    int prefetch = _inPipe.prefetch();
    int capacity = _inPipe.capacity();
    
    if (capacity > 0) {
      
    }
    else {
      if (prefetch <= 0) {
        prefetch = 24;
      }

      capacity = 2 * Integer.highestOneBit(prefetch);
    }
    
    _prefetch = prefetch;
    
    _inPipe.inFlow(_inFlow);
    
    _inFlow.init();
    
    _queue = new QueueRing<>(capacity);
  }

  @Override
  public void next(T value)
  {
    Objects.requireNonNull(value);
    
    if (_stateInRef.get() != StateInPipe.CLOSE) {
      if (! _queue.offer(value, 0, TimeUnit.MILLISECONDS)) {
        throw new PipeExceptionFull(L.l("full pipe for pipe.next() size={0}",
                                        _queue.size()));
      }
      
      wakeIn();
      
      int size = _queue.size();
      
      if (_prefetch <= size) {
        outFull();
      }
    }
  }

  @Override
  public void ok()
  {
    _isOk = true;
    wakeIn();
  }

  @Override
  public void fail(Throwable exn)
  {
    Objects.requireNonNull(exn);
    
    if (_fail == null) {
      _fail = exn;
    
      wakeIn();
    }
  }

  @Override
  public int available()
  {
    int size = (int) _queue.size();
    
    int available = Math.max(0, _prefetch - size);
    
    if (available <= 0) {
      outFull();
    }

    return available;
  }
  
  private void outFull()
  {
    OutFlow outFlow = _outFlow;
    
    if (outFlow == null) {
      return;
    }
    
    StateOutPipe stateOld;
    StateOutPipe stateNew;
    
    do {
      stateOld = _stateOutRef.get();
      stateNew = stateOld.toFull();
    } while (! _stateOutRef.compareAndSet(stateOld, stateNew));
    
    int size = (int) _queue.size();
    
    if (size < _prefetch) {
      do {
        stateOld = _stateOutRef.get();
        stateNew = stateOld.toWake();
      } while (! _stateOutRef.compareAndSet(stateOld, stateNew));
      
      outFlow.available(_prefetch - size);
    }
  }
  
  /**
   * Reads data from the pipe.
   */
  public void read()
  {
    StateInPipe stateOld;
    StateInPipe stateNew;
    
    do {
      stateOld = _stateInRef.get();
      stateNew = stateOld.toActive();
    } while (! _stateInRef.compareAndSet(stateOld, stateNew));
    
    while (stateNew.isActive()) {
      readPipe();
      
      wakeOut();
      
      do {
        stateOld = _stateInRef.get();
        stateNew = stateOld.toIdle();
      } while (! _stateInRef.compareAndSet(stateOld, stateNew));
    }
  }
    
  public void readPipe()
  {
    PipeIn<T> inPipe = _inPipe;
    
    Outbox<T> outbox = null;
    
    try {
      _queue.deliver(this, outbox);
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
      
    /*
    T msg;
      
    while ((msg = _queue.poll()) != null) {
      inPipe.next(msg);
    }
    */
    
    if (_fail != null) {
      inPipe.fail(_fail);
    }
    else if (_isOk) {
      inPipe.ok();
      
      _stateInRef.set(StateInPipe.CLOSE);
    }
  }

  @Override
  public void deliver(T msg, Outbox<T> outbox) throws Exception
  {
    _inPipe.next(msg);
  }
  
  /**
   * Notify the reader of available data in the pipe. If the writer is asleep,
   * wake it.
   */
  private void wakeIn()
  {
    StateInPipe stateOld;
    StateInPipe stateNew;
    
    do {
      stateOld = _stateInRef.get();
      stateNew = stateOld.toWake();
    } while (! _stateInRef.compareAndSet(stateOld, stateNew));
    
    if (stateOld == StateInPipe.IDLE) {
      OutboxAmp outbox = OutboxAmp.current();
      Objects.requireNonNull(outbox);
      
      PipeWakeInMessage<T> msg = new PipeWakeInMessage<>(outbox, _inRef, this);
    
      outbox.offer(msg);
    }
  }
  
  /**
   * Notify the reader of available space in the pipe. If the writer is asleep,
   * wake it.
   */
  private void wakeOut()
  {
    OutFlow outFlow = _outFlow;
    
    if (outFlow == null) {
      return;
    }
    
    StateOutPipe stateOld;
    StateOutPipe stateNew;
    
    do {
      stateOld = _stateOutRef.get();
      
      if (! stateOld.isFull()) {
        return;
      }
      
      stateNew = stateOld.toWake();
    } while (! _stateOutRef.compareAndSet(stateOld, stateNew));

    OutboxAmp outbox = OutboxAmp.current();
    Objects.requireNonNull(outbox);
      
    PipeWakeOutMessage<T> msg = new PipeWakeOutMessage<>(outbox, _outRef, this, outFlow);
    
    outbox.offer(msg);
  }
  
  private class PipeInFlowImpl implements PipeIn.Flow
  {
    void init()
    {
    }

    @Override
    public void pause()
    {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void resume()
    {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void credit(int newCredits)
    {
      // TODO Auto-generated method stub
      
    }
  }
  
  enum StateInPipe {
    IDLE {
      @Override
      StateInPipe toWake() { return WAKE; }
    },
    
    ACTIVE {
      @Override
      StateInPipe toWake() { return WAKE; }
      
      @Override
      boolean isActive() { return true; }
      
      @Override
      StateInPipe toIdle() { return IDLE; }
    },
    
    WAKE {
      @Override
      StateInPipe toActive() { return ACTIVE; }
      
      @Override
      StateInPipe toIdle() { return ACTIVE; }
    },
    
    CLOSE {
    };
    
    StateInPipe toWake()
    {
      return this;
    }
    
    StateInPipe toActive()
    {
      return this;
    }
    
    StateInPipe toIdle()
    {
      return this;
    }
    
    boolean isActive()
    {
      return false;
    }
  }
  
  enum StateOutPipe {
    IDLE {
      @Override
      public StateOutPipe toFull() { return FULL; }
    },
    
    FULL {
      @Override
      public boolean isFull() { return true; }

      @Override
      public StateOutPipe toWake() { return IDLE; }
    },
    
    CLOSE {
    };

    public boolean isFull()
    {
      return false;
    }

    public StateOutPipe toWake()
    {
      return this;
    }

    public StateOutPipe toFull()
    {
      return this;
    }
  }
}
