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

import com.caucho.v5.amp.outbox.DeliverOutbox;
import com.caucho.v5.amp.outbox.MessageOutbox;
import com.caucho.v5.amp.outbox.Outbox;
import com.caucho.v5.amp.outbox.OutboxImpl;

/**
 * Processor to spawn threads.
 */
final class DeliverAmpSpawn<M extends MessageOutbox<M>>
  implements DeliverOutbox<M>
{
  private static final Logger log 
    = Logger.getLogger(DeliverAmpSpawn.class.getName());
  
  private final DeliverOutbox<M> _processor;
  private final Executor _executor;

  private Outbox _outbox;
  
  DeliverAmpSpawn(DeliverOutbox<M> processor,
                 Executor executor)
  {
    _processor = processor;
    _executor = executor;
    _outbox = new OutboxImpl();
  }
  
  @Override
  public void deliver(M item, Outbox outbox)
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
      DeliverOutbox<M> deliver = _processor;
      
      try {
        deliver.beforeBatch();
      
        deliver.deliver(_item, _outbox);
      
        deliver.afterBatch();
      } catch (Exception e) {
        log.log(Level.FINER, e.toString(), e);
      } finally {
      }
    }
    
  }
}
