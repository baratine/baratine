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


/**
 * Interface for an actor queue
 */
public class DisruptorBuilderQueueTop<M extends MessageDeliver>
  extends DisruptorBuilderQueueBase<M>
{
  private DisruptorBuilderQueueNode<M> _prologue;
  private DisruptorBuilderQueueNode<M> _main;
  
  private DisruptorBuilderQueueNode<M> _top;
  private QueueDeliverBuilder<M> _queueBuilder;

  DisruptorBuilderQueueTop(QueueDeliverBuilder<M> queueBuilder,
                      DeliverFactory<M> actorFactory)
  {
    _main = new DisruptorBuilderQueueNode<M>(this, actorFactory);
    _queueBuilder = queueBuilder;
  }
  
  @Override
  public DisruptorBuilderQueueNode<M> prologue(DeliverFactory<M> actorFactory)
  {
    if (_prologue != null || _top != null) {
      throw new IllegalStateException();
    }
    
    DisruptorBuilderQueueNode<M> prologue
      = new DisruptorBuilderQueueNode<>(this, actorFactory);
    
    _prologue = prologue;
    
    return prologue;
  }
  
  @Override
  public DisruptorBuilderQueueNode<M> peer(DeliverFactory<M> actorFactory)
  {
    return _main.peer(actorFactory);
  }
  
  @Override
  public DisruptorBuilderQueueNode<M> next(DeliverFactory<M> actorFactory)
  {
    return _main.next(actorFactory);
  }
  
  @Override
  public CounterBuilder createCounterBuilder(CounterBuilder head,
                                                  int index)
  {
    DisruptorBuilderQueueNode<M> top = getTopNode();
    
    return top.createCounterBuilder(head, index);
  }

  @Override
  public WorkerDeliverLifecycle<M> build(QueueDeliver<M> queue,
                                         CounterBuilder headBuilder,
                                         CounterBuilder tailBuilder,
                                         WorkerDeliverLifecycle<M> nextTask,
                                         QueueDeliverBuilder<M> queueBuilder,
                                         boolean isTail)
  {
    DisruptorBuilderQueueNode<M> top = getTopNode();
    
    if (top.getPeers().size() == 0 && top.getNext() == null) {
      return top.buildSingle(queue, queueBuilder);
    }
    else {
      return top.build(queue, headBuilder, tailBuilder, nextTask, 
                        queueBuilder, isTail);
    }
  }

  @Override
  public QueueService<M> build()
  {
    return build(_queueBuilder);
  }
  
  @Override
  protected DisruptorBuilderQueueTop<M> getTop()
  {
    return this;
  }

  private DisruptorBuilderQueueNode<M> getTopNode()
  {
    if (_top != null) {
    }
    else if (_prologue != null) {
      _top = _prologue;
      
      _top.setNext(_main);
    }
    else {
      _top = _main;
    }
    
    return _top;
  }
}
