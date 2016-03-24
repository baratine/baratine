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
import com.caucho.v5.amp.pipe.PipeImpl;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.LoadState;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.util.L10N;

import io.baratine.pipe.PipeIn;
import io.baratine.pipe.PipeOut;
import io.baratine.pipe.ResultPipeOut;

/**
 * Register a publisher to a pipe.
 */
public class PipeOutMessage<T>
  extends QueryMessageBase<Void>
  implements ResultPipeOut<T>
{
  private static final L10N L = new L10N(PipeOutMessage.class);
  private static final Logger log 
    = Logger.getLogger(PipeOutMessage.class.getName());
  
  private final ResultPipeOut<T> _result;

  private Object[] _args;

  private InboxAmp _callerInbox;
  
  private PipeImpl<T> _pipe;
  
  public PipeOutMessage(OutboxAmp outbox,
                        InboxAmp callerInbox,
                        HeadersAmp headers,
                        ServiceRefAmp serviceRef,
                        MethodAmp method,
                        ResultPipeOut<T> result,
                        long expires,
                        Object []args)
  {
    //super(outbox, headers, serviceRef, method);
    super(outbox, serviceRef, method, expires);
    
    Objects.requireNonNull(result);
    
    _result = result;
    _args = args;
    
    Objects.requireNonNull(callerInbox);
    
    _callerInbox = callerInbox;
  }
  
  private InboxAmp getCallerInbox()
  {
    return _callerInbox;
  }

  @Override
  public final void invokeQuery(InboxAmp inbox, StubAmp actorDeliver)
  {
    try {
      MethodAmp method = getMethod();
    
      StubAmp actorMessage = serviceRef().getActor();

      LoadState load = actorDeliver.load(actorMessage, this);
      
      load.outPipe(actorDeliver, actorMessage,
                  method,
                  getHeaders(),
                  this,
                  _args);
      
    } catch (Throwable e) {
      fail(e);
    }
  }

  //@Override
  public void failQ(Throwable exn)
  {
    _result.fail(exn);
  }

  @Override
  public void ok(Void value)
  {
    throw new IllegalStateException(getClass().getSimpleName());
  }

  @Override
  public void ok(PipeIn<T> inPipe)
  {
    if (inPipe == null) {
      _result.fail(new NullPointerException(L.l("NPE from service {0}", getMethod())));
      Objects.requireNonNull(inPipe);
    }
    
    ServiceRefAmp inRef = inboxTarget().serviceRef();
    
    ServiceRefAmp outRef = inboxCaller().serviceRef();
    PipeOut.Flow<T> outFlow = _result.flow();
    
    PipeImpl<T> pipe = new PipeImpl<>(inRef, inPipe, outRef, outFlow);
    
    _pipe = pipe;
    
    super.ok((Void) null);
  }

  @Override
  protected boolean invokeOk(StubAmp actorDeliver)
  {
    _result.flow().ready(_pipe);
    
    return true;
  }
  
  @Override
  protected boolean invokeFail(StubAmp actorDeliver)
  {
    //System.out.println("Missing Fail:" + this);
    _result.fail(getException());

    return true;
  }

  /*
  @Override
  public void handle(OutPipe<T> pipe, Throwable exn) throws Exception
  {
    throw new IllegalStateException(getClass().getName());
  }
  */
  

  @Override
  public String toString()
  {
    String toAddress = null;
    
    if (inboxTarget() != null && inboxTarget().serviceRef() != null) {
      toAddress = inboxTarget().serviceRef().address();
    }
    
    String callbackName = null;
    
    if (_result != null) {
      callbackName = _result.getClass().getName();
    }
    
    return (getClass().getSimpleName()
        + "[" + getMethod().name()
        + ",to=" + toAddress
        + ",result=" + callbackName
        + "]");
    
  }

  @Override
  public void handle(PipeOut<T> pipe, Throwable exn)
  {
    throw new UnsupportedOperationException();
  }
}
