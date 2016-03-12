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

package com.caucho.v5.amp.inbox;

import java.util.function.Supplier;

import com.caucho.v5.amp.deliver.OutboxProvider;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.thread.ThreadAmp;

/**
 * Supplier of outboxes for the system
 */
public class OutboxProviderAmp
  extends OutboxProvider<OutboxAmp>
{
  private static ThreadLocal<OutboxAmp> _outboxLocal = new ThreadLocal<>();
  
  public static OutboxProviderAmp getProvider()
  {
    OutboxProvider<OutboxAmp> provider = OutboxProvider.getProvider();
    
    return (OutboxProviderAmp) provider; 
  }

  @Override
  public OutboxAmp get()
  {
    return new OutboxAmpThread();
  }
  
  @Override
  public OutboxAmp current()
  {
    return currentAmp();
  }
  
  public static OutboxAmp currentAmp()
  {
    Thread thread = Thread.currentThread();
    
    if (thread instanceof ThreadAmp) {
      ThreadAmp threadAmp = (ThreadAmp) thread;
      
      return (OutboxAmp) threadAmp.outbox();
    }
    
    return _outboxLocal.get();
  }
  
  /*
  @Override
  public OutboxAmp currentOrCreate()
  {
    OutboxAmp outbox = current();
    
    if (outbox != null) {
      outbox.open();

      return outbox;
    }
    else {
      return new OutboxAmpImpl(this);
    }
  }
  */
  
  @Override
  public OutboxAmp currentOrCreate(Supplier<OutboxAmp> supplier)
  {
    return currentOrCreateAmp(supplier);
  }
  
  public static OutboxAmp 
  currentOrCreateAmp(Supplier<OutboxAmp> supplier)
  {
    OutboxAmp outbox = currentAmp();
    
    if (outbox != null) {
      outbox.open();
      
      return outbox;
    }
    else {
      outbox = supplier.get();
      
      return outbox;
    }
  }

  public void current(OutboxAmp outboxAmp)
  {
    _outboxLocal.set(outboxAmp);
  }
}
