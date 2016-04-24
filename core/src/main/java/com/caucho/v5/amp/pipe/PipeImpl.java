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
import com.caucho.v5.amp.ServicesAmp;
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
public class PipeImpl<T> implements Pipe<T>, Deliver<T>
{
  private static final L10N L = new L10N(PipeImpl.class);
  private static final Logger log = Logger.getLogger(PipeImpl.class.getName());
  
  private static final long OFFER_TIMEOUT_DEFAULT = 10000L;
  private static final int CAPACITY_DEFAULT = 256;
  
  private final ServicesAmp _services;
  
  private Pipe<T> _inPipe;
  private QueueRingForPipe<T> _queue;
  
  private volatile boolean _isOk;
  private volatile Throwable _fail;
  
  private AtomicReference<StateInPipe> _stateInRef
    = new AtomicReference<>(StateInPipe.IDLE);
  
  private AtomicReference<StateOutPipe> _stateOutRef
    = new AtomicReference<>(StateOutPipe.IDLE);
  
  private ServiceRefAmp _inRef;
  
  private FlowInImpl _inFlow = new FlowInImpl();

  private int _prefetch;
  private volatile long _creditsIn;
  
  private final Credits _creditsOut = new CreditsOut();

  private ServiceRefAmp _outRef;

  private long _offerTimeout = OFFER_TIMEOUT_DEFAULT;
  private OnAvailable _outFlow;
  private FlowOutBlock<T> _outBlock;
  
  public PipeImpl(PipeBuilder<T> builder)
  {
    _inRef = builder.inRef();
    Objects.requireNonNull(_inRef);
    
    _inPipe = builder.inPipe();
    Objects.requireNonNull(_inPipe);
    
    _outRef = builder.outRef();
    
    _services = builder.services();
    
    int prefetch = builder.prefetch();
    long credits = builder.credits();
    int capacity = builder.capacity();

    if (credits >= 0) {
      // XXX: illegal argument exception for too-long credits
      if (capacity <= 0) {
        if (credits > 0) {
          capacity = (int) Math.max(32, 2 * Long.highestOneBit(credits));
        }
        else {
          capacity = CAPACITY_DEFAULT;
        }
      }
      
      _creditsIn = credits;
      prefetch = 0;
    }
    else {
      if (prefetch <= 0) {
        if (capacity <= 0) {
          capacity = CAPACITY_DEFAULT;
        }
        
        if (capacity >= 16) {
          prefetch = capacity - 8;
        }
        else {
          prefetch = capacity - 1;
        }
      }
      else {
        if (capacity <= 0) {
          capacity = 2 * Integer.highestOneBit(prefetch);
        }
      }
    }
    
    _prefetch = prefetch;
    
    _inFlow.init();
    
    long offerTimeout = 10000L;
    _outBlock = new FlowOutBlock<>(offerTimeout);
    _queue = new QueueRingForPipe<>(capacity);
    
    updatePrefetchCredits();
    
    _inPipe.credits(_inFlow);
  }
  
  @SuppressWarnings("unchecked")
  public void flow(OnAvailable flow)
  {
    Objects.requireNonNull(flow);
    
    _outFlow = _outRef.pin(flow).as(OnAvailable.class);
    _offerTimeout = 0;
  }

  @Override
  public void next(T value)
  {
    Objects.requireNonNull(value);
    
    if (_stateInRef.get() == StateInPipe.CLOSE) {
      return;
    }
    
    validateCredits();
    
    if (! _queue.offer(value, 0, TimeUnit.MILLISECONDS)) {
      throw new PipeExceptionFull(L.l("full pipe for pipe.next() size={0}",
                                      _queue.size()));
    }
      
    wakeIn();
  }
  
  private void validateCredits()
  {
    if (_creditsIn <= sent()) {
      FlowOutBlock<T> outBlock = _outBlock;

      if (outBlock != null) {
        long seq = outBlock.blockSequence();
        
        if (available() <= 0) {
          outBlock.block(seq);
        }
      }
      
      if (_creditsIn <= sent()) {
        throw new IllegalStateException(L.l("Pipe.next called with no available credits"));
      }
    }
  }

