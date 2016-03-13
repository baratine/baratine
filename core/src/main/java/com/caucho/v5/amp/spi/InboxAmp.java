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

package com.caucho.v5.amp.spi;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.deliver.WorkerDeliver;

import io.baratine.service.Cancel;
import io.baratine.service.Result;
import io.baratine.stream.ResultStream;

/**
 * Mailbox for an actor
 */
public interface InboxAmp // extends OutboxContext<MessageAmp>
{
  public static final long TIMEOUT_DEFAULT = 60000L;
  public static final long TIMEOUT_INFINITY = Integer.MAX_VALUE;
  
  //
  // information/reflection
  //
  
  ServiceManagerAmp manager();
  
  String getAddress();
  
  ServiceRefAmp serviceRef();
  
  boolean bind(String address);

  //
  // queue operations
  //
  
  default boolean isEmpty()
  {
    return getSize() == 0;
  }
  
  long getSize();
  
  boolean offer(MessageAmp message, long timeout);

  void offerAndWake(MessageAmp message, long timeout);
  
  boolean offerResult(MessageAmp message);
  
  //
  // support operations
  //
  
  ActorAmp getDirectActor();
  
  WorkerDeliver<MessageAmp> worker();

  //void wake();
  
  MessageAmp getMessage();

  HeadersAmp createHeaders(HeadersAmp callerHeaders,
                           ServiceRefAmp serviceRef,
                           MethodAmp method);

  QueryRefAmp addQuery(String address, 
                       Result<?> result, 
                       ClassLoader loader);
  
  QueryRefAmp addQuery(String address, 
                       ResultStream<?> result, 
                       ServiceRefAmp targetRef,
                       ClassLoader loader);

  QueryRefAmp addQuery(long qid, 
                       ResultStream<Object> result,
                       Cancel cancel);

  QueryRefAmp removeQueryRef(long id);
  QueryRefAmp getQueryRef(long id);
  
  //
  // lifecycle
  //

  void init();
  void start();

  boolean isLifecycleAware();
  
  void onActive();
  void onInit();
  
  boolean isClosed();
  
  void shutdown(ShutdownModeAmp mode);

  void shutdownActors(ShutdownModeAmp mode);
}
