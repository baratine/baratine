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

package com.caucho.v5.amp.message;

import io.baratine.spi.Headers;

import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.inbox.OutboxAmpNull;
import com.caucho.v5.amp.queue.WorkerDeliver;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.MethodAmp;
import com.caucho.v5.amp.spi.OutboxAmp;

/**
 * Handle to an amp instance.
 */
abstract public class MethodMessageBase implements MessageAmp
{
  private static final Logger log
    = Logger.getLogger(MethodMessageBase.class.getName());
  
  private final InboxAmp _inboxTarget;
  private final OutboxAmp _outboxCaller;
  
  private final ServiceRefAmp _serviceRef;
  private final MethodAmp _method;
  private HeadersAmp _headers;
  
  /*
  public MethodMessageBase(ServiceRefAmp serviceRef,
                           MethodAmp method)
  {
    InboxAmp inboxTarget = serviceRef.getInbox();

    _inboxTarget = inboxTarget;
    
    OutboxAmp outbox = getOutboxCaller(inboxTarget);
    
    _outboxCaller = outbox;
    
    _serviceRef = serviceRef;
    _method = method;
    
    HeadersAmp headersCaller = outbox.getMessage().getHeaders();
    
    _headers = inboxTarget.createHeaders(headersCaller, serviceRef, method);
  }
  */
  
  public MethodMessageBase(OutboxAmp outboxCaller,
                           ServiceRefAmp serviceRef,
                           MethodAmp method)
  {
    InboxAmp inboxTarget = serviceRef.inbox();

    _inboxTarget = inboxTarget;
    
    _outboxCaller = outboxCaller;
    
    _serviceRef = serviceRef;
    _method = method;
    
    MessageAmp message = outboxCaller.getMessage();

    HeadersAmp headersCaller;
    
    if (message != null) {
      headersCaller = message.getHeaders();
    }
    else {
      headersCaller = HeadersNull.NULL;
    }
    
    _headers = inboxTarget.createHeaders(headersCaller, serviceRef, method);
  }
  
  /*
  public MethodMessageBase(Headers headersNew,
                       ServiceRefAmp serviceRef,
                       MethodAmp method)
  {
    InboxAmp inboxTarget = serviceRef.getInbox();
    
    _inboxTarget = inboxTarget;
    
    OutboxAmp outbox = getOutboxCaller(inboxTarget);
    
    _outboxCaller = outbox;
    
    _serviceRef = serviceRef;
    _method = method;
    
    HeadersAmp headers = outbox.getMessage().getHeaders();
    
    if (headersNew != null && headersNew.getSize() > 0) {
      headers = pushHeaders(headers, headersNew.iterator());
    }
    
    _headers = inboxTarget.createHeaders(headers, serviceRef, method);
  }
  */
  
  protected MethodMessageBase(OutboxAmp outboxCaller,
                              HeadersAmp headersCaller,
                              ServiceRefAmp serviceRef,
                              MethodAmp method)
  {
    _outboxCaller = outboxCaller;
    _inboxTarget = serviceRef.inbox();
    
    _serviceRef = serviceRef;
    _method = method;

    //_headers = _inboxTarget.createHeaders(headersCaller, serviceRef, method);
    _headers = headersCaller;
  }
  
  /**
   * Returns the calling outbox from the current context, if available.
   * 
   * The outbox is associated with the current thread and is used to 
   * batch and deliver messages.
   */
  /*
  private static OutboxAmp getOutboxCaller(InboxAmp inboxTarget)
  {
    OutboxAmp outbox = OutboxAmp.current();
    
    if (outbox != null) {
      // OutboxAmp outbox = (OutboxAmp) outboxDeliver;
    
      return outbox;
    }
    else {
      return inboxTarget.getManager().getSystemOutbox(); 
    }
  }
  */
  
  /**
   * Returns the calling outbox from the current context, if available.
   * 
   * The outbox is associated with the current thread and is used to 
   * batch and deliver messages.
   */
  public static OutboxAmp getOutboxCurrent()
  {
    OutboxAmp outbox = OutboxAmp.current();
    
    if (outbox != null) {
      // OutboxAmp outbox = (OutboxAmp) outboxDeliver;
    
      return outbox;
    }
    else {
      return OutboxAmpNull.NULL;
    }
  }
  
  protected HeadersAmp pushHeaders(HeadersAmp headers, 
                                    Iterator<Map.Entry<String,Object>> iter)
  {
    if (! iter.hasNext()) {
      return headers;
    }
    
    Map.Entry<String,Object> entry = iter.next();
    
    headers = pushHeaders(headers, iter);
    
    headers = headers.add(entry.getKey(), entry.getValue());

    return headers;
  }

  @Override
  public HeadersAmp getHeaders()
  {
    return _headers;
  }
  
  protected final ServiceRefAmp serviceRef()
  {
    return _serviceRef;
  }
  
  protected final MethodAmp getMethod()
  {
    return _method;
  }
  
  @Override
  public Type getType()
  {
    return Type.UNKNOWN;
  }
  
  @Override
  public final InboxAmp inboxTarget()
  {
    return _inboxTarget;
  }
  
  @Override
  public final OutboxAmp getOutboxCaller()
  {
    return _outboxCaller;
  }
  
  protected final HeadersAmp getHeadersCaller()
  {
    // return getOutboxCaller().getMessage().getHeaders();
    return _headers;
  }

  // XXX: change to final if possible
  @Override
  public void offer(long timeout)
  {
    OutboxAmp outbox = getOutboxCaller();
    
    // XXX: header
    
    outbox.offer(this);
  }

  @Override
  public void offerQueue(long timeout)
  {
    inboxTarget().offer(this, timeout);
  }
  
  @Override
  public WorkerDeliver<MessageAmp> getWorker()
  {
    return inboxTarget().getWorker();
  }

  @Override
  public void fail(Throwable exn)
  {
    log.log(Level.FINE, exn.toString(), exn);
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + _method.name()
            + "," + _serviceRef.address() + "]");
  }
}
