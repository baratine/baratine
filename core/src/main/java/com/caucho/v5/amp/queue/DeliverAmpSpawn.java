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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Processor to spawn threads.
 */
final class DeliverAmpSpawn<M extends MessageDeliver>
  extends DeliverAmpBase<M>
{
  private static final Logger log 
    = Logger.getLogger(DeliverAmpSpawn.class.getName());
  
  private final Deliver<M> _processor;
  private final Executor _executor;

  private OutboxDeliverBase<M> _outbox;
  
  DeliverAmpSpawn(Deliver<M> processor,
                 Executor executor)
  {
    _processor = processor;
    _executor = executor;
    _outbox = new OutboxDeliverImpl<M>();
  }
  
  @Override
  public void deliver(M item, Outbox<M> outbox)
  {
    _executor.execute(new SpawnTask(item));
  }
  
  class SpawnTask implements Runnable {
    private M _item;
    
    SpawnTask(M item)
    {
      _item = item;
    }
    
    @Override
    public void run()
    {
      Deliver<M> actor = _processor;
      
      try {
        actor.beforeBatch();
      
        actor.deliver(_item, _outbox);
      
        actor.afterBatch();
      } catch (Exception e) {
        log.log(Level.FINER, e.toString(), e);
      } finally {
      }
    }
    
  }
}
