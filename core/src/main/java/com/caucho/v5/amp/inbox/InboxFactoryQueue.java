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

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.manager.ServiceConfig;
import com.caucho.v5.amp.queue.QueueServiceBuilderImpl;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.InboxFactoryAmp;
import com.caucho.v5.amp.spi.MessageAmp;

/**
 * Creates mailboxes for actors.
 */
public class InboxFactoryQueue implements InboxFactoryAmp
{
  //private OutboxAmpFactory _outboxFactory;
  private ServiceManagerAmp _manager;
  
  public InboxFactoryQueue(ServiceManagerAmp manager)
  {
    _manager = manager;
    //_outboxFactory = new OutboxAmpFactory(manager);
  }
  /**
   * Creates a mailbox for an actor.
   */
  @Override
  public InboxAmp create(ServiceManagerAmp manager,
                            QueueServiceFactoryInbox serviceQueueFactory,
                            ServiceConfig config)
  {
    QueueServiceBuilderImpl<MessageAmp> queueBuilder
      = new QueueServiceBuilderImpl<>();
      
    //queueBuilder.setOutboxFactory(OutboxAmpFactory.newFactory());
    // Executor executor = ThreadPool.getCurrent();
    queueBuilder.setClassLoader(_manager.classLoader());
    
    queueBuilder.capacity(config.queueSizeMax());
    queueBuilder.initial(config.queueSize());
    
    return new InboxQueue(manager, 
                            queueBuilder,
                            serviceQueueFactory,
                            config);
  }
}
