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

import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.inbox.OutboxAmpFactory;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.StubAmp;

import io.baratine.service.Result;

/**
 * Handles the context for an actor, primarily including its
 * query map.
 */
public class QueryWithResultMessage<T>
  extends QueryMessageBase<T>
{
  private static final Logger log 
    = Logger.getLogger(QueryWithResultMessage.class.getName());
  
  private final Result<T> _result;
  
  /*
  public QueryWithResultMessage(ServiceRefAmp serviceRef,
                                MethodAmp method,
                                long expires,
                                Result<T> result)
  {
    super(serviceRef, method, expires);
    
    Objects.requireNonNull(result);
    
    _result = result;
  }
  */
  
  public QueryWithResultMessage(OutboxAmp outboxCaller,
                                ServiceRefAmp serviceRef,
                                MethodAmp method,
                                long expires,
                                Result<T> result)
  {
    super(outboxCaller, serviceRef, method, expires);
    
    Objects.requireNonNull(result);
    
    _result = result;
  }
  
  public QueryWithResultMessage(OutboxAmp outboxCaller,
                                HeadersAmp headers,
                                ServiceRefAmp serviceRef,
                                MethodAmp method,
                                long expires,
                                Result<T> result)
  {
    super(outboxCaller, headers, serviceRef, method, expires);
    
    Objects.requireNonNull(result);
    
    _result = result;
  }
  
  /* baratine/11h0, baratine/119d */
  /* XXX: Issues with async/future that are chained */
  @Override
  public boolean isFuture()
  {
    Result<T> result = _result;
    
    return result.isFuture() && getMethod().isDirect();
  }
  
  @Override
  public <U> void completeFuture(Result<U> result, U value)
  {
    _result.completeFuture(result, value);
  }
  
  @Override
  public void completeFuture(T value)
  {
    //_result.completeAsync(value);
    
    //OutboxAmp outbox = getOutboxCaller();
    OutboxAmp outbox = OutboxAmp.current();
    boolean isNew = false;
    
    if (outbox == null) {
      outbox = OutboxAmpFactory.newFactory().get();
      outbox.message(this);
      // OutboxThreadLocal.setCurrent(outbox);
      isNew = true;
    }
    
    InboxAmp oldInbox = outbox.inbox();

    /*
    if (oldInbox == null && ! isNew) {
      System.err.println("OUTB: " + outbox);
      Thread.dumpStack();
    }
    */
    
    try {
      outbox.inbox(inboxCaller());
      
      _result.completeFuture(value);
    } finally {
      outbox.inbox(oldInbox);
      
      if (isNew) {
        outbox.close();
        // OutboxThreadLocal.setCurrent(null);
      }
    }
  }

  @Override
  protected void offerQuery(long timeout)
  {
    MethodAmp method = getMethod();
    
    if (method.isDirect() && serviceRef().stub().isStarted()) {
      try (OutboxAmp outbox = OutboxAmp.currentOrCreate(serviceRef().manager())) {
        offerDirect(outbox);
      }
    }
    else {
      super.offerQuery(timeout);
    }
  }
  
  private void offerDirect(OutboxAmp outbox)
  {
    outbox.flush();
  
    InboxAmp inbox = inboxTarget();
  
    Object oldContext = outbox.getAndSetContext(inbox);
    
    try {
      invoke(inbox, inbox.stubDirect());
    } finally {
      outbox.getAndSetContext(oldContext);
    }
  }
  
  private void offerDirectSystem()
  {
    //OutboxAmpBase outbox = new OutboxAmpBase();
    //outbox.setInbox(getInboxTarget());
    //outbox.setMessage(this);
    
    try {
      //OutboxThreadLocal.setCurrent(outbox);
      
      InboxAmp inbox = inboxTarget();
      
      invoke(inbox, inbox.stubDirect());
      
      //outbox.flush();
    } finally {
      //OutboxThreadLocal.setCurrent(null);
    }
  }
  
  @Override
  protected void offerResult(long timeout)
  {
    Result<T> result = _result;
    
    if (result.isFuture()) {
      sendReplyAsync(result);
    }
    else {
      super.offerResult(timeout);
    }
  }

  @Override
  protected boolean invokeOk(StubAmp stubDeliver)
  {
    return stubDeliver.ok(_result, (T) getReply());
  }
  
  @Override
  protected boolean invokeFail(StubAmp stubDeliver)
  {
    return stubDeliver.fail(_result, getException());
  }
  
  protected String getLocation()
  {
    return null;
  }

  @Override
  public String toString()
  {
    String toAddress = null;
    
    if (inboxTarget() != null && inboxTarget().serviceRef() != null) {
      toAddress = inboxTarget().serviceRef().address();
    }
    
    String callbackName = _result.getClass().getName();
    
    String loc = getLocation();
    
    if (loc != null) {
      loc = ",@" + loc;
    }
    else {
      loc = "";
    }
    
    return (getClass().getSimpleName()
        + "[" + getMethod().name()
        + ",to=" + toAddress
        + ",state=" + getState()
        + ",result=" + callbackName
        + loc
        + "]");
    
  }
  
}
