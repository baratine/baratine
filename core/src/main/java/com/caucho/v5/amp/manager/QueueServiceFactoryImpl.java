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

package com.caucho.v5.amp.manager;

import java.util.Objects;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.inbox.InboxQueue;
import com.caucho.v5.amp.inbox.QueueServiceFactoryInbox;
import com.caucho.v5.amp.outbox.QueueService;
import com.caucho.v5.amp.queue.DisruptorBuilderQueue;
import com.caucho.v5.amp.queue.DisruptorBuilderQueue.DeliverFactory;
import com.caucho.v5.amp.queue.QueueServiceBuilder;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.ActorFactoryAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;

/**
 * Creates MPC skeletons and stubs.
 */
public class QueueServiceFactoryImpl implements QueueServiceFactoryInbox
{
  private ServiceManagerAmp _manager;
  /*
  private String _name;
  private ActorAmp _actor;
  private Supplier<ActorAmp> _supplier;
  private ServiceConfig _config;
  */
  private ActorFactoryAmp _actorFactory;

  QueueServiceFactoryImpl(ServiceManagerAmp manager,
                          ActorFactoryAmp actorFactory)
  /*
                           String name,
                           ActorAmp actor,
                           Supplier<ActorAmp> supplier,
                           ServiceConfig config)
                           */
  {
    Objects.requireNonNull(manager);
    Objects.requireNonNull(actorFactory);
    
    _manager = manager;
    _actorFactory = actorFactory;
    /*
    _supplier = supplier;
    _config = config;
    */

    if (config().isJournal()) {
      throw new IllegalStateException();
    }
    /*
      }
     */

    //_name = name;
    //_actor = actor;
  }

  @Override
  public String getName()
  {
    return _actorFactory.actorName();
  }
  
  public ServiceConfig config()
  {
    return _actorFactory.config();
  }

  @Override
  public ActorAmp getMainActor()
  {
    return _actorFactory.mainActor();
  }

  @Override
  public QueueService<MessageAmp> build(QueueServiceBuilder<MessageAmp> queueBuilder,
                                        InboxQueue inbox)
  {
    DeliverFactory<MessageAmp> factory
      = inbox.createDeliverFactory(_actorFactory, config());

    DisruptorBuilderQueue<MessageAmp> builder;
    builder = queueBuilder.disruptorBuilder(factory);

    if (config().isJournal()) {
      throw new IllegalStateException();
      /*
      String name = "test";

      builder.prologue(createJournalFactory(inbox, name));
      */
    }

    return builder.build();
  }

  /*
  private DeliverFactory<MessageAmp> 
  createJournalFactory(InboxQueue queue, String name)
  {
    ActorJournal journalActor = (ActorJournal) _actor;

    Supplier<ActorAmp> supplier;

    supplier = ()->journalActor;

    ServiceConfig.Builder builder = ServiceConfig.Builder.create();
    ServiceConfig config = builder.build();

    return queue.createDeliverFactory(supplier, _config);
  }
  */
}