  @Override
  public void close()
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

  //@Override
  private void offerTimeout(long timeout, TimeUnit unit)
  {
    if (sent() > 0) {
      throw new IllegalStateException();
    }

    if (_outFlow != null) {
      throw new IllegalStateException();
    }

    _outBlock = new FlowOutBlock<>(unit.toMillis(timeout));
  }
  
  //@Override
  public void onAvailable(OnAvailable flowOut)
  {
    Objects.requireNonNull(flowOut);
    
    if (sent() > 0) {
      throw new IllegalStateException();
    }

    if (_outFlow != null) {
      throw new IllegalStateException();
    }
    
    if (isClosed()) {
      throw new IllegalStateException();
    }
    
    _outFlow = flowOut;
    _outBlock = null;
    //outFull();

    if (credits().available() > 0) {
      flowOut.available();
    }
  }
  
  /*
  private void ready(Ready ready)
  {
    Objects.requireNonNull(ready);
    
    if (sent() > 0) {
      throw new IllegalStateException();
    }

    if (_outFlow != null) {
      throw new IllegalStateException();
    }
    
    if (isClosed()) {
      throw new IllegalStateException();
    }
    
    //_outFlow = flowOut;
    _outBlock = null;

    outFull();
  }
  */

  /*
  @Override
  public FlowIn flow()
  {
    return _inFlow;
  }
  */
  
  private int available()
  {
    long sent = sent();
    
    int available = Math.max(0, (int) (_creditsIn - sent));
    
    available = Math.min(available, _queue.remainingCapacity());
    
    if (available <= 0) {
      outFull();
    }

    return available;
  }
  
  private long sent()
  {
    return _queue.head();
  }
  
  @Override
  public Credits credits()
  {
    return _creditsOut;
  }
  
  private final long creditSequence()
  {
    return _creditsIn;
  }
  
  void creditSequence(long credits)
  {
    _creditsIn = Math.max(credits, _creditsIn);

    wakeOut();
  }
  
  private void outFull()
  {
    OnAvailable outFlow = _outFlow;
    
    if (outFlow == null) {
      return;
    }

    StateOutPipe stateOld;
    StateOutPipe stateNew;
    
    do {
      stateOld = _stateOutRef.get();
      stateNew = stateOld.toFull();
    } while (! _stateOutRef.compareAndSet(stateOld, stateNew));

    long sent = _queue.head();
    
    if (sent < _creditsIn) {
      do {
        stateOld = _stateOutRef.get();
        stateNew = stateOld.toWake();
      } while (! _stateOutRef.compareAndSet(stateOld, stateNew));
      
      outFlow.available();
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
    
    if (_stateInRef.get().isClosed()) {
      StateOutPipe outStateOld = _stateOutRef.getAndSet(StateOutPipe.CLOSE);
      
      if (! outStateOld.isClosed()) {
        _outFlow.cancel();
      }
    }
  }
    
  public void readPipe()
  {
    Pipe<T> inPipe = _inPipe;
    
    Outbox outbox = null;
    
    try {
      _queue.deliver(this, outbox);
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }

    updatePrefetchCredits();
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
      inPipe.close();
      
      _stateInRef.set(StateInPipe.CLOSE);
    }
  }

  @Override
  public void deliver(T msg, Outbox outbox) throws Exception
  {
    _inPipe.next(msg);
    
    updatePrefetchCredits();
  }
  
  private void updatePrefetchCredits()
  {
    int prefetch = _prefetch;
    
    if (prefetch > 0) {
      _creditsIn = _queue.getTail() + _prefetch;
    }
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
      
      if (stateOld.isActive()) {
        return;
      }
      
      stateNew = stateOld.toWake();
    } while (! _stateInRef.compareAndSet(stateOld, stateNew));
    
