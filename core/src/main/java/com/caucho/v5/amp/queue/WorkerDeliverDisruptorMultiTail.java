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
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Interface for the transaction log.
 */
public final class WorkerDeliverDisruptorMultiTail<M extends MessageDeliver>
  extends WorkerDeliverBase<M>
{
  private static final Logger log
    = Logger.getLogger(WorkerDeliverDisruptorMultiTail.class.getName());
  
  private final QueueDeliver<M> _queue;
  private final Deliver<M> _actor;
  
  private final int _headCounter;
  private final int _tailCounter;
  
  private final WorkerDeliverLifecycle<M> _tailWorker;
  
  public WorkerDeliverDisruptorMultiTail(Deliver<M> deliver,
                                         Supplier<OutboxDeliver<M>> outboxFactory,
                                         OutboxContext<M> outboxContext,
                                         Executor executor,
                                         ClassLoader loader,
                                         QueueDeliver<M> queue,
                                         int headCounterIndex, 
                                         int tailCounterIndex,
                                         WorkerDeliverLifecycle<M> tailWorker)
  {
    super(deliver, outboxFactory, outboxContext, executor, loader);
    
    _queue = queue;
    _actor = deliver;
    _headCounter = headCounterIndex;
    _tailCounter = tailCounterIndex;
    _tailWorker = tailWorker;
  }
  
  private Deliver<M> getActor()
  {
    return _actor;
  }

  @Override
  public void runImpl(OutboxDeliver<M> outbox, M msg)
  {
    if (msg != null) {
      throw new IllegalArgumentException();
    }
    
    final Deliver<M> actor = getActor();
    final QueueDeliver<M> queue = _queue;
    final WorkerDeliverLifecycle<M> tailWorker = _tailWorker;
    
    try {
      actor.beforeBatch();

      try {
        queue.deliverMultiTail(actor,
                               outbox,
                               _headCounter,
                               _tailCounter,
                               tailWorker);
      } finally {
        actor.afterBatch();
      }
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _actor + "]";
  }
}
