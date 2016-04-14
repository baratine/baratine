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

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.deliver.WorkerDeliver;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.QueryRefAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.StubAmp;

import io.baratine.service.Cancel;
import io.baratine.service.ResultChain;
import io.baratine.stream.ResultStream;

/**
 * Mailbox for an actor
 */
abstract public class InboxWrapper implements InboxAmp
{
  abstract protected InboxAmp delegate();
  
  @Override
  public ServicesAmp manager()
  {
    return delegate().manager();
  }
  
  @Override
  public String getAddress()
  {
    return delegate().getAddress();
  }
  
  @Override
  public boolean isLifecycleAware()
  {
    return delegate().isLifecycleAware();
  }

  @Override
  public boolean bind(String address)
  {
    return delegate().bind(address);
  }
  
  @Override
  public long getSize()
  {
    return delegate().getSize();
  }
  
  @Override
  public void offerAndWake(MessageAmp msg, long timeout)
  {
    delegate().offerAndWake(msg, timeout);
  }
  
  @Override
  public boolean offerResult(MessageAmp msg)
  {
    return delegate().offerResult(msg);
  }
  
  @Override
  public WorkerDeliver<MessageAmp> worker()
  {
    return delegate().worker();
  }
  
  /*
  @Override
  public void wake()
  {
    return delegate().wake();
  }
  */

  /**
   * Adds a query callback to handle a later message.
   *
   * @param id the unique query identifier
   * @param result the application's callback for the result
   */
  @Override
  public QueryRefAmp addQuery(String address,
                              ResultChain<?> result,
                              ClassLoader loader)
  {
    return delegate().addQuery(address, result, loader);
  }
  
  @Override
  public QueryRefAmp addQuery(String address, 
                              ResultStream<?> result,
                              ServiceRefAmp targetRef,
                              ClassLoader loader)
  {
    return delegate().addQuery(address, result, targetRef, loader);
  }
  
  @Override
  public QueryRefAmp addQuery(long qid,
                              ResultStream<Object> result,
                              Cancel cancel)
  {
    return delegate().addQuery(qid, result, cancel);
  }
  
  @Override
  public QueryRefAmp removeQueryRef(long id)
  {
    return delegate().removeQueryRef(id);
  }
  
  @Override
  public QueryRefAmp getQueryRef(long id)
  {
    return delegate().getQueryRef(id);
  }
  
  @Override
  public HeadersAmp createHeaders(HeadersAmp callerHeaders,
                                  ServiceRefAmp serviceRef,
                                  MethodAmp method)
  {
    return delegate().createHeaders(callerHeaders, serviceRef, method);
  }
  
  @Override
  public void init()
  {
    delegate().init();
  }
  
  @Override
  public void start()
  {
    delegate().start();
  }

  /**
   * Closes the mailbox
   */
  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
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

  /*
  @Override
  public MessageAmp getMessage()
  {
    return getDelegate().getMessage();
  }
  */
  
  @Override
  public MessageAmp getMessage()
  {
    return null;
  }

  @Override
  public ServiceRefAmp serviceRef()
  {
    return delegate().serviceRef();
  }

  @Override
  public boolean offer(MessageAmp message, long timeout)
  {
    return delegate().offer(message, timeout);
  }

  @Override
  public StubAmp stubDirect()
  {
    return delegate().stubDirect();
  }

  @Override
  public boolean isClosed()
  {
    return delegate().isClosed();
  }
}
