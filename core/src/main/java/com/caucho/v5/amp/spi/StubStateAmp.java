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

import java.util.ArrayList;

import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.SaveResult;
import com.caucho.v5.amp.stub.StubAmp;

import io.baratine.pipe.ResultPipeIn;
import io.baratine.pipe.ResultPipeOut;
import io.baratine.service.Result;
import io.baratine.stream.ResultStream;


/**
 * State/dispatch for a stub
 */
public interface StubStateAmp
{
  //
  // state transitions
  //

  default boolean isActive()
  {
    return true;
  }

  default boolean isModified()
  {
    return false;
  }

  default boolean isDelete()
  {
    return false;
  }
  
  StubStateAmp load(StubAmp stub,
                    InboxAmp inbox,
                    MessageAmp msg);
  
  /**
   * Journal replay.
   */
  default StubStateAmp loadReplay(StubAmp stub,
                               InboxAmp inbox,
                               MessageAmp msg)
  {
    throw new IllegalStateException(this + " " + stub + " " + msg);
  }

  default void onActive(StubAmp stub, InboxAmp inbox)
  {
  }
  
  /**
   * After a slow init or load, resend the pending messages that were waiting
   * for the init or load to complete.
   */
  default void flushPending(StubAmp stub, InboxAmp inbox)
  {
  }
  
  /**
   * Mark the stub as create.
   */
  default void onCreate(StubAmp stub)
  {
  }
  
  /**
   * Mark the stub as modified.
   */
  default void onModify(StubAmp stub)
  {
  }
  
  /**
   * Mark the stub as deleted.
   */
  default void onDelete(StubAmp stub)
  {
  }

  default void onSave(StubAmp stub, SaveResult saveResult)
  {
  }

  default boolean onSave(StubAmp stub)
  {
    return false;
  }

  default void onSaveComplete(StubAmp stub)
  {
  }


  default void beforeBatch(StubAmp stub)
  {
    stub.beforeBatchImpl();
  }
  

  default void afterBatch(StubAmp stub)
  {
    stub.afterBatchImpl();
  }

  default void shutdown(StubAmp stub, ShutdownModeAmp mode)
  {
    stub.onShutdown(mode);
  }

  /**
   * Used by journal to return a null stub so the invocation doesn't
   * occur twice. 
   */
  default StubAmp stub(StubAmp stub)
  {
    return stub;
  }
  
  //
  // method calls
  //

  default void send(StubAmp stubDeliver,
                    StubAmp stubMessage,
                    MethodAmp method, 
                    HeadersAmp headers)
  {
    method.send(headers, stubDeliver.worker(stubMessage));
  }

  default void send(StubAmp stubDeliver,
                    StubAmp stubMessage,
                    MethodAmp method, 
                    HeadersAmp headers, 
                    Object arg0)
  {
    method.send(headers, stubDeliver.worker(stubMessage), arg0);
  }

  default void send(StubAmp stubDeliver,
                    StubAmp stubMessage,
                    MethodAmp method, 
                    HeadersAmp headers, 
                    Object arg0,
                    Object arg1)
  {
    method.send(headers, stubDeliver.worker(stubMessage), arg0, arg1);
  }

  default void send(StubAmp stubDeliver,
                    StubAmp stubMessage,
                    MethodAmp method, 
                    HeadersAmp headers, 
                    Object arg0,
                    Object arg1,
                    Object arg2)
  {
    method.send(headers, stubDeliver.worker(stubMessage), arg0, arg1, arg2);
  }

  default void send(StubAmp stubDeliver,
                    StubAmp stubMessage,
                    MethodAmp method, 
                    HeadersAmp headers, 
                    Object []args)
  {
    method.send(headers, stubDeliver.worker(stubMessage), args);
  }
  
  default void query(StubAmp stubDeliver,
                     StubAmp stubMessage,
                     MethodAmp method,
                     HeadersAmp headers,
                     Result<?> result)
  {
    method.query(headers, result, 
                 stubDeliver.worker(stubMessage));
  }
  
  default void query(StubAmp stubDeliver,
                     StubAmp stubMessage,
                     MethodAmp method,
                     HeadersAmp headers,
                     Result<?> result,
                     Object arg0)
  {
    method.query(headers, result, 
                 stubDeliver.worker(stubMessage),
                 arg0);
  }
  
  default void query(StubAmp stubDeliver,
                     StubAmp stubMessage,
                     MethodAmp method,
                     HeadersAmp headers,
                     Result<?> result,
                     Object arg0,
                     Object arg1)
  {
    method.query(headers, result, 
                 stubDeliver.worker(stubMessage),
                 arg0, arg1);
  }
  
  default void query(StubAmp stubDeliver,
                     StubAmp stubMessage,
                     MethodAmp method,
                     HeadersAmp headers,
                     Result<?> result,
                     Object arg0,
                     Object arg1,
                     Object arg2)
  {
    method.query(headers, result, 
                 stubDeliver.worker(stubMessage),
                 arg0, arg1, arg2);
  }
  
  default void query(StubAmp stubDeliver,
                     StubAmp stubMessage,
                     MethodAmp method,
                     HeadersAmp headers,
                     Result<?> result, 
                     Object[] args)
  {
    method.query(headers, result, 
                 stubDeliver.worker(stubMessage), 
                 args);
  }
  
  default void stream(StubAmp stubDeliver,
                      StubAmp stubMessage,
                      MethodAmp method,
                      HeadersAmp headers,
                      ResultStream<?> result, 
                      Object[] args)
  {
    method.stream(headers, result, 
                  stubDeliver.worker(stubMessage), 
                  args);
  }
  
  default void outPipe(StubAmp stubDeliver,
                       StubAmp stubMessage,
                       MethodAmp method,
                       HeadersAmp headers,
                       ResultPipeOut<?> result, 
                       Object[] args)
  {
    method.outPipe(headers, result, 
                   stubDeliver.worker(stubMessage), 
                   args);
  }
  
  default void inPipe(StubAmp stubDeliver,
                       StubAmp stubMessage,
                       MethodAmp method,
                       HeadersAmp headers,
                       ResultPipeIn<?> result, 
                       Object[] args)
  {
    method.inPipe(headers, result, 
                   stubDeliver.worker(stubMessage), 
                   args);
  }
  
  default void queryError(StubAmp stub,
                          HeadersAmp headers,
                          long qid,
                          Throwable exn)
  {
    StubAmp queryStub = stub(stub);
    
    queryStub.queryError(headers, queryStub, qid, exn);
  }

  default void streamCancel(StubAmp stubDeliver,
                            StubAmp stubMessage,
                            HeadersAmp headers, 
                            String addressFrom, 
                            long qid)
  {
    StubAmp queryActor = stubDeliver.worker(stubMessage);
    
    queryActor.streamCancel(headers, stubMessage, addressFrom, qid);
  }

  default void streamResult(StubAmp stubDeliver, 
                            StubAmp stubMessage,
                            HeadersAmp headers,
                            long qid,
                            int sequence,
                            ArrayList<Object> values,
                            Throwable exn,
                            boolean isComplete)
  {
    StubAmp queryActor = stubDeliver.worker(stubMessage);
    
    queryActor.streamReply(headers, stubMessage, qid, sequence,
                           values, exn, isComplete);
  }
}