    if (stateOld == StateInPipe.IDLE) {
      try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_services)) {
        Objects.requireNonNull(outbox);
      
        PipeWakeInMessage<T> msg = new PipeWakeInMessage<>(outbox, _inRef, this);
    
        outbox.offer(msg);
      }
    }
  }
  
  private void cancel()
  {
    _stateInRef.set(_stateInRef.get().toClose());
  }

  /**
   * Notify the reader of available space in the pipe. If the writer is asleep,
   * wake it.
   */
  void wakeOut()
  {
    OnAvailable outFlow = _outFlow;
    
    if (outFlow == null) {
      return;
    }
    
    if (_creditsIn <= _queue.head()) {
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
      
    try (OutboxAmp outbox = OutboxAmp.currentOrCreate(_outRef.manager())) {
      Objects.requireNonNull(outbox);
    
      PipeWakeOutMessage<T> msg = new PipeWakeOutMessage<>(outbox, _outRef, this, outFlow);
    
      outbox.offer(msg);
    }
  }
  
  private class FlowInImpl implements Credits
  {
    void init()
    {
    }
    
    @Override
    public long get()
    {
      return PipeImpl.this.creditSequence();
    }

    @Override
    public void set(long credits)
    {
      if (credits < _creditsIn) {
        throw new IllegalArgumentException(String.valueOf(credits));
      }
      
      PipeImpl.this.creditSequence(credits);
    }

    @Override
    public int available()
    {
      return PipeImpl.this.available();
    }

    @Override
    public void onAvailable(OnAvailable flow)
    {
      PipeImpl.this.onAvailable(flow);
    }

    @Override
    public void cancel()
    {
      PipeImpl.this.cancel();
    }
  }
  
  private static class FlowOutBlock<T> implements OnAvailable
  {
    private long _timeout;
    
    private volatile Thread _thread;
    private volatile long _blockSequence;
    private volatile long _readySequence;
    
    FlowOutBlock(long timeout)
    {
      _timeout = timeout;
    }
    
    public long blockSequence()
    {
      return ++_blockSequence;
    }
    
    public void block(long blockSequence)
    {
      long expire = System.currentTimeMillis() + _timeout;
      
      Thread thread = Thread.currentThread();
      _thread = thread;
      
      try {
        while (_readySequence < blockSequence
               && (System.currentTimeMillis() < expire)) {
          LockSupport.parkUntil(expire);
        }
      } catch (Exception e) {
        log.log(Level.FINER, e.toString(), e);
      } finally {
        _thread = null;
      }
    }
    
    @Override
    public void available()
    {
      _readySequence = _blockSequence;
      Thread thread = _thread;
      
      if (thread != null) {
        LockSupport.unpark(thread);
      }
    }
  }
  
  private class CreditsOut implements Credits
  {
    @Override
    public long get()
    {
      return creditSequence();
    }

    @Override
    public void set(long credits)
    {
      throw new UnsupportedOperationException(getClass().getName());
      /*
      if (credits < _creditsIn) {
        throw new IllegalArgumentException(String.valueOf(credits));
      }
      
      creditSequence(credits);
      */
    }

    @Override
    public int available()
    {
      return PipeImpl.this.available();
    }

    @Override
    public void onAvailable(OnAvailable ready)
    {
      PipeImpl.this.onAvailable(ready);
    }

    @Override
    public void offerTimeout(long timeout, TimeUnit unit)
    {
      PipeImpl.this.offerTimeout(timeout, unit);
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
      @Override
      boolean isClosed() { return false; }
    };
    
    StateInPipe toWake()
    {
      return this;
    }
    
    StateInPipe toActive()
    {
      return this;
    }
    
    StateInPipe toClose()
    {
      return CLOSE;
    }
    
    StateInPipe toIdle()
    {
      return this;
    }
    
    boolean isActive()
    {
      return false;
    }
    
    boolean isClosed()
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
      @Override
      public boolean isClosed() { return true; }
    };

    public boolean isFull()
    {
      return false;
    }

    public boolean isClosed()
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
