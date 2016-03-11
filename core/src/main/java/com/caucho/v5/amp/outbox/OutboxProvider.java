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

import java.util.Objects;
import java.util.function.Supplier;

import com.caucho.v5.amp.inbox.OutboxProviderAmp;

/**
 * Outbox for the current worker thread.
 */
abstract public class OutboxProvider<O extends Outbox> implements Supplier<O>
{
  private static OutboxProvider<?> _provider;
  
  public static void setProvider(OutboxProvider<?> provider)
  {
    Objects.requireNonNull(provider);
    
    _provider = provider;
  }
  
  public static <O extends Outbox> OutboxProvider<O> getProvider()
  {
    return (OutboxProvider) _provider;
  }
  
  abstract public O current();
  
  /*
  public Outbox<M> currentOrCreate()
  {
    Outbox<M> outbox = current();
    
    if (outbox != null) {
      // XXX: issues with updating count;
      return outbox;
    }
    else {
      return get();
    }
  }
  */
  
  abstract public O currentOrCreate(Supplier<O> supplier);
  
  static {
    try {
      setProvider(new OutboxProviderAmp());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
