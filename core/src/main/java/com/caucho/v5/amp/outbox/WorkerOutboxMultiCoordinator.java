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

package com.caucho.v5.amp.outbox;

import com.caucho.v5.amp.spi.ShutdownModeAmp;

/**
 * queue with delivery workers that consume its messages.
 */
public class WorkerOutboxMultiCoordinator<M extends MessageOutbox<M>>
  implements WorkerOutbox<M>
{
  private QueueOutbox<M> _queue;
  private WorkerOutbox<M>[] _workers;
  private int _multiworkerOffset;
  
  public WorkerOutboxMultiCoordinator(QueueOutbox<M> queue,
                                      WorkerOutbox<M> []workers,
                                      int multiworkerOffset)
  {
    _queue = queue;
    _workers = workers;
    _multiworkerOffset = multiworkerOffset;
  }
    
  @Override
  public boolean wake()
  {
    int size = _queue.size();
  
    int count = (size + _multiworkerOffset - 1) / _multiworkerOffset;
    boolean isWake = false;
  
    for (int i = 0; i < count && i < _workers.length; i++) {
      if (_workers[i].wake()) {
        isWake = true;
      }
    }
    
    return isWake;
  }
  
  @Override
  public void wakeAll()
  {
    for (WorkerOutbox<M> worker : _workers) {
      worker.wakeAll();
    }
  }

  @Override
  public void onActive()
  {
    for (WorkerOutbox<M> worker : _workers) {
      worker.onActive();
    }
  }

  @Override
  public void onInit()
  {
    for (WorkerOutbox<M> worker : _workers) {
      worker.onInit();
    }
  }

  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    for (WorkerOutbox<M> worker : _workers) {
      worker.shutdown(mode);
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _workers[0] +  "]";
  }
}
