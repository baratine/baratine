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

import com.caucho.v5.amp.outbox.MessageOutbox;
import com.caucho.v5.amp.outbox.QueueOutbox;
import com.caucho.v5.amp.outbox.WorkerOutbox;
import com.caucho.v5.amp.spi.ShutdownModeAmp;


/**
 * queue with attached workers to process messages.
 */
public final class QueueServiceImpl<M extends MessageOutbox<M>>
  extends QueueServiceBase<M>
{
  private final WorkerOutbox<M> _worker;
  
  QueueServiceImpl(QueueOutbox<M> queue,
                   WorkerOutbox<M> worker)
  {
    super(queue);

    _worker = worker;
  }
  
  /**
   * Returns the head worker in the queue for late queuing.
   */
  /*
  @Override
  public WorkerOutbox<M,C> getWorker()
  {
    return _worker;
  }
  */
  
  @Override
  public boolean isSingleWorker()
  {
    return getQueue().counterGroup().getSize() == 2;
  } 
  
  @Override
  public boolean wake()
  {
    return _worker.wake();
  }
  
  @Override
  public WorkerOutbox<M> worker()
  {
    return _worker;
  }
  
  @Override
  public void wakeAll()
  {
    _worker.wakeAll();
  }
  
  @Override
  public void wakeAllAndWait()
  {
    _worker.wakeAllAndWait();
  }
  
  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    super.shutdown(mode);
    
    _worker.shutdown(mode);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _worker + "]";
  }
}
