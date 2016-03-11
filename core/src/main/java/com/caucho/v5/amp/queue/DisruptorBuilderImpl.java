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

import com.caucho.v5.amp.outbox.DeliverOutbox;
import com.caucho.v5.amp.outbox.MessageOutbox;
import com.caucho.v5.amp.outbox.QueueOutbox;
import com.caucho.v5.amp.outbox.WorkerOutbox;
import com.caucho.v5.amp.outbox.WorkerOutboxSingleThread;


/**
 * Interface for an actor queue
 */
class DisruptorBuilderImpl<M extends MessageOutbox<M>>
  extends DisruptorBuilderQueueBase<M>
{
  private final DeliverOutbox<M> _deliver;
  
  DisruptorBuilderImpl(DeliverOutbox<M> deliver)
  {
    _deliver = deliver;
  }
  
  @Override
  public CounterBuilder createCounterBuilder(CounterBuilder head,
                                                  int index)
  {
    return new CounterBuilderAtomic(index);
  }

  @Override
  public WorkerOutbox<M>
  build(QueueOutbox<M> queue,
        CounterBuilder prev,
        CounterBuilder next,
        WorkerOutbox<M> nextTask,
        QueueDeliverBuilder<M> queueBuilder,
        boolean isTail)
  {
    Executor executor = queueBuilder.createExecutor();
    ClassLoader loader = queueBuilder.getClassLoader();
    
    //Supplier<Outbox<M,C>> outboxFactory = queueBuilder.getOutboxFactory();
    Object outboxContext = queueBuilder.getOutboxContext();
    
    WorkerOutbox<M> worker;

    worker = new WorkerDeliverDisruptor<>(_deliver,
                  //                         outboxFactory,
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

  public WorkerOutbox<M>
  buildSingle(QueueOutbox<M> queue,
              QueueDeliverBuilder<M> queueBuilder)
  {
    Executor executor = queueBuilder.createExecutor();
    ClassLoader loader = queueBuilder.getClassLoader();
    
    //Supplier<Outbox<M,C>> outboxFactory = queueBuilder.getOutboxFactory();
    Object outboxContext = queueBuilder.getOutboxContext();
    
    WorkerOutboxSingleThread<M> worker;
    
    worker = new WorkerOutboxSingleThread<>(_deliver,
                                              outboxContext,
                                              executor,
                                              loader,
                                              queue);
    
    return worker;
  }
}
