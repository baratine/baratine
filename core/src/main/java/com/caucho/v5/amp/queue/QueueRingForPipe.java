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

package com.caucho.v5.amp.queue;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.caucho.v5.amp.deliver.Deliver;
import com.caucho.v5.amp.deliver.Outbox;
import com.caucho.v5.amp.deliver.WorkerDeliver;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.util.L10N;

/**
 * Value queue with atomic reference.
 */
public final class QueueRingForPipe<M>
  extends QueueRingBase<M>
{
  private static final L10N L = new L10N(QueueRingForPipe.class);
  
  // private final RingValueArray<M> _ring;
  private final ArrayRing<M> _ring;
  
  // private final RingUnsafeArray<T> _ring;
  private final int _capacity;
  
  //private final AtomicLong _headRef;
  //private final AtomicLong _tail;
  
  private final AtomicLong _headRef;
  private final AtomicLong _tailRef;
  
  private final RingBlocker _blocker;
  
  private volatile boolean _isWriteClosed;
  
  public QueueRingForPipe(int capacity)
  {
    if (Integer.bitCount(capacity) != 1 || capacity < 2) {
      throw new IllegalArgumentException(L.l("Invalid ring capacity {0}",
                                             Long.toHexString(capacity)));
    }
    
    RingBlockerBasic blocker = new RingBlockerBasic();

    _capacity = capacity;
    
    _ring = new ArrayRingPlain<M>(capacity);
    
    //_headRef = new AtomicLong();
    _headRef = new AtomicLong();
    _tailRef = new AtomicLong();
    
    //_headRef = counterGroup[0];
    //_tail = counterGroup[counterGroup.length - 1];
    
    _blocker = blocker;
  }

  public int getCapacity()
  {
    return _capacity;
  }
  
  /*
  @Override
  public int getOfferReserve()
  {
    return _capacity / 2;
  }
  */
  
  @Override
  public final boolean isEmpty()
  {
    return _headRef.get() == _tailRef.get();
  }
  
  @Override
  public final int size()
  {
    long head = _headRef.get();
    long tail = _tailRef.get();
    
    return (int) (head - tail);
  }

  @Override
  public int remainingCapacity()
  {
    return getCapacity() - size() - 1;
  }
  
  public final long head()
  {
    return _headRef.get();
  }
  
  public final long getHeadAlloc()
  {
    return _headRef.get();
  }
  
  public final long getTail()
  {
    return _tailRef.get();
  }
  
  public final long getTailAlloc()
  {
    return _tailRef.get();
  }
  
  @Override
  public int counterGroupSize()
  {
    return 2;
  }
  
  /*
  @Override
  public WorkerDeliverLifecycle worker()
  {
    return _blocker;
  }
  */
  
  public final M getValue(long ptr)
  {
    return get(ptr);
  }
  
  private final M get(long ptr)
  {
    return _ring.get(ptr);
  }

  /*
  private final M getAndClear(long ptr)
  {
    return _ring.takeAndClear(ptr);
  }
  
  private final boolean isSet(long ptr)
  {
    return _ring.get(ptr) != null;
  }
  */
  
  @Override
  public final boolean offer(final M value, 
                             final long timeout, 
                             final TimeUnit unit)
  {
    Objects.requireNonNull(value);
    
    // completePoll();
    
    //final AtomicLong headRef = _headRef;
    //final AtomicLong tailRef = _tail;
    //final int capacity = _capacity;
    
    while (true) {
      // final AtomicReferenceArray<T> ring = _ring;
      final long tail = _tailRef.get();
      final long head = _headRef.get();
      final long nextHead = head + 1;

      if (nextHead - tail < _capacity) {
        _ring.setLazy(head, value);
        _headRef.lazySet(nextHead);

        return true;
      }
      else {
        long offerSequence = _blocker.nextOfferSequence();
        
        if (_capacity <= head + 1 - tail
            && ! _blocker.offerWait(offerSequence, timeout, unit)) {
          return false;
        }
      }
    }
  }
  
  @Override
  public final M peek()
  {
    long head = _headRef.get();
    long tailAlloc = _tailRef.get();
    
    if (tailAlloc < head) {
      return get(tailAlloc);
    }
    
    return null;
  }
  
  @Override
  public final M poll(long timeout, TimeUnit unit)
  {
    // final AtomicLong tailAllocRef = _tailAlloc;
    //final AtomicLong headRef = _headRef;
    //final AtomicLong tailRef = _tail;
    
    final ArrayRing<M> ring = _ring;

    final RingBlocker blocker = _blocker;
    
    while (true) {
      long tail = _tailRef.get();
      final long head = _headRef.get();
      
      M value;
      
      if (tail == head) {
        blocker.offerWake();
        
        if (timeout <= 0) {
          return null;
        }
        
        long pollSequence = blocker.nextPollSequence();
        
        if (_headRef.get() == _tailRef.get()
            && ! blocker.pollWait(pollSequence, timeout, unit)) {
          // repeat test after pollSequence allocation because of wake
          // timing
          return null;
        }
      }
      else if ((value = ring.pollAndClear(tail)) != null) {
        _tailRef.set(tail + 1);
        blocker.offerWake();
          
        return value;
      }
    }
  }
  
  @Override
  public void deliver(final Deliver<M> deliver,
                      final Outbox outbox)
    throws Exception
  {
    long initialTail = _tailRef.get();
    long tail = initialTail;
    long head = _headRef.get();
    
    try {
      do {
        tail = deliver(head, tail, deliver, outbox);
      
        head = _headRef.get();
      } while (tail < head);
    } finally {
      _blocker.offerWake();
    }
  }
  
  public void wake()
  {
  }

  private long deliver(long head, 
                       long tail,
                       final Deliver<M> deliver,
                       final Outbox outbox)
    throws Exception
  {
    final int tailChunk = 32;
    final ArrayRing<M> ring = _ring;
    
    long lastTail = tail;

    try {
      while (tail < head) {
        //long tailChunkStart = tail;
        long tailChunkEnd = Math.min(head, tail + tailChunk);
    
        while (tail < tailChunkEnd) {
          M item = ring.takeAndClear(tail);
      
          if (item != null) {
            tail++;
    
            deliver.deliver(item, outbox);
          }
        }
        
        //ring.clear(tailChunkStart, tailChunkEnd);
        _tailRef.lazySet(tail);
        lastTail = tail;
      }
    } finally {
      if (tail != lastTail) {
        _tailRef.lazySet(tail);
      }
    }

    return lastTail;
  }
  
  @Override
  public void deliver(final Deliver<M> processor,
                      final Outbox outbox,
                      final int headIndex,
                      final int tailIndex,
                      final WorkerDeliver<?> nextWorker,
                      boolean isTail)
    throws Exception
  {
    throw new UnsupportedOperationException();
  }

  /*
  @Override
  public void deliverMulti(DeliverOutbox<M> actor,
                           Outbox outbox,
                           int headCounter,
                           int tailCounter,
                           WorkerOutbox<M> tailWorker)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */
  
  public final boolean isWriteClosed()
  {
    return _isWriteClosed;
  }
  
  public final void closeWrite()
  {
    _isWriteClosed = true;
    
    _blocker.offerWake();
    _blocker.pollWake();
  }
  
  public final void shutdown(ShutdownModeAmp mode)
  {
    closeWrite();
    
    _blocker.shutdown(mode);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getCapacity() + "]";
  }
  
  private static final class PtrRef
  {
    private long _value;
    
    public final long get()
    {
      return _value;
    }
    
    public final void set(long value)
    {
      _value = value;
    }
    
    public final void lazySet(long value)
    {
      _value = value;
    }
  }
}
