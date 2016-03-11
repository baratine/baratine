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

import java.util.function.Supplier;

/**
 * Outbox for the current worker thread.
 */
public interface Outbox extends AutoCloseable
{
  default boolean isEmpty()
  {
    return true;
  }

  /**
   * Adds the message to the outgoing queue.
   */
  void offer(MessageOutbox<?> msg);
  
  void flush();
  
  /**
   * Flushes pending messages, executing the last one in this thread, if
   * possible.
   * 
   * Unlike a normal flush, the outbox might not be empty when the call 
   * completes, because the last message might itself send messages. The
   * return false signals this case. 
   * 
   * @return false if there are messages left in the outbox.
   */
  default boolean flushAndExecuteLast()
  {
    flush();
    
    return false;
  }
  
  default void flushAndExecuteAll()
  {
    while (flushAndExecuteLast()) {
    }
  }
  
  //MessageOutbox<?> tailMessage();
  
  void open();
  
  void close();

  default Object context()
  {
    return null;
  }
  
  default Object getAndSetContext(Object context)
  {
    return null;
  }
  
  static Outbox current()
  {
    return (Outbox) OutboxProvider.getProvider().current();
  }
  
  static <O extends Outbox> O currentOrCreate(Supplier<O> supplier)
  {
    OutboxProvider<O> provider = OutboxProvider.getProvider();
    
    return provider.currentOrCreate(supplier);
  }
}
