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

import java.util.concurrent.atomic.AtomicLong;

import com.caucho.v5.amp.spi.ShutdownModeAmp;

/**
 * Interface for building a disruptor queue.
 */
public class WorkerDeliverDisruptorMultiWorker<M extends MessageDeliver> 
  implements WorkerDeliverMessage<M>
{
  private final QueueDeliver<M> _queue;
  private final WorkerDeliverMessage<M>[] _workers;
  private final AtomicLong _lastHead = new AtomicLong();
  
  WorkerDeliverDisruptorMultiWorker(QueueDeliver<M> queue,
                                    WorkerDeliverMessage<M> []workers)
  {
    _queue = queue;
    _workers = workers;
  }
  
  @Override
  public boolean runOne(OutboxDeliver<M> outbox, M tailMsg)
  {
    if (! _queue.isEmpty()) {
      return false;
    }
    
    for (WorkerDeliverMessage<M> worker : _workers) {
      if (worker.runOne(outbox, tailMsg)) {
        return true;
      }
    }
    
    return false;
  }
  
  @Override
  public boolean wake()
  {
    long lastHead;
    long head;

    do {
      lastHead = _lastHead.get();
      head = _queue.getHead();
    } while (! _lastHead.compareAndSet(lastHead, head));
    
    long size = Math.min(head - lastHead, _queue.size());

    boolean isWake = false;
    
    WorkerDeliverLifecycle[] workers = _workers;
    
    int len = workers.length;

    for (int i = 0; i < len && size > 0; i++) {
      if (workers[i].wake()) {
        isWake = true;
        
        size = Math.min(size - 1, _queue.size());
      }
    }
    
    return isWake;
  }

  @Override
  public void wakeAll()
  {
    for (WorkerDeliverLifecycle worker : _workers) {
      worker.wakeAll();
    }
  }

  @Override
  public void wakeAllAndWait()
  {
    for (WorkerDeliverLifecycle worker : _workers) {
      worker.wakeAllAndWait();
    }
  }
  
  @Override
  public void onActive()
  {
    for (WorkerDeliverLifecycle worker : _workers) {
      worker.onActive();
    }
  }
  
  @Override
  public void onInit()
  {
    for (WorkerDeliverLifecycle worker : _workers) {
      worker.onInit();
    }
  }
  
  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    for (WorkerDeliverLifecycle worker : _workers) {
      worker.shutdown(mode);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _workers[0] + "]";
  }
}
