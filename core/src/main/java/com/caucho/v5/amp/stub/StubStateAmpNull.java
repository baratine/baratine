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

import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.spi.StubStateAmp;

import io.baratine.pipe.ResultPipeIn;
import io.baratine.pipe.ResultPipeOut;
import io.baratine.service.Result;
import io.baratine.stream.ResultStream;


/**
 * State/dispatch for a loadable actor.
 */
public class StubStateAmpNull implements StubStateAmp
{
  public static final StubStateAmpNull STATE = new StubStateAmpNull();
  
  public StubStateAmpNull()
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
  }

  @Override
  public void send(StubAmp stubDeliver,
                   StubAmp stubMessage,
                   MethodAmp method, 
                   HeadersAmp headers, 
                   Object arg0)
  {
  }

  @Override
  public void send(StubAmp stubDeliver,
                   StubAmp stubMessage,
                   MethodAmp method, 
                   HeadersAmp headers, 
                   Object arg0,
                   Object arg1)
  {
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
  }

  @Override
  public void send(StubAmp stubDeliver,
                   StubAmp stubMessage,
                   MethodAmp method, 
                   HeadersAmp headers, 
                   Object []args)
  {
  }
  
  @Override
  public void query(StubAmp stubDeliver,
                    StubAmp stubMessage,
                    MethodAmp method,
                    HeadersAmp headers,
                    Result<?> result)
  {
  }

  @Override
  public void query(StubAmp stubDeliver,
                    StubAmp stubMessage,
                    MethodAmp method,
                    HeadersAmp headers,
                    Result<?> result,
                    Object arg0)
  {
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
  }

  @Override
  public void query(StubAmp stubDeliver,
                     StubAmp stubMessage,
                     MethodAmp method,
                     HeadersAmp headers,
                     Result<?> result, 
                     Object[] args)
  {
  }

  @Override
  public void stream(StubAmp stubDeliver,
                      StubAmp stubMessage,
                      MethodAmp method,
                      HeadersAmp headers,
                      ResultStream<?> result, 
                      Object[] args)
  {
  }
  
  @Override
  public void outPipe(StubAmp stubDeliver,
                       StubAmp stubMessage,
                       MethodAmp method,
                       HeadersAmp headers,
                       ResultPipeOut<?> result, 
                       Object[] args)
  {
  }

  @Override
  public void inPipe(StubAmp stubDeliver,
                       StubAmp stubMessage,
                       MethodAmp method,
                       HeadersAmp headers,
                       ResultPipeIn<?> result, 
                       Object[] args)
  {
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

  @Override
  public void flushPending(StubAmp stubBean, InboxAmp inbox)
  {
  }

  @Override
  public boolean onSave(StubAmp stub)
  {
    return false;
  }

  @Override
  public void beforeBatch(StubAmp stub)
  {
  }
  

  @Override
  public void afterBatch(StubAmp stub)
  {
  }

  @Override
  public void shutdown(StubAmp stub, ShutdownModeAmp mode)
  {
    stub.onShutdown(mode);
  }
}
