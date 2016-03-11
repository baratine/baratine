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

import com.caucho.v5.amp.outbox.DeliverOutbox;
import com.caucho.v5.amp.outbox.MessageOutbox;
import com.caucho.v5.amp.outbox.QueueService;
import com.caucho.v5.amp.queue.DisruptorBuilderQueue.DeliverFactory;

/**
 * Builder for a service queue.
 */
public interface QueueServiceBuilder<M extends MessageOutbox<M>>
{
  QueueServiceBuilder<M> processors(DeliverOutbox<M> ...processors);
  
  QueueServiceBuilder<M> capacity(int capacity);
  
  int getCapacity();
  
  QueueServiceBuilder<M> initial(int initial);
  
  int getInitial();
  
  QueueServiceBuilder<M> multiworker(boolean isMultiworker);
  
  boolean isMultiworker();
  
  QueueServiceBuilder<M> multiworkerOffset(int offset);
  
  int getMultiworkerOffset();

  QueueService<M> build(DeliverOutbox<M> processor);

  QueueService<M> build(DeliverOutbox<M> ...processors);

  DisruptorBuilderQueue<M> disruptorBuilder(DeliverFactory<M> actorFactory);
  
  DisruptorBuilderQueue<M> disruptorBuilder(DeliverOutbox<M> actorFactory);
  
  QueueService<M> disruptor(DeliverOutbox<M> first,
                            DeliverOutbox<M> ...rest);
}
