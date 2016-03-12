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

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.deliver.Deliver;
import com.caucho.v5.amp.manager.ServiceConfig;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.MessageAmp;

/**
 * worker factory for the queue inbox.
 */
public class DeliverInboxFactory implements Supplier<Deliver<MessageAmp>>
{
  private static final Logger log 
    = Logger.getLogger(DeliverInboxFactory.class.getName());
  
  private final InboxQueue _inbox;
  private final Supplier<ActorAmp> _supplier;
  private final ServiceConfig _config;
  private DeliverInboxState _stateMultiWorker;

  DeliverInboxFactory(InboxQueue inbox,
                      Supplier<ActorAmp> supplierActor,
                      ServiceConfig config)
  {
    _inbox = inbox;
    _supplier = supplierActor;
    _config = config;
    
    if (workers() > 1) {
      _stateMultiWorker = new DeliverInboxState();
    }
  }

  @Override
  public Deliver<MessageAmp> get()
  {
    ActorAmp actor = _supplier.get();
    
    boolean isDebug = _inbox.manager().isDebug() || log.isLoggable(Level.FINE);

    if (workers() > 1) {
      return new DeliverInboxMultiWorker(_inbox, actor, _stateMultiWorker);
    }
    else if (isDebug) {
      return new DeliverInboxDebug(_inbox, actor);
    }
    else {
      return new DeliverInbox(_inbox, actor);
    }
  }

  private int workers()
  {
    if (_config != null) {
      return _config.workers();
    }
    else {
      return 1;
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _supplier + "]";
  }
}
