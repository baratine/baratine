/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
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

package com.caucho.v5.amp.inbox;

import java.util.concurrent.TimeUnit;

import com.caucho.v5.amp.queue.QueueDeliver;
import com.caucho.v5.amp.queue.QueueRingResizing;
import com.caucho.v5.amp.queue.QueueService;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.thread.WorkerThreadPoolBase;

/**
 * Queue for overflow.
 */
class InboxQueueReplyOverflow
{
  private static final int OVERFLOW_SIZE = 1024 * 1024;
  
  private final QueueService<MessageAmp> _targetQueue;
  private final QueueDeliver<MessageAmp> _overflowQueue;
  private final ReplyWriter _replyWriter = new ReplyWriter(); 
  
  InboxQueueReplyOverflow(QueueService<MessageAmp> targetQueue)
  {
    _targetQueue = targetQueue;
    
    _overflowQueue = new QueueRingResizing<>(256, OVERFLOW_SIZE);
  }
  
  boolean offer(MessageAmp msg)
  {
    boolean result = _overflowQueue.offer(msg);

    _replyWriter.wake();
    
    if (! result) {
      System.out.println("FullOverflow: " + msg);
    }
    
    return result;
  }
  
  private void processItem()
  {
    MessageAmp msg;
    
    while ((msg = _overflowQueue.poll()) != null) {
      _targetQueue.wake();
      
      if (! _targetQueue.offer(msg, 100, TimeUnit.SECONDS)) {
        System.out.println("FrozenQueue: " + msg + " " + _targetQueue);
      }
    }
    
    _targetQueue.wake();
  }
  
  class ReplyWriter extends WorkerThreadPoolBase {
    @Override
    public final long runTask()
    {
      processItem();

      return 0;
    }
  }
}
