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
public final class QueueRingFixed<M >
  extends QueueRingBase<M>
{
  private static final L10N L = new L10N(QueueRingFixed.class);

  private final ArrayRing<M> _ring;
  private final RingTailGetter<M> _tailGetter;
  private final RingNonTailGetter<M> _nonTailGetter;

  private final int _capacity;

  private final AtomicLong _head;
  private final AtomicLong _tail;

  private final RingBlocker _blocker;
  private final AtomicLong []_counterGroup;

  private volatile boolean _isWriteClosed;

  public QueueRingFixed(int capacity)
  {
    this(capacity, CounterBuilder.create(1));
  }

  public QueueRingFixed(int capacity, RingBlocker blocker)
  {
    this(capacity, CounterBuilder.create(1), 0, blocker);
  }

  public QueueRingFixed(int capacity,
                   CounterBuilder counterBuilder)
  {
    this(capacity, counterBuilder, 0, new RingBlockerBasic());
  }

  public QueueRingFixed(int capacity,
                   CounterBuilder counterBuilder,
                   long initialIndex,
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
      ring = new ArrayRingAtomic<M>(capacity);
    }

    _ring = ring;

    _tailGetter = new RingTailGetter<M>(_ring);
    _nonTailGetter = new RingNonTailGetter<M>(_ring);

    _counterGroup = counterBuilder.build(initialIndex);
    _head = _counterGroup[0];
    _tail = _counterGroup[_counterGroup.length - 1];

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
    return _head.get() == _tail.get();
  }

  @Override
  public final int size()
  {
    long head = _head.get();
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
    return _head.get();
  }

  public final long getHeadAlloc()
  {
    return _head.get();
  }

  public final long getTail()
  {
    return _tail.get();
  }

  public final long getTailAlloc()
  {
    return _tail.get();
  }

  /*
  @Override
  public WorkerOutbox<M> worker()
  {
    return _blocker;
  }
  */
  
  @Override
  public void wake()
  {
    _blocker.offerWake();
  }

  public final M getValue(long ptr)
  {
    return get(ptr);
  }

  private final M get(long ptr)
  {
    return _ring.get(ptr);
  }

  @Override
  public final boolean offer(final M value,
                             final long timeout,
                             final TimeUnit unit)
  {
    Objects.requireNonNull(value);

    final AtomicLong headRef = _head;
    final AtomicLong tailRef = _tail;
    final int capacity = _capacity;

    while (true) {
      final long tail = tailRef.get();
      final long head = headRef.get();
      final long nextHead = head + 1;

      if (capacity <= nextHead - tail) {
        long sequence = _blocker.nextOfferSequence();

        if (capacity <= headRef.get() + 1 - tailRef.get()
            && ! _blocker.offerWait(sequence, timeout, unit)) {
          // retest the capacity after the sequence is allocated because of
          // wake timing
          
          return false;
        }
      }
      else if (headRef.compareAndSet(head, nextHead)) {
        // _ring.setLazy(head, value);
        _ring.set(head, value);

        return true;
      }
    }
  }

  @Override
  public final M poll(long timeout, TimeUnit unit)
  {
    // final AtomicLong tailAllocRef = _tailAlloc;
    final AtomicLong headRef = _head;
    final AtomicLong tailRef = _tail;

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

        if (tailRef.get() == headRef.get()
            && ! blocker.pollWait(pollSequence, timeout, unit)) {
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
  public final M peek()
  {
    long head = _head.get();
    long tailAlloc = _tail.get();

    if (tailAlloc < head) {
      return get(tailAlloc);
    }

    return null;
  }

  @Override
  public void deliver(final Deliver<M> deliver,
                      final Outbox outbox)
    throws Exception
  {
    final int tailChunk = 64;
    final ArrayRing<M> ring = _ring;
    final AtomicLong headRef = _head;
    final AtomicLong tailRef = _tail;

    long head = headRef.get();
    long tail = tailRef.get();
    long lastTail = tail;
    
    try {
      while (tail < head) {
        long tailChunkEnd = Math.min(head, tail + tailChunk);

        while (tail < tailChunkEnd) {
          M item = ring.takeAndClear(tail);

          tail++;

          deliver.deliver(item, outbox);
        }

        tailRef.set(tail);
        lastTail = tail;

        // XXX: verify
        _blocker.offerWake();

        head = headRef.get();
      }
    } finally {
      if (tail != lastTail) {
        tailRef.set(tail);
      }

      _blocker.offerWake();
    }
  }

  @Override
  public void deliver(final Deliver<M> deliver,
                      final Outbox outbox,
                      final int headIndex,
                      final int tailIndex,
                      final WorkerDeliver<?> nextWorker,
                      boolean isTail)
    throws Exception
  {
    final AtomicLong []counterGroup = _counterGroup;

    final AtomicLong headCounter = counterGroup[headIndex];
    final AtomicLong tailCounter = counterGroup[tailIndex];

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

          tail++;

          deliver.deliver(item, outbox);
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

  /*
  @Override
  public final void deliverMulti(DeliverOutbox<M> deliver,
                                 Outbox outbox,
                                 int headIndex,
                                 int tailIndex,
                                 WorkerOutbox<?> tailWorker)
    throws Exception
  {
    final CounterRingGroup counterGroup = counterGroup();

    final CounterRing headRef = counterGroup.counter(headIndex);
    final CounterMultiTail tailRef = counterGroup.getMultiCounter(tailIndex);

    while (true) {
      final long head = headRef.get();

      long tail = tailRef.allocate(head);

      if (tail < 0) {
        return;
      }

      M message = _ring.get(tail);

      try {
        deliver.deliver(message, outbox);
      } finally {
        tailRef.update(tail, tailWorker);
      }
    }
  }
  /*

/*
  @Override
  public final void deliverMultiTail(DeliverOutbox<M> actor,
                                     Outbox outbox,
                                     int headIndex,
                                     int tailIndex,
                                     WorkerOutbox<?> tailWorker)
    throws Exception
  {
    final CounterRingGroup counterGroup = counterGroup();

    final CounterRing headRef = counterGroup.counter(headIndex);
    final CounterRing tailRef = counterGroup.counter(tailIndex);

    while (true) {
      long tail = tailRef.get();
      final long head = headRef.get();

      M message;

      if (tail == head) {
        return;
      }
      else if ((message = _ring.pollAndClear(tail)) == null) {
        continue;
      }
      else if (! tailRef.compareAndSet(tail, tail + 1)) {
        _ring.set(tail, message);
        continue;
      }

      actor.deliver(message, outbox);
    }
  }
  */

  @Override
  public final int counterGroupSize()
  {
    return _counterGroup.length;
  }

  public final boolean isWriteClosed()
  {
    return _isWriteClosed;
  }
  
  public final void pollWake()
  {
    _blocker.pollWake();
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

  private final static class RingTailGetter<T> extends RingGetter<T> 
  {
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

  private final static class RingNonTailGetter<T> extends RingGetter<T>
  {
    private final ArrayRing<T> _ring;

    RingNonTailGetter(ArrayRing<T> ring)
    {
      _ring = ring;
    }

    @Override
    public final T get(long index)
    {
      T value;

      while ((value = _ring.get(index)) == null) {
      }

      return value;
    }
  }
}
