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

import java.util.concurrent.atomic.AtomicReference;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.deliver.WorkerDeliver;
import com.caucho.v5.amp.message.OnInitMessage;
import com.caucho.v5.amp.message.QueryMap;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.QueryRefAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.stub.MethodAmp;

import io.baratine.service.Cancel;
import io.baratine.service.ResultChain;
import io.baratine.stream.ResultStream;

/**
 * Mailbox for an actor
 */
abstract public class InboxBase 
  implements InboxAmp
{
  private final ServicesAmp _manager;
  
  // private final AtomicLong _qId = new AtomicLong();

  private final AtomicReference<QueryMap> _queryMapRef = new AtomicReference<>();
  
  protected InboxBase(ServicesAmp manager)
  {
    _manager = manager;
  }
  
  @Override
  public final ServicesAmp manager()
  {
    return _manager;
  }
  
  @Override
  public String getAddress()
  {
    return "actor:" + serviceRef().stub();
  }
  
  @Override
  public boolean isLifecycleAware()
  {
    return false;
  }

  @Override
  public boolean bind(String address)
  {
    return false;
  }
  
  @Override
  public long getSize()
  {
    return 0;
  }
  
  @Override
  public void offerAndWake(MessageAmp msg, long timeout)
  {
    offer(msg, timeout);
  }
  
  @Override
  public boolean offerResult(MessageAmp msg)
  {
    return offer(msg, InboxAmp.TIMEOUT_INFINITY);
  }
  
  @Override
  public boolean isEmpty()
  {
    return getSize() == 0;
  }
  
  @Override
  public WorkerDeliver<MessageAmp> worker()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Adds a query callback to handle a later message.
   *
   * @param id the unique query identifier
   * @param result the application's callback for the result
   * @param loader the caller's class loader
   */
  @Override
  public QueryRefAmp addQuery(String address, 
                              ResultChain<?> result,
                              ClassLoader loaderCaller)
  {
    return queryMap().addQuery(address, result, loaderCaller);
  }
  
  @Override
  public QueryRefAmp addQuery(String address, 
                              ResultStream<?> result,
                              ServiceRefAmp targetRef,
                              ClassLoader loaderCaller)
  {
    return queryMap().addQuery(address, result, targetRef, loaderCaller);
  }
  
  @Override
  public QueryRefAmp addQuery(long qid, 
                              ResultStream<Object> result,
                              Cancel cancel)
  {
    return queryMap().addQuery(qid, result, cancel);
  }
  
  @Override
  public QueryRefAmp removeQueryRef(long id)
  {
    return queryMap().extractQuery(id);
  }
  
  @Override
  public QueryRefAmp getQueryRef(long id)
  {
    return queryMap().getQuery(id);
  }
  
  @Override
  public HeadersAmp createHeaders(HeadersAmp callerHeaders,
                                  ServiceRefAmp serviceRef,
                                  MethodAmp method)
  {
    return callerHeaders;
  }
  
  @Override
  public void init()
  {
    offer(new OnInitMessage(this), InboxAmp.TIMEOUT_INFINITY);
  }
  
  @Override
  public void start()
  {
    offer(new OnInitMessage(this), InboxAmp.TIMEOUT_INFINITY);
  }
  
  protected boolean isSingle()
  {
    return true;
  }
  
  private QueryMap queryMap()
  {
    QueryMap queryMap = _queryMapRef.get();
    
    if (queryMap == null) {
      queryMap = new QueryMap(this);
      _queryMapRef.compareAndSet(null, queryMap);
      
      queryMap = _queryMapRef.get();
    }
    
    return queryMap;
  }

  /**
   * Closes the mailbox
   */
  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
    QueryMap queryMap = _queryMapRef.get();
    
    if (queryMap != null) {
      queryMap.close();
    }
  }
  
  @Override
  public void onActive()
  {
  }
  
  @Override
  public void onInit()
  {
  }
  
  @Override
  public void shutdownStubs(ShutdownModeAmp mode)
  {
  }
}
