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

import com.caucho.v5.amp.spi.ShutdownModeAmp;

/**
 * Processes an actor item.
 */
public class DeliverAmpBase<M>
  implements Deliver<M>
{
  @Override
  public String getName()
  {
    return getClass().getSimpleName();
  }
  
  /**
   * Initialize the deliver with the worker's outbox.
   */
  /*
  @Override
  public void initOutbox(Outbox<M> message)
  {
  }
  */
  
  /**
   * Delivers a message to the actor.
   * 
   * @param msg the message to be delivered
   * @param threadContext the thread context
   */
  /*
  public void deliver(M msg)
    throws Exception
  {
  }
  */
  
  @Override
  public void deliver(M msg, Outbox<M> outbox)
    throws Exception
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Called before items in the queue are processed. This can be
   * used to flush buffers.
   */
  @Override
  public void beforeBatch()
  {
  }

  /**
   * Called when all items in the queue are processed. This can be
   * used to flush buffers.
   * @throws Exception 
   */
  @Override
  public void afterBatch()
    throws Exception
  {
  }

  @Override
  public void onInit()
  {
  }

  @Override
  public void onActive()
  {
  }

  @Override
  public void shutdown(ShutdownModeAmp shutdown)
  {
  }
}
