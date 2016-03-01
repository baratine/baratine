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
 * Extension deliver message queue.Ring-based memory queue processed by a single worker.
 */
abstract public class DeliverServiceQueueBase<M extends MessageDeliver>
  extends DeliverAmpBase<M> 
{
  private final QueueService<M> _queue;
  
  public DeliverServiceQueueBase(int size)
  {
    QueueServiceBuilderImpl<M> builder = new QueueServiceBuilderImpl<>();
    builder.capacity(size);
    
    _queue = builder.build(this);
  }
  
  public final boolean isEmpty()
  {
    return _queue.isEmpty();
  }
  
  public final int getSize()
  {
    return _queue.size();
  }
  
  public final boolean offer(M value)
  {
    _queue.offer(value);
    
    return true;
  }
  
  public final void wake()
  {
    _queue.wake();
  }
  
  @Override
  abstract public void deliver(M tailMessage, Outbox<M> outbox);
  
  @Override
  public void beforeBatch()
  {
  }
  
  @Override
  public void afterBatch()
  {
  }
}
