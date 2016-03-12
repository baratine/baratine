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

package com.caucho.v5.amp.deliver;

import java.util.concurrent.Executor;

import com.caucho.v5.amp.spi.ShutdownModeAmp;

public final class WorkerDeliverMultiThread<M> // extends MessageOutbox<M>>
  extends WorkerDeliverBase<M>
{
  private final QueueRing<M> _queue;
  private final Deliver<M> _deliver;
    
  public WorkerDeliverMultiThread(Deliver<M> deliver,
                                 Object outboxContext,
                                 Executor executor,
                                 ClassLoader loader,
                                 QueueRing<M> queue)
  {
    super(deliver, outboxContext, executor, loader);
      
    _queue = queue;
    _deliver = deliver;
  }
  
  protected Deliver<M> getActor()
  {
    return _deliver;
  }
    
  @Override
  public void runImpl(Outbox outbox, M item)
    throws Exception
  {
    final QueueRing<M> queue = _queue;
    final Deliver<M> deliver = getActor();
      
    deliver.beforeBatch();
      
    try {
      if (item != null && (queue.size() == 0 || ! queue.offer(item))) {
        // deliver message from the current thread's outbox, bypassing queue
        deliver.deliver(item, outbox);
      }

      M value;

      while ((value = queue.poll()) != null) {
        deliver.deliver(value, outbox);
      }
    } finally {
      deliver.afterBatch();
    }
  }

  @Override
  protected void runOneImpl(Outbox outbox, M tailMsg) 
    throws Exception
  {
    final Deliver<M> deliver = getActor();
      
    deliver.beforeBatch();
      
    try {
      // deliver message from the current thread's outbox, bypassing queue
      deliver.deliver(tailMsg, outbox);
    } finally {
      deliver.afterBatch();
    }
  }

  @Override
  protected boolean isRunOneValid()
  {
    return _queue.isEmpty();
  }
  
  /*
  @Override
  public void onInit()
  {
    super.onInit();

    _deliver.onInit();
  }
  
  @Override
  public void onActive()
  {
    super.onActive();

    _deliver.onActive();
  }
  */
  
  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    super.shutdown(mode);

    _deliver.shutdown(mode);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _deliver + "]";
  }
}
