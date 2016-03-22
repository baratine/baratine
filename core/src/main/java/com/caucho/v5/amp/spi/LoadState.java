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

import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.SaveResult;

import io.baratine.io.ResultPipeIn;
import io.baratine.io.ResultPipeOut;
import io.baratine.service.Result;
import io.baratine.service.ServiceRef;
import io.baratine.stream.ResultStream;


/**
 * State/dispatch for a loadable actor.
 */
public interface LoadState
{
  LoadState load(StubAmp actor,
                 InboxAmp inbox,
                 MessageAmp msg);
  
  default LoadState loadReplay(StubAmp actor,
                               InboxAmp inbox,
                               MessageAmp msg)
  {
    throw new IllegalStateException(this + " " + actor + " " + msg);
  }

  void onModify(StubAmp actorAmpBase);

  default void onSave(StubAmp actor, SaveResult saveResult)
  {
  }
  
  default StubAmp getActor(StubAmp actor)
  {
    return actor;
  }

  default void onActive(StubAmp actor, InboxAmp inbox)
  {
  }

  default void send(StubAmp actorDeliver,
                    StubAmp actorMessage,
                    MethodAmp method, 
                    HeadersAmp headers)
  {
    method.send(headers, actorDeliver.worker(actorMessage));
  }

  default void send(StubAmp actorDeliver,
                    StubAmp actorMessage,
                    MethodAmp method, 
                    HeadersAmp headers, 
                    Object arg0)
  {
    method.send(headers, actorDeliver.worker(actorMessage), arg0);
  }

  default void send(StubAmp actorDeliver,
                    StubAmp actorMessage,
                    MethodAmp method, 
                    HeadersAmp headers, 
                    Object arg0,
                    Object arg1)
  {
    method.send(headers, actorDeliver.worker(actorMessage), arg0, arg1);
  }

  default void send(StubAmp actorDeliver,
                    StubAmp actorMessage,
                    MethodAmp method, 
                    HeadersAmp headers, 
                    Object arg0,
                    Object arg1,
                    Object arg2)
  {
    method.send(headers, actorDeliver.worker(actorMessage), arg0, arg1, arg2);
  }

  default void send(StubAmp actorDeliver,
                    StubAmp actorMessage,
                    MethodAmp method, 
                    HeadersAmp headers, 
                    Object []args)
  {
    method.send(headers, actorDeliver.worker(actorMessage), args);
  }
  
  default void query(StubAmp actorDeliver,
                     StubAmp actorMessage,
                     MethodAmp method,
                     HeadersAmp headers,
                     Result<?> result)
  {
    method.query(headers, result, 
                 actorDeliver.worker(actorMessage));
  }
  
  default void query(StubAmp actorDeliver,
                     StubAmp actorMessage,
                     MethodAmp method,
                     HeadersAmp headers,
                     Result<?> result,
                     Object arg0)
  {
    method.query(headers, result, 
                 actorDeliver.worker(actorMessage),
                 arg0);
  }
  
  default void query(StubAmp actorDeliver,
                     StubAmp actorMessage,
                     MethodAmp method,
                     HeadersAmp headers,
                     Result<?> result,
                     Object arg0,
                     Object arg1)
  {
    method.query(headers, result, 
                 actorDeliver.worker(actorMessage),
                 arg0, arg1);
  }
  
  default void query(StubAmp actorDeliver,
                     StubAmp actorMessage,
                     MethodAmp method,
                     HeadersAmp headers,
                     Result<?> result,
                     Object arg0,
                     Object arg1,
                     Object arg2)
  {
    method.query(headers, result, 
                 actorDeliver.worker(actorMessage),
                 arg0, arg1, arg2);
  }
  
  default void query(StubAmp actorDeliver,
                     StubAmp actorMessage,
                     MethodAmp method,
                     HeadersAmp headers,
                     Result<?> result, 
                     Object[] args)
  {
    method.query(headers, result, 
                 actorDeliver.worker(actorMessage), 
                 args);
  }
  
  default void stream(StubAmp actorDeliver,
                      StubAmp actorMessage,
                      MethodAmp method,
                      HeadersAmp headers,
                      ResultStream<?> result, 
                      Object[] args)
  {
    method.stream(headers, result, 
                  actorDeliver.worker(actorMessage), 
                  args);
  }
  
  default void outPipe(StubAmp actorDeliver,
                       StubAmp actorMessage,
                       MethodAmp method,
                       HeadersAmp headers,
                       ResultPipeOut<?> result, 
                       Object[] args)
  {
    method.outPipe(headers, result, 
                   actorDeliver.worker(actorMessage), 
                   args);
  }
  
  default void inPipe(StubAmp actorDeliver,
                       StubAmp actorMessage,
                       MethodAmp method,
                       HeadersAmp headers,
                       ResultPipeIn<?> result, 
                       Object[] args)
  {
    method.inPipe(headers, result, 
                   actorDeliver.worker(actorMessage), 
                   args);
  }
  
  default void queryError(StubAmp actor,
                          HeadersAmp headers,
                          long qid,
                          Throwable exn)
  {
    StubAmp queryActor = getActor(actor);
    
    queryActor.queryError(headers, queryActor, qid, exn);
  }

  default void streamCancel(StubAmp actorDeliver,
                            StubAmp actorMessage,
                            HeadersAmp headers, 
                            String addressFrom, 
                            long qid)
  {
    StubAmp queryActor = actorDeliver.worker(actorMessage);
    
    queryActor.streamCancel(headers, actorMessage, addressFrom, qid);
  }

  default void streamResult(StubAmp actorDeliver, 
                            StubAmp actorMessage,
                            HeadersAmp headers,
                            long qid,
                            int sequence,
                            ArrayList<Object> values,
                            Throwable exn,
                            boolean isComplete)
  {
    StubAmp queryActor = actorDeliver.worker(actorMessage);
    
    queryActor.streamReply(headers, actorMessage, qid, sequence,
                           values, exn, isComplete);
  }

  default void consume(StubAmp actor, ServiceRef subscriber)
  {
    actor.consume(subscriber);
  }

  default void flushPending(StubAmp actorBean, InboxAmp serviceRef)
  {
  }

  default boolean onSave(StubAmp actor)
  {
    return false;
  }

  default void beforeBatch(ActorAmpState actor)
  {
    actor.beforeBatchImpl();
  }
  

  default void afterBatch(ActorAmpState actor)
  {
    actor.afterBatchImpl();
  }

  default void shutdown(StubAmp actor, ShutdownModeAmp mode)
  {
    actor.onShutdown(mode);
  }

  default boolean isActive()
  {
    return false;
  }

  default boolean isModified()
  {
    return false;
  }
}
