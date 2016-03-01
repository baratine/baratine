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

import java.util.concurrent.Executor;
import java.util.function.Supplier;


/**
 * Interface for an actor queue
 */
class DisruptorBuilderImpl<M extends MessageDeliver>
  extends DisruptorBuilderQueueBase<M>
{
  private final Deliver<M> _deliver;
  
  DisruptorBuilderImpl(Deliver<M> actor)
  {
    _deliver = actor;
  }
  
  @Override
  public CounterBuilder createCounterBuilder(CounterBuilder head,
                                                  int index)
  {
    return new CounterBuilderAtomic(index);
  }

  @Override
  public WorkerDeliverBase<M>
  build(QueueDeliver<M> queue,
        CounterBuilder prev,
        CounterBuilder next,
        WorkerDeliverLifecycle<M> nextTask,
        QueueDeliverBuilder<M> queueBuilder,
        boolean isTail)
  {
    Executor executor = queueBuilder.createExecutor();
    ClassLoader loader = queueBuilder.getClassLoader();
    
    Supplier<OutboxDeliver<M>> outboxFactory = queueBuilder.getOutboxFactory();
    OutboxContext<M> outboxContext = queueBuilder.getOutboxContext();
    
    WorkerDeliverDisruptor<M> worker;

    worker = new WorkerDeliverDisruptor<M>(_deliver,
                                           outboxFactory,
                                           outboxContext,
                                           executor,
                                           loader,
                                           queue,
                                           prev.getTailIndex(),
                                           next.getHeadIndex(), 
                                           isTail,
                                           nextTask);
    
    return worker;
  }

  public WorkerDeliverBase<M>
  buildSingle(QueueDeliver<M> queue,
              QueueDeliverBuilder<M> queueBuilder)
  {
    Executor executor = queueBuilder.createExecutor();
    ClassLoader loader = queueBuilder.getClassLoader();
    
    Supplier<OutboxDeliver<M>> outboxFactory = queueBuilder.getOutboxFactory();
    OutboxContext<M> outboxContext = queueBuilder.getOutboxContext();
    
    WorkerDeliverSingleThread<M> worker;
    
    worker = new WorkerDeliverSingleThread<M>(_deliver,
                                              outboxFactory,
                                              outboxContext,
                                              executor,
                                              loader,
                                              queue);
    
    return worker;
  }
}
