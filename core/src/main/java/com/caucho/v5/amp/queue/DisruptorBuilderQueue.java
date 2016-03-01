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


import java.util.function.Supplier;

/**
 * Interface for building a disruptor queue.
 */
public interface DisruptorBuilderQueue<M extends MessageDeliver>
{
  DisruptorBuilderQueue<M> peer(DeliverFactory<M> factory);
  
  DisruptorBuilderQueue<M> next(DeliverFactory<M> factory);
  DisruptorBuilderQueue<M> next(Deliver<M> factory);

  /**
   * Used in cases like the journal where the journal is prepended to
   * the main.
   */
  DisruptorBuilderQueue<M> prologue(DeliverFactory<M> factory);
  
  //QueueService<M> build(QueueDeliverBuilder<M> queueBuilder);
  QueueService<M> build();
  
  CounterBuilder createCounterBuilder(CounterBuilder head,
                                           int index);
  
  /*
  ActorThreadContextImpl<M> build(ActorQueue<M> queue,
                                  ActorCounterBuilder head,
                                  ActorCounterBuilder tail,
                                  TaskWorker nextTask,
                                  ActorQueueBuilder<M> queueBuilder,
                                  boolean isTail);
  */
  
  WorkerDeliver<M> build(QueueDeliver<M> queue,
                      CounterBuilder head,
                      CounterBuilder tail,
                      WorkerDeliverLifecycle<M> nextTask,
                      QueueDeliverBuilder<M> queueBuilder,
                      boolean isTail);
  
  public interface DeliverFactory<M> extends Supplier<Deliver<M>>
  {
    @Override
    Deliver<M> get();
    
    int getMaxWorkers();
  }
}
