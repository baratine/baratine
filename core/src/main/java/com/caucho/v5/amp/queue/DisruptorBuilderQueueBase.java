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

import com.caucho.v5.amp.outbox.DeliverOutbox;
import com.caucho.v5.amp.outbox.MessageOutbox;
import com.caucho.v5.amp.outbox.QueueOutbox;
import com.caucho.v5.amp.outbox.QueueService;
import com.caucho.v5.amp.outbox.WorkerOutbox;



/**
 * Interface to build a disruptor based queue.
 */
public class DisruptorBuilderQueueBase<M extends MessageOutbox<M>>
  implements DisruptorBuilderQueue<M>
{
  /*
  @Override
  public DisruptorBuilderQueue<M,C> peer(DeliverFactory<M> factory)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */
  
  @Override
  public DisruptorBuilderQueue<M> next(DeliverFactory<M> factory)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public DisruptorBuilderQueue<M> next(DeliverOutbox<M> deliver)
  {
    return next(new DeliverFactorySingleton<>(deliver));
  }
  
  /*
  @Override
  public DisruptorBuilderQueue<M,C> prologue(DeliverFactory<M> factory)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */
  
  @Override
  public CounterBuilder createCounterBuilder(CounterBuilder head,
                                                  int index)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public QueueService<M> build()
  {
    return getTop().build();
  }
  
  protected DisruptorBuilderQueueTop<M> getTop()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  protected QueueService<M> build(QueueDeliverBuilder<M> queueBuilder)
  {
    CounterBuilderHead prev = new CounterBuilderHead();
    
    CounterBuilder next = createCounterBuilder(prev, prev.getHeadIndex() + 1);
    
    CounterBuilder counter = new CounterBuilderTop(prev, next);
    
    QueueOutbox<M> queue = queueBuilder.buildQueue(counter);
    
    WorkerOutbox<M> nextTask = null;//(WorkerDeliverLifecycle) queue.worker();
    // Executor executor = queueBuilder.createExecutor();

    WorkerOutbox<M> worker;
    
    worker = build(queue, 
                   prev,
                   next,
                   nextTask, 
                   queueBuilder,
                   true);
    
    if (worker instanceof WorkerDeliverDisruptor) {
      WorkerDeliverDisruptor<M> workerDisruptor
        = (WorkerDeliverDisruptor<M>) worker;
      
      workerDisruptor.setHeadWorker(worker);
    }

    return new QueueServiceImpl<M>(queue, worker);
  }

  @Override
  public WorkerOutbox<M> build(QueueOutbox<M> queue,
                                         CounterBuilder head,
                                         CounterBuilder tail,
                                         WorkerOutbox<M> nextTask,
                                         QueueDeliverBuilder<M> queueBuilder,
                                         boolean isTail)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  static class DeliverFactorySingleton<M extends MessageOutbox<M>>
    implements DeliverFactory<M>
  {
    private DeliverOutbox<M> _deliver;
    
    DeliverFactorySingleton(DeliverOutbox<M> deliver)
    {
      Objects.requireNonNull(deliver);
      
      _deliver = deliver;
    }
    
    @Override
    public int getMaxWorkers()
    {
      return 1;
    }
    
    @Override
    public DeliverOutbox<M> get()
    {
      return _deliver;
    }
  }
}
