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

import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.util.L10N;

/**
 * Value queue with atomic reference.
 */
public final class QueueRingSingleWriter<M>
  extends QueueDeliverBase<M>
{
  private static final L10N L = new L10N(QueueRingSingleWriter.class);
  
  // private final RingValueArray<M> _ring;
  private final ArrayRing<M> _ring;
  private final RingTailGetter<M> _tailGetter; 
  private final RingNonTailGetter<M> _nonTailGetter; 
  
  // private final RingUnsafeArray<T> _ring;
  private final int _capacity;
  
  private final CounterGroup _counterGroup;
  
  private final CounterActor _headRef;
  
  private final CounterActor _tail;
  
  private final RingBlocker _blocker;
  
  private volatile boolean _isWriteClosed;
  
  public QueueRingSingleWriter(int capacity)
  {
    this(capacity, CounterBuilderSingle.create());
  }
  
  public QueueRingSingleWriter(int capacity,
                           CounterBuilder counterBuilder)
  {
    this(capacity, 0, counterBuilder, new RingBlockerBasic());
  }
    
  public QueueRingSingleWriter(int capacity,
                                    int initialIndex,
                                    CounterBuilder counterBuilder,
                                    RingBlocker blocker)
  {
    if (Integer.bitCount(capacity) != 1 || capacity < 2) {
      throw new IllegalArgumentException(L.l("Invalid ring capacity {0}",
                                             Long.toHexString(capacity)));
    }
    
    if (blocker == null) {
      throw new NullPointerException(L.l("RingBlocker is required"));
    }

    _capacity = capacity;

    ArrayRing<M> ring = null;
    
    // ring = RingValueArrayUnsafe.create(capacity);
    
    if (ring == null) {
      ring = new ArrayRingImpl<M>(capacity);
    }
    
    _ring = ring; 
    
    _tailGetter = new RingTailGetter<M>(_ring);
    _nonTailGetter = new RingNonTailGetter<M>(_ring);
    
    _counterGroup = counterBuilder.build(initialIndex);
    
    _headRef = _counterGroup.getCounter(0);
    _tail = _counterGroup.getCounter(_counterGroup.getSize() - 1);
    
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
    return _headRef.get() == _tail.get();
  }
  
  @Override
  public final int size()
  {
    long head = _headRef.get();
    long tail = _tail.get();
    
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
    return _tail.get();
  }
  
  public final long getTailAlloc()
  {
    return _tail.get();
  }
  
  @Override
  public CounterGroup getCounterGroup()
  {
    return _counterGroup;
  }
  
  @Override
  public WorkerDeliverLifecycle getOfferTask()
  {
    return _blocker;
  }
  
  public final M getValue(long ptr)
  {
    return get(ptr);
  }
  
  private final M get(long ptr)
  {
    return _ring.get(ptr);
  }
  
  private final M getAndClear(long ptr)
  {
    return _ring.takeAndClear(ptr);
  }
  
  private final boolean isSet(long ptr)
  {
    return _ring.get(ptr) != null;
  }
  
  @Override
  public final boolean offer(final M value, 
                             final long timeout, 
                             final TimeUnit unit)
  {
    Objects.requireNonNull(value);
    
    // completePoll();
    
    final CounterActor headRef = _headRef;
    final CounterActor tailRef = _tail;
    final int capacity = _capacity;
    
    while (true) {
      // final AtomicReferenceArray<T> ring = _ring;
      final long tail = tailRef.get();
      final long head = headRef.get();
      final long nextHead = head + 1;

      if (nextHead - tail < capacity) {
        headRef.setLazy(nextHead);
        _ring.setLazy(head, value);

        return true;
      }
      else {
        long offerSequence = _blocker.nextOfferSequence();
        
        if (capacity <= headRef.get() + 1 - tailRef.get()
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
    long tailAlloc = _tail.get();
    
    if (tailAlloc < head) {
      return get(tailAlloc);
    }
    
    return null;
  }
  
  @Override
  public final M poll(long timeout, TimeUnit unit)
  {
    // final AtomicLong tailAllocRef = _tailAlloc;
    final CounterActor headRef = _headRef;
    final CounterActor tailRef = _tail;
    
    final ArrayRing<M> ring = _ring;

    final RingBlocker blocker = _blocker;
    
    while (true) {
      long tail = tailRef.get();
      final long head = headRef.get();
      
      M value;
      
      if (tail == head) {
        blocker.offerWake();
        
        if (timeout <= 0) {
          return null;
        }
        
        long pollSequence = blocker.nextPollSequence();
        
        if (headRef.get() == tailRef.get()
            && ! blocker.pollWait(pollSequence, timeout, unit)) {
          // repeat test after pollSequence allocation because of wake
          // timing
          return null;
        }
      }
      else if ((value = ring.pollAndClear(tail)) != null) {
        if (tailRef.compareAndSet(tail, tail + 1)) {
          blocker.offerWake();
          
          return value;
        }
        else {
          ring.set(tail, value);
        }
      }
    }
  }
  
  @Override
  public void deliver(final Deliver<M> deliver,
                      final Outbox<M> outbox)
    throws Exception
  {
    final CounterActor headCounter = _headRef;
    final CounterActor tailCounter = _tail;
    
    long initialTail = tailCounter.get();
    long tail = initialTail;
    long head = headCounter.get();
    
    try {
      do {
        tail = deliver(head, tail, deliver, outbox);
      
        head = headCounter.get();
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
                       final Outbox<M> outbox)
    throws Exception
  {
    final int tailChunk = 32;
    final ArrayRing<M> ring = _ring;
    final CounterActor tailCounter = _tail;
    
    long lastTail = tail;

    try {
      while (tail < head) {
        long tailChunkEnd = Math.min(head, tail + tailChunk);
    
        while (tail < tailChunkEnd) {
          M item = ring.takeAndClear(tail);
      
          if (item != null) {
            tail++;
    
            deliver.deliver(item, outbox);
          }
        }
        
        tailCounter.set(tail);
        lastTail = tail;
      }
    } finally {
      if (tail != lastTail) {
        tailCounter.set(tail);
      }
    }

    return lastTail;
  }
  
  @Override
  public void deliver(final Deliver<M> processor,
                      final Outbox<M> outbox,
                      final int headIndex,
                      final int tailIndex,
                      final WorkerDeliver nextWorker,
                      boolean isTail)
    throws Exception
  {
    final CounterGroup counterGroup = getCounterGroup();
    final CounterActor headCounter = counterGroup.getCounter(headIndex);
    final CounterActor tailCounter = counterGroup.getCounter(tailIndex);
    
    final RingGetter<M> ringGetter = isTail ? _tailGetter : _nonTailGetter;
    
    int tailChunk = 2;
    long initialTail = tailCounter.get();
    long tail = initialTail;
    long head = headCounter.get();
    
    try {
      do {
        long tailChunkEnd = Math.min(head, tail + tailChunk);

        while (tail < tailChunkEnd) {
          M item = ringGetter.get(tail);
          
          if (item != null) {
            tail++;
        
            processor.deliver(item, outbox);
          }
        }
      
        tailCounter.set(tail);
        initialTail = tail;
        tailChunk = Math.min(256, 2 * tailChunk);
        nextWorker.wake();
        
        head = headCounter.get();
      } while (head != tail);
    } finally {
      if (tail != initialTail) {
        tailCounter.set(tail);
      }
      
      nextWorker.wake();
    }
  }

  @Override
  public void deliverMulti(Deliver<M> actor,
                           Outbox<M> outbox,
                           int headCounter,
                           int tailCounter,
                           WorkerDeliver tailWorker)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
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
  
  abstract private static class RingGetter<T> {
    abstract public T get(long index);
  }
  
  private final static class RingTailGetter<T> extends RingGetter<T> {
    private final ArrayRing<T> _ring;
    
    RingTailGetter(ArrayRing<T> ring)
    {
      _ring = ring;
    }
    
    @Override
    public final T get(long index)
    {
      return _ring.takeAndClear(index);
    }
  }
  
  private final static class RingNonTailGetter<T> extends RingGetter<T> {
    private final ArrayRing<T> _ring;
    
    RingNonTailGetter(ArrayRing<T> ring)
    {
      _ring = ring;
    }
    
    @Override
    public final T get(long index)
    {
      return _ring.get(index);
    }
  }

  /* (non-Javadoc)
   * @see com.caucho.env.actor.ActorQueue#deliverMultiTail(com.caucho.env.actor.Actor, int, int, com.caucho.env.thread.TaskWorker)
   */
  @Override
  public void deliverMultiTail(Deliver<M> actor,
                               Outbox<M> outbox,
                               int headIndex, 
                               int tailIndex,
                               WorkerDeliver tailWorker) throws Exception
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
}
