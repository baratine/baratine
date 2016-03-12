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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;


/**
 * Blocking queue with a processor.
 */
public interface QueueRing<M>
  extends BlockingQueue<M>
{
  long head();

  /**
   * Offer a new message to the queue. 
   * 
   * A following {@code wake()} will be required because offer 
   * does not automatically wake the consumer
   * 
   * @param value the next message
   * @param timeout offer timeout
   * @param unit units for the offer timeout
   * @return true if the offer succeeds
   */
  @Override
  boolean offer(M value,
                long timeout,
                TimeUnit unit);
  
  /**
   * Wake the worker to process new messages.
   */
  void wake();
  
  /**
   * Deliver available messages to the delivery handler.
   * 
   * @param deliver handler to process the message 
   * @param outbox message context
   */
  void deliver(Deliver<M> deliver,
               Outbox outbox)
    throws Exception;
  
  int counterGroupSize();
  
  /**
   * Disruptor delivery.
   * 
   * Disruptors handle a chain of delivery agents that process the
   * message in turn.
   * 
   * @param deliver delivery handler for the message
   * @param outbox context for the deliver
   * @param headIndex head counter index for the delivery
   * @param tailIndex tail counter index for the delivery
   * @param nextWorker next worker to wake in the chain
   * @param isTail true for the last delivery in the chain
   */
  void deliver(Deliver<M> deliver,
               Outbox outbox,
               int headIndex,
               int tailIndex,
               WorkerDeliver<?> nextWorker,
               boolean isTail)
    throws Exception;
}
