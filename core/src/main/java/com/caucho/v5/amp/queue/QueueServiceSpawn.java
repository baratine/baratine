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
 * Queue that spawns threads to handle requests.
 */
public final class QueueServiceSpawn<T extends Runnable>
  extends QueueServiceBase<T>
{
  private static final Logger log 
    = Logger.getLogger(QueueServiceSpawn.class.getName());
  
  private final Executor _executor;
  
  private final RunnableQueueServiceSpawn _worker = new RunnableQueueServiceSpawn();
  
  private final SpawnThreadManager _threadManager;
  
  QueueServiceSpawn(QueueDeliver<T> queue,
                 Executor executor,
                 SpawnThreadManager threadManager)
  {
    super(queue);
    
    _executor = executor;
    _threadManager = threadManager;
  }
  
  @Override
  public boolean wake()
  {
    if (_threadManager.getSpawnCount() < getQueue().size()
        && _threadManager.allocateThread()) {
      _executor.execute(_worker);
      
      return true;
    }
    else {
      return false;
    }
  }
  
  @Override
  public void wakeAllAndWait()
  {
    wakeAll();
  }
  
  private class RunnableQueueServiceSpawn implements Runnable {
    @Override
    public void run()
    {
      try {
        boolean isFirst = true;
        
        while (true) {
          T value = getQueue().poll();
      
          if (isFirst) {
            _threadManager.onThreadBegin();
          }
          
          isFirst = false;
      
          wake();
        
          if (value == null) {
            return;
          }
        
          value.run();
        }
      } catch (Exception e) {
        log.log(Level.FINER, e.toString(), e);
      } finally {
        _threadManager.onThreadEnd();
        
        wake();
      }
    }
  }
}
