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

import java.util.concurrent.TimeUnit;

import com.caucho.v5.amp.outbox.DeliverOutbox;
import com.caucho.v5.amp.outbox.MessageOutbox;
import com.caucho.v5.amp.outbox.Outbox;
import com.caucho.v5.amp.outbox.QueueOutbox;
import com.caucho.v5.amp.outbox.QueueOutboxBase;
import com.caucho.v5.amp.outbox.WorkerOutbox;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.thread.ThreadPool;
import com.caucho.v5.util.L10N;

/**
 * Value queue with atomic reference.
 */
public final class QueueRingResizing<M>
  extends QueueOutboxBase<M>
{
  private static final L10N L = new L10N(QueueRingResizing.class);
  
  private final int _minCapacity;
  private final int _maxCapacity;
  
  private int _capacity;
  
  private final CounterBuilder _counterBuilder;
  private final RingBlocker _baseBlocker;

  private volatile QueueRing<M> _readQueue;
  
  private final Object _resizeLock = new Object();
  private volatile QueueRing<M> _writeQueue;
  
  public QueueRingResizing(int minCapacity, int maxCapacity)
  {
    this(minCapacity, 
         maxCapacity,
         CounterBuilderSingle.create());
  }
  
  public QueueRingResizing(int minCapacity, 
                           int maxCapacity,
                           CounterBuilder counterBuilder)
  {
    this(minCapacity, 
         maxCapacity,
         counterBuilder,
         new RingBlockerBasic());
  }
    
  public QueueRingResizing(int minCapacity,
                                   int maxCapacity,
                                   CounterBuilder counterBuilder,
                                   RingBlocker blocker)
  {
    if (Integer.bitCount(minCapacity) != 1 || minCapacity < 2) {
      throw new IllegalArgumentException(L.l("Invalid min capacity {0}",
                                             Long.toHexString(minCapacity)));
    }
    
    if (Integer.bitCount(maxCapacity) != 1 || maxCapacity < 2) {
      throw new IllegalArgumentException(L.l("Invalid max capacity {0}",
                                             Long.toHexString(maxCapacity)));
    }
    
    if (maxCapacity < minCapacity) {
      throw new IllegalArgumentException(L.l("Invalid min {0} and max {1} capacity",
                                             minCapacity,
                                             maxCapacity));
    }

    if (blocker == null) {
      throw new NullPointerException(L.l("RingBlocker is required"));
    }

    _minCapacity = minCapacity;
    _maxCapacity = maxCapacity;
    
    _capacity = _minCapacity;
    
    _baseBlocker = blocker;
    
    _counterBuilder = counterBuilder;

    _writeQueue = createQueue(_capacity, 0);
    _readQueue = _writeQueue;
  }
  
  public static <T extends MessageOutbox<T>> 
  QueueOutbox<T> create(int min, int max)
  {
    return create(min, max,
                  CounterBuilderSingle.create(),
                  new RingBlockerBasic());
  }
    
  public static <T extends MessageOutbox<T>> 
  QueueOutbox<T> create(int min, 
                        int max,
                        CounterBuilder counterBuilder,
                        RingBlocker blocker)
  {
    if (min == max) {
      return new QueueRing<T>(max, counterBuilder, 0, blocker);
    }
    else {
      return new QueueRingResizing<T>(min, max, counterBuilder, blocker);
    }
  }
  
  /*
  @Override
  public int getOfferReserve()
  {
    return _readQueue.getOfferReserve();
  }
  */
  
  /*
  @Override
  public WorkerDeliverLifecycle worker()
  {
    return _baseBlocker;
  }
  */
  
  @Override
  public void wake()
  {
    _baseBlocker.wake();
  }

  @Override
  public boolean isEmpty()
  {
    return _writeQueue.isEmpty();
  }
  
  @Override
  public int size()
  {
    // bfs/1017 - worker start
    //return _writeQueue.size();
    
    /*
    QueueRing<T> writeQueue = _writeQueue;
    QueueRing<T> readQueue = _readQueue;
    
    if (writeQueue == readQueue) {
      return readQueue.size();
    }
    else {
      return writeQueue.size() + readQueue.size();
    }*/
    
    return _readQueue.size();
  }
  
  public long head()
  {
    return _readQueue.head();
  }
  
  @Override
  public final boolean offer(M value, long timeout, TimeUnit unit)
  {
    QueueRing<M> writeQueue;
    do {
      writeQueue = _writeQueue;
      
      if (writeQueue.offer(value, timeout, unit)) {
        return true;
      }
    } while (writeQueue.isWriteClosed());
    
    return false;
  }
  
  @Override
  public final M peek()
  {
    QueueRing<M> queue = _readQueue;
    
    M value = queue.peek();
    
    if (value != null) {
      return value;
    }
    
    if (pollResize(queue)) {
      value = _readQueue.peek();
    }
    
    return value;
  }
 
  @Override
  public final M poll(long timeout, TimeUnit unit)
  {
    QueueRing<M> readQueue = _readQueue;

    M value = readQueue.poll(timeout, unit);
    
    while (value == null && pollResize(readQueue)) {
      readQueue = _readQueue;
      
      value = readQueue.poll(timeout, unit);
    }
    
    return value;
  }
  
  @Override
  public void deliver(DeliverOutbox<M> deliver, Outbox outbox)
    throws Exception
  {
    QueueRing<M> readQueue;
    
    do {
      readQueue = _readQueue;
      
      readQueue.deliver(deliver, outbox);
    } while (pollResize(readQueue));
  }
  
  @Override
  public void deliver(DeliverOutbox<M> processor,
                      Outbox outbox,
                      int headIndex,
                      int tailIndex,
                      WorkerOutbox<?> nextWorker,
                      boolean isTail)
    throws Exception
  {
    QueueRing<M> readQueue;
    
    // the loop is required on resize because the wake assumes the deliver
    // will consume at least one item if it was available at the time of the
    // wake.
      
    do {
      readQueue = _readQueue;
      
      readQueue.deliver(processor,
                        outbox,
                        headIndex, 
                        tailIndex, 
                        nextWorker, 
                        isTail);
    } while (isTail && pollResize(readQueue));
  }

  /*
  @Override
  public void deliverMulti(DeliverOutbox<T> processor,
                           Outbox outbox,
                           int headIndex,
                           int tailIndex,
                           WorkerOutbox<T> tailWorker)
    throws Exception
  {
    QueueRing<T> readQueue;
    
    readQueue = _readQueue;

    readQueue.deliverMulti(processor,
                           outbox,
                           headIndex,
                           tailIndex,
                           tailWorker);
  }
  
  @Override
  public void deliverMultiTail(DeliverOutbox<T> processor,
                               Outbox outbox,
                               int headIndex,
                               int tailIndex,
                               WorkerOutbox<T> tailWorker)
    throws Exception
  {
    QueueRing<T> readQueue;
    
    do {
      readQueue = _readQueue;

      readQueue.deliverMultiTail(processor,
                                 outbox,
                                 headIndex,
                                 tailIndex,
                                 tailWorker);
    } while (pollResize(readQueue));
  }
  */
  @Override
  public final CounterRingGroup counterGroup()
  {
    return _readQueue.counterGroup();
  }
  
  private QueueRing<M> createQueue(int capacity,
                                   long initialIndex)
  {
    if (_maxCapacity <= capacity) {
      return new QueueRing<M>(_maxCapacity, 
                              _counterBuilder,
                              initialIndex,
                              _baseBlocker);
    }
    else {
      ResizingRingBlocker resizingBlocker
        = new ResizingRingBlocker(capacity, initialIndex, _writeQueue);
      
      return resizingBlocker.getQueue();
    }
  }
  
  private boolean pollResize(QueueRing<M> readQueue)
  {
    synchronized (_resizeLock) {
      if (readQueue == _readQueue && readQueue != _writeQueue) {
        if (readQueue.isEmpty()) {
          _readQueue = _writeQueue;
          
          _baseBlocker.offerWake();
          _baseBlocker.pollWake();
          
          return true;
        }
      }
    }

    return false;
  }
  
  private void copyQueue(M item, QueueRing<M> queue)
  {
    long timeout = 10;
    
    offer(item, timeout, TimeUnit.SECONDS);
    
    while ((item = queue.poll()) != null) {
      offer(item, timeout, TimeUnit.SECONDS);
    }
  }
  
  public final void shutdown(ShutdownModeAmp mode)
  {
    _baseBlocker.shutdown(mode);
  }
  
  private class ResizingRingBlocker implements RingBlocker {
    private final QueueRing<M> _queue;
    
    ResizingRingBlocker(int capacity,
                        long initialIndex,
                        QueueRing<M> prevQueue)
    {
      _queue = new QueueRing<M>(capacity, 
                                _counterBuilder, initialIndex, 
                                     this);
    }
    
    QueueRing<M> getQueue()
    {
      return _queue;
    }
    
    @Override
    public final long nextOfferSequence()
    {
      return 0;
    }
    
    @Override
    public final boolean offerWait(long offerSequence,
                                   long timeout,
                                   TimeUnit unit)
    {
      synchronized (_resizeLock) {
        QueueRing<M> queue = _queue;

        if (queue.isWriteClosed()) {
          // queue was already resized
          return false;
        }
        else if (queue.size() + 1 < queue.getCapacity()) {
          // space available
          return true;
        }
        else if (queue.getCapacity() < _maxCapacity) {
          queue.closeWrite();

          QueueRing<M> nextQueue = createQueue(4 * queue.getCapacity(),
                                               queue.getHeadAlloc());

          if (queue != _readQueue) {
            // if unshifted, copy the items
            M item;

            while ((item = queue.poll()) != null) {
              // System.out.println("COPY: " + item);

              if (! nextQueue.offer(item)) {
                M firstItem = item;
                
                ThreadPool.current().schedule(()->copyQueue(firstItem, queue));
                break;
              }
            }
          }

          _writeQueue = nextQueue;

          return false;
        }
        else {
          return false;
        }
      }
    }

    @Override
    public final void offerWake()
    {
    }

    @Override
    public final boolean wake()
    {
      return true;
    }

    @Override
    public final void wakeAll()
    {
      wake();
    }
    
    @Override
    public final long nextPollSequence()
    {
      return _baseBlocker.nextPollSequence();
    }

    @Override
    public final boolean pollWait(long sequence,
                                  long timeout, 
                                  TimeUnit unit)
    {
      return _baseBlocker.pollWait(sequence, timeout, unit);
    }
    
    @Override
    public final boolean isPollWait()
    {
      return _baseBlocker.isPollWait();
    }

    @Override
    public void pollWake()
    {
      _baseBlocker.pollWake();
    }

    /*
    @Override
    public void onActive()
    {
      _baseBlocker.onActive();
    }

    @Override
    public void onInit()
    {
      _baseBlocker.onInit();
    }
    */

    @Override
    public void shutdown(ShutdownModeAmp mode)
    {
      _baseBlocker.shutdown(mode);
    }
    
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _writeQueue + "]";
    }
  }
}
