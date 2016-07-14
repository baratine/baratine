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

package com.caucho.v5.amp.stub;

import java.util.ArrayList;
import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.StubStateAmp;
import com.caucho.v5.util.L10N;

import io.baratine.pipe.PipeSub;
import io.baratine.pipe.PipePub;
import io.baratine.service.Result;
import io.baratine.service.ResultChain;
import io.baratine.stream.ResultStream;

/**
 * State/dispatch for a loadable actor.
 */
public class StubStateAmpUnload implements StubStateAmp
{
  private static final L10N L = new L10N(StubStateAmpUnload.class);
  private static final Logger log
    = Logger.getLogger(StubStateAmpUnload.class.getSimpleName());
  
  public static final StubStateAmpUnload STATE = new StubStateAmpUnload();
  
  private StubStateAmpUnload()
  {
  }
  
  @Override
  public StubStateAmp load(StubAmp stub,
                           InboxAmp inbox,
                           MessageAmp msg)
  {
    return this;
  }

  @Override
  public void send(StubAmp stubDeliver,
                   StubAmp stubMessage,
                   MethodAmp method, 
                   HeadersAmp headers)
  {
    if (method.name().startsWith("create")) {
      StubAmp stub = stubDeliver.worker(stubMessage);
      
      stub.state(StubStateAmpBean.ACTIVE);
      stub.state().onModify(stub);
      
      stub.state().send(stubDeliver, stubMessage, method, headers);
    }
    else {
      logSendFail(stubMessage, method);
    }
  }

  @Override
  public void send(StubAmp stubDeliver,
                   StubAmp stubMessage,
                   MethodAmp method, 
                   HeadersAmp headers, 
                   Object arg0)
  {
    if (method.name().startsWith("create")) {
      StubAmp stub = stubDeliver.worker(stubMessage);
      
      stub.state(StubStateAmpBean.ACTIVE);
      stub.state().onModify(stub);
      
      stub.state().send(stubDeliver, stubMessage, method, headers,
                        arg0);
    }
    else {
      logSendFail(stubMessage, method);
    }
  }

  @Override
  public void send(StubAmp stubDeliver,
                   StubAmp stubMessage,
                   MethodAmp method, 
                   HeadersAmp headers, 
                   Object arg0,
                   Object arg1)
  {
    if (method.name().startsWith("create")) {
      StubAmp stub = stubDeliver.worker(stubMessage);
      
      stub.state(StubStateAmpBean.ACTIVE);
      stub.state().onModify(stub);
      
      stub.state().send(stubDeliver, stubMessage, method, headers,
                        arg0, arg1);
    }
    else {
      logSendFail(stubMessage, method);
    }
  }

  @Override
  public void send(StubAmp stubDeliver,
                   StubAmp stubMessage,
                   MethodAmp method, 
                   HeadersAmp headers, 
                   Object arg0,
                   Object arg1,
                   Object arg2)
  {
    if (method.name().startsWith("create")) {
      StubAmp stub = stubDeliver.worker(stubMessage);
      
      stub.state(StubStateAmpBean.ACTIVE);
      stub.state().onModify(stub);
      
      stub.state().send(stubDeliver, stubMessage, method, headers,
                        arg0, arg1, arg2);
    }
    else {
      logSendFail(stubMessage, method);
    }
  }

  @Override
  public void send(StubAmp stubDeliver,
                   StubAmp stubMessage,
                   MethodAmp method, 
                   HeadersAmp headers, 
                   Object []args)
  {
    if (method.name().startsWith("create")) {
      StubAmp stub = stubDeliver.worker(stubMessage);
      
      stub.state(StubStateAmpBean.ACTIVE);
      stub.state().onModify(stub);
      
      stub.state().send(stubDeliver, stubMessage, method, headers,
                        args);
    }
    else {
      logSendFail(stubMessage, method);
    }
  }
  
  @Override
  public void query(StubAmp stubDeliver,
                    StubAmp stubMessage,
                    MethodAmp method,
                    HeadersAmp headers,
                    Result<?> result)
  {
    if (method.name().startsWith("create")) {
      StubAmp stub = stubDeliver.worker(stubMessage);
      
      stub.state(StubStateAmpBean.ACTIVE);
      stub.state().onModify(stub);
      
      stub.state().query(stubDeliver, stubMessage, method, headers, result);
    }
    else {
      queryFail(stubMessage, method, result);
    }
  }

