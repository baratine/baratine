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

import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.thread.ThreadPool;
import com.caucho.v5.util.L10N;

/**
 * Value queue with atomic reference.
 */
public final class QueueRingResizing<T> 
  extends QueueDeliverBase<T>
{
  private static final L10N L = new L10N(QueueRingResizing.class);
  
  private final int _minCapacity;
  private final int _maxCapacity;
  
  private int _capacity;
  
  private final CounterBuilder _counterBuilder;
  private final RingBlocker _baseBlocker;

  private volatile QueueRing<T> _readQueue;
  
  private final Object _resizeLock = new Object();
  private volatile QueueRing<T> _writeQueue;
  
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
  
  public static <T> QueueDeliver<T> create(int min, int max)
  {
    return create(min, max,
                  CounterBuilderSingle.create(),
                  new RingBlockerBasic());
  }
    
  public static <T> QueueDeliver<T> create(int min, 
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
  
  @Override
  public WorkerDeliverLifecycle getOfferTask()
  {
    return _baseBlocker;
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
  public final boolean offer(T value, long timeout, TimeUnit unit)
  {
    QueueRing<T> writeQueue;
    do {
      writeQueue = _writeQueue;
      
      if (writeQueue.offer(value, timeout, unit)) {
        return true;
      }
    } while (writeQueue.isWriteClosed());
    
    return false;
  }
  
  @Override
  public final T peek()
  {
    QueueRing<T> queue = _readQueue;
    
    T value = queue.peek();
    
    if (value != null) {
      return value;
    }
    
    if (pollResize(queue)) {
      value = _readQueue.peek();
    }
    
    return value;
  }
 
  @Override
  public final T poll(long timeout, TimeUnit unit)
  {
    QueueRing<T> readQueue = _readQueue;

    T value = readQueue.poll(timeout, unit);
    
    while (value == null && pollResize(readQueue)) {
      readQueue = _readQueue;
      
      value = readQueue.poll(timeout, unit);
    }
    
    return value;
  }
  
  @Override
  public void deliver(Deliver<T> deliver, Outbox<T> outbox)
    throws Exception
  {
    QueueRing<T> readQueue;
    
    do {
      readQueue = _readQueue;
      
      readQueue.deliver(deliver, outbox);
    } while (pollResize(readQueue));
  }
  
  @Override
  public void deliver(Deliver<T> processor,
                      Outbox<T> outbox,
                      int headIndex,
                      int tailIndex,
                      WorkerDeliver nextWorker,
                      boolean isTail)
    throws Exception
  {
    QueueRing<T> readQueue;
    
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
  
  @Override
  public void deliverMulti(Deliver<T> processor,
                           Outbox<T> outbox,
                           int headIndex,
                           int tailIndex,
                           WorkerDeliver tailWorker)
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
  public void deliverMultiTail(Deliver<T> processor,
                               Outbox<T> outbox,
                               int headIndex,
                               int tailIndex,
                               WorkerDeliver tailWorker)
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
  
  @Override
  public final CounterGroup getCounterGroup()
  {
    return _readQueue.getCounterGroup();
  }
  
  private QueueRing<T> createQueue(int capacity,
                                   long initialIndex)
  {
    if (_maxCapacity <= capacity) {
      return new QueueRing<T>(_maxCapacity, 
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
  
  private boolean pollResize(QueueRing<T> readQueue)
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
  
  private void copyQueue(T item, QueueRing<T> queue)
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
    private final QueueRing<T> _queue;
    
    ResizingRingBlocker(int capacity,
                        long initialIndex,
                        QueueRing<T> prevQueue)
    {
      _queue = new QueueRing<T>(capacity, 
                                _counterBuilder, initialIndex, 
                                     this);
    }
    
    QueueRing<T> getQueue()
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
        QueueRing<T> queue = _queue;

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

          QueueRing<T> nextQueue = createQueue(4 * queue.getCapacity(),
                                               queue.getHeadAlloc());

          if (queue != _readQueue) {
            // if unshifted, copy the items
            T item;

            while ((item = queue.poll()) != null) {
              // System.out.println("COPY: " + item);

              if (! nextQueue.offer(item)) {
                T firstItem = item;
                
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
