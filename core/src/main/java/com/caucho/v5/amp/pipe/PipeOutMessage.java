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

package com.caucho.v5.amp.pipe;

import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.message.QueryWithResultMessage;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.LoadStateAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.util.L10N;

import io.baratine.pipe.Pipe;
import io.baratine.pipe.ResultPipeOut;

/**
 * Register a publisher to a pipe.
 */
public class PipeOutMessage<T>
  extends QueryWithResultMessage<Pipe<T>>
  implements ResultPipeOut<T>
{
  private static final L10N L = new L10N(PipeOutMessage.class);
  private static final Logger log 
    = Logger.getLogger(PipeOutMessage.class.getName());
  
  private final ResultPipeOut<T> _result;

  private Object[] _args;
  
  private PipeImpl<T> _pipe;
  private int _capacity;
  private int _prefetch = Pipe.PREFETCH_DEFAULT;
  private long _credits = Pipe.CREDIT_DISABLE;
  
  public PipeOutMessage(OutboxAmp outbox,
                        HeadersAmp headers,
                        ServiceRefAmp serviceRef,
                        MethodAmp method,
                        ResultPipeOut<T> result,
                        long expires,
                        Object []args)
  {
    //super(outbox, headers, serviceRef, method);
    super(outbox, serviceRef, method, expires, result);
    
    Objects.requireNonNull(result);
    
    _result = result;
    
    _args = args;
  }

  @Override
  public final void invokeQuery(InboxAmp inbox, StubAmp stubDeliver)
  {
    try {
      MethodAmp method = method();
    
      StubAmp stubMessage = serviceRef().stub();

      LoadStateAmp load = stubDeliver.load(stubMessage, this);
      
      load.outPipe(stubDeliver, stubMessage,
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
  
  private int capacity()
  {
    return _capacity;
  }
  
  @Override
  public void capacity(int capacity)
  {
    _capacity = capacity;
  }
  
  private int prefetch()
  {
    return _prefetch;
  }
  
  @Override
  public ResultPipeOut<T> prefetch(int prefetch)
  {
    _prefetch = prefetch;
    
    return this;
  }
  
  private long credits()
  {
    return _credits;
  }
  
  @Override
  public ResultPipeOut<T> credits(long credits)
  {
    _credits = credits;
    
    return this;
  }

  @Override
  public void ok(Pipe<T> pipeIn)
  {
    Objects.requireNonNull(pipeIn);

    PipeBuilder<T> builder = new PipeBuilder<>();
    
    builder.inPipe(pipeIn);
    builder.inRef(inboxTarget().serviceRef());
    builder.outRef(inboxCaller().serviceRef());

    builder.capacity(capacity());
    builder.credits(credits());
    builder.prefetch(prefetch());
    
    PipeImpl<T> pipe = builder.build();
    
    _pipe = pipe;
    
    super.ok(pipe);
  }

  @Override
  protected boolean invokeOk(StubAmp stubDeliver)
  {
    _result.ok(_pipe);
    
    return true;
  }
  
  @Override
  protected boolean invokeFail(StubAmp actorDeliver)
  {
    fail().printStackTrace();;
    _result.fail(fail());

    return true;
  }

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
        + "[" + method().name()
        + ",to=" + toAddress
        + ",result=" + callbackName
        + "]");
    
  }
}