  @Override
  public void query(StubAmp stubDeliver,
                    StubAmp stubMessage,
                    MethodAmp method,
                    HeadersAmp headers,
                    Result<?> result,
                    Object arg0)
  {
    if (method.name().startsWith("create")) {
      StubAmp stub = stubDeliver.worker(stubMessage);
      
      stub.state(StubStateAmpBean.ACTIVE);
      stub.state().onModify(stub);
      
      stub.state().query(stubDeliver, stubMessage, method, headers, result,
                         arg0);
    }
    else {
      queryFail(stubMessage, method, result);
    }
  }
  
  @Override
  public void query(StubAmp stubDeliver,
                    StubAmp stubMessage,
                    MethodAmp method,
                    HeadersAmp headers,
                    Result<?> result,
                    Object arg0,
                    Object arg1)
  {
    if (method.name().startsWith("create")) {
      StubAmp stub = stubDeliver.worker(stubMessage);
      
      stub.state(StubStateAmpBean.ACTIVE);
      stub.state().onModify(stub);
      
      stub.state().query(stubDeliver, stubMessage, method, headers, result,
                         arg0, arg1);
    }
    else {
      queryFail(stubMessage, method, result);
    }
  }

  @Override
  public void query(StubAmp stubDeliver,
                    StubAmp stubMessage,
                    MethodAmp method,
                    HeadersAmp headers,
                    Result<?> result,
                    Object arg0,
                    Object arg1,
                    Object arg2)
  {
    if (method.name().startsWith("create")) {
      StubAmp stub = stubDeliver.worker(stubMessage);
      
      stub.state(StubStateAmpBean.ACTIVE);
      stub.state().onModify(stub);
      
      stub.state().query(stubDeliver, stubMessage, method, headers, result,
                         arg0, arg1, arg2);
    }
    else {
      queryFail(stubMessage, method, result);
    }
  }

  @Override
  public void query(StubAmp stubDeliver,
                     StubAmp stubMessage,
                     MethodAmp method,
                     HeadersAmp headers,
                     Result<?> result, 
                     Object[] args)
  {
    if (method.name().startsWith("create")) {
      StubAmp stub = stubDeliver.worker(stubMessage);
      
      stub.state(StubStateAmpBean.ACTIVE);
      stub.state().onModify(stub);
      
      stub.state().query(stubDeliver, stubMessage, method, headers, result,
                         args);
    }
    else {
      queryFail(stubMessage, method, result);
    }
  }

  @Override
  public void stream(StubAmp stubDeliver,
                      StubAmp stubMessage,
                      MethodAmp method,
                      HeadersAmp headers,
                      ResultStream<?> result, 
                      Object[] args)
  {
    //queryFail(stubMessage, method, result);
  }
  
  @Override
  public void outPipe(StubAmp stubDeliver,
                       StubAmp stubMessage,
                       MethodAmp method,
                       HeadersAmp headers,
                       PipePub<?> result, 
                       Object[] args)
  {
    queryFail(stubMessage, method, result);
  }

  @Override
  public void inPipe(StubAmp stubDeliver,
                       StubAmp stubMessage,
                       MethodAmp method,
                       HeadersAmp headers,
                       PipeSub<?> result, 
                       Object[] args)
  {
    queryFail(stubMessage, method, result);
  }

  @Override
  public void queryError(StubAmp stub,
                          HeadersAmp headers,
                          long qid,
                          Throwable exn)
  {
  }

  @Override
  public void streamCancel(StubAmp stubDeliver,
                            StubAmp stubMessage,
                            HeadersAmp headers, 
                            String addressFrom, 
                            long qid)
  {
  }

  @Override
  public void streamResult(StubAmp stubDeliver, 
                            StubAmp stubMessage,
                            HeadersAmp headers,
                            long qid,
                            int sequence,
                            ArrayList<Object> values,
                            Throwable exn,
                            boolean isComplete)
  {
  }
  
  private void logSendFail(StubAmp stub, MethodAmp method)
  {
    log.finer(L.l("{0} invalid because the bean {1} is not loaded"));
  }
  
  private void queryFail(StubAmp stub, MethodAmp method, ResultChain<?> result)
  {
    IllegalStateException exn 
      = new IllegalStateException(L.l("'{0}.{1}()' invalid because the bean {2} is not loaded",
                                      stub.bean().getClass().getSimpleName(),
                                      method.name(), 
                                      stub.bean()));
    
    result.fail(exn);
  }
}
