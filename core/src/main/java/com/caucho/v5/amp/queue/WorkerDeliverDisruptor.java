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
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.outbox.DeliverOutbox;
import com.caucho.v5.amp.outbox.MessageOutbox;
import com.caucho.v5.amp.outbox.Outbox;
import com.caucho.v5.amp.outbox.QueueOutbox;
import com.caucho.v5.amp.outbox.WorkerOutbox;
import com.caucho.v5.amp.outbox.WorkerOutboxBase;
import com.caucho.v5.amp.spi.ShutdownModeAmp;


/**
 * Interface for the transaction log.
 */
public final class WorkerDeliverDisruptor<M extends MessageOutbox<M>>
  extends WorkerOutboxBase<M>
{
  private static final Logger log
    = Logger.getLogger(WorkerDeliverDisruptor.class.getName());
  
  private final QueueOutbox<M> _queue;
  private final DeliverOutbox<M> _deliver;
  
  private final int _headCounter;
  private final int _tailCounter;
  private final boolean _isTail;
  
  private WorkerOutbox<M> _headWorker;
  private final WorkerOutbox<M> _tailWorker;
  
  public WorkerDeliverDisruptor(DeliverOutbox<M> deliver,
                                Object outboxContext,
                                Executor executor,
                                ClassLoader loader,
                                QueueOutbox<M> queue,
                                int headCounterIndex, 
                                int tailCounterIndex,
                                boolean isTail,
                                WorkerOutbox<M> tailWorker)
  {
    super(deliver, outboxContext, executor, loader);

    _queue = queue;
    _deliver = deliver;
    _headCounter = headCounterIndex;
    _tailCounter = tailCounterIndex;
    _tailWorker = tailWorker;
    _isTail = isTail;
  }
  
  void setHeadWorker(WorkerOutbox<M> headWorker)
  {
    _headWorker = headWorker;
    
    if (_tailWorker instanceof WorkerDeliverDisruptor) {
      WorkerDeliverDisruptor<M> tailDisruptor
        = (WorkerDeliverDisruptor<M>) _tailWorker;
      
      tailDisruptor.setHeadWorker(headWorker);
    }
  }

  @Override
  public void runImpl(Outbox outbox, M msg)
  {
    
    final DeliverOutbox<M> deliver = _deliver;
    final QueueOutbox<M> queue = _queue;
    final WorkerOutbox<M> tailWorker = _tailWorker;
    final boolean isTail = _isTail;
    
    if (msg != null) {
      if (queue.offer(msg, 0, TimeUnit.SECONDS)) {
        msg = null;
      }
    }
    
    try {
      deliver.beforeBatch();

      try {
        queue.deliver(deliver,
                      outbox,
                      _headCounter,
                      _tailCounter,
                      tailWorker, isTail);
      } finally {
        deliver.afterBatch();
      }
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);
    }
    
    if (msg != null) {
      if (! queue.offer(msg, 10, TimeUnit.SECONDS)) {
        log.warning("Dropped message: " + msg);
      }
    }
    
    if (isTail && queue.size() > 0 && _headWorker != null) {
      // with queue resizing, the head might need to be woken
      _headWorker.wake();
    }
  }

  @Override
  public void onActive()
  {
    _deliver.onActive();
    
    if (! _isTail) {
      _tailWorker.onActive();
    }
  }

  @Override
  public void onInit()
  {
    _deliver.onInit();
    
    if (! _isTail) {
      _tailWorker.onInit();
    }
  }

  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    // cloud/05h3
    // _deliver.shutdown(mode);
    
    if (! _isTail) {
      _tailWorker.shutdown(mode);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _deliver + "," + getState() + "]";
  }
}
