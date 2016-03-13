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

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.OutboxAmp;

/**
 * Outbox for contextual executors
 */
public class OutboxAmpExecutorFactory implements Supplier<OutboxAmp>
{
  private Supplier<Executor> _factory;
  private InboxAmp _systemInbox;
  private ServiceManagerAmp _manager;
  
  private CachedExecutorInbox _lastInbox;
  
  public OutboxAmpExecutorFactory(ServiceManagerAmp manager,
                                  Supplier<Executor> factory, 
                                  InboxAmp systemInbox)
  {
    Objects.requireNonNull(manager);
    Objects.requireNonNull(factory);
    Objects.requireNonNull(systemInbox);
    
    _manager = manager;
    _factory = factory;
    _systemInbox = systemInbox;
  }
  
  @Override
  public OutboxAmp get()
  {
    OutboxAmpImpl outbox = new OutboxAmpImpl();
    
    outbox.inbox(getInbox());
    
    return outbox;
  }
  
  private InboxAmp getInbox()
  {
    /*
    InboxAmp inbox = super.getInbox();
    
    if (inbox != null) {
      return inbox;
    }
    */
    
    InboxAmp inbox; //  = super.getInbox();
    
    Executor executor = _factory.get();
    
    if (executor != null) {
      CachedExecutorInbox lastInbox = _lastInbox;
      
      if (lastInbox != null) {
        inbox = lastInbox.get(executor);
        
        if (inbox != null) {
          return inbox;
        }
      }
        
      inbox = new InboxExecutor(_manager, "system:", executor);
      
      _lastInbox = new CachedExecutorInbox(executor, inbox);
      
      return inbox;
    }
    
    return _systemInbox;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _factory + "]";
  }
  
  private static class CachedExecutorInbox {
    private final Executor _executor;
    private final InboxAmp _inbox;
    
    CachedExecutorInbox(Executor executor, InboxAmp inbox)
    {
      _executor = executor;
      _inbox = inbox;
    }
    
    InboxAmp get(Executor executor)
    {
      if (_executor == executor) {
        return _inbox;
      }
      else {
        return null;
      }
    }
  }
}
