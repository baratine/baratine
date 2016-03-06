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

import com.caucho.v5.amp.actor.SaveResult;

import io.baratine.io.ResultInPipe;
import io.baratine.io.ResultOutPipe;
import io.baratine.service.Result;
import io.baratine.service.ResultStream;
import io.baratine.service.ServiceRef;


/**
 * State/dispatch for a loadable actor.
 */
public interface LoadState
{
  LoadState load(ActorAmp actor,
                 InboxAmp inbox,
                 MessageAmp msg);
  
  default LoadState loadReplay(ActorAmp actor,
                               InboxAmp inbox,
                               MessageAmp msg)
  {
    throw new IllegalStateException(this + " " + actor + " " + msg);
  }

  void onModify(ActorAmp actorAmpBase);

  default void onSave(ActorAmp actor, SaveResult saveResult)
  {
  }
  
  default ActorAmp getActor(ActorAmp actor)
  {
    return actor;
  }

  default void onActive(ActorAmp actor, InboxAmp inbox)
  {
  }

  default void send(ActorAmp actorDeliver,
                    ActorAmp actorMessage,
                    MethodAmp method, 
                    HeadersAmp headers)
  {
    method.send(headers, actorDeliver.getActor(actorMessage));
  }

  default void send(ActorAmp actorDeliver,
                    ActorAmp actorMessage,
                    MethodAmp method, 
                    HeadersAmp headers, 
                    Object arg0)
  {
    method.send(headers, actorDeliver.getActor(actorMessage), arg0);
  }

  default void send(ActorAmp actorDeliver,
                    ActorAmp actorMessage,
                    MethodAmp method, 
                    HeadersAmp headers, 
                    Object arg0,
                    Object arg1)
  {
    method.send(headers, actorDeliver.getActor(actorMessage), arg0, arg1);
  }

  default void send(ActorAmp actorDeliver,
                    ActorAmp actorMessage,
                    MethodAmp method, 
                    HeadersAmp headers, 
                    Object arg0,
                    Object arg1,
                    Object arg2)
  {
    method.send(headers, actorDeliver.getActor(actorMessage), arg0, arg1, arg2);
  }

  default void send(ActorAmp actorDeliver,
                    ActorAmp actorMessage,
                    MethodAmp method, 
                    HeadersAmp headers, 
                    Object []args)
  {
    method.send(headers, actorDeliver.getActor(actorMessage), args);
  }
  
  default void query(ActorAmp actorDeliver,
                     ActorAmp actorMessage,
                     MethodAmp method,
                     HeadersAmp headers,
                     Result<?> result)
  {
    method.query(headers, result, 
                 actorDeliver.getActor(actorMessage));
  }
  
  default void query(ActorAmp actorDeliver,
                     ActorAmp actorMessage,
                     MethodAmp method,
                     HeadersAmp headers,
                     Result<?> result,
                     Object arg0)
  {
    method.query(headers, result, 
                 actorDeliver.getActor(actorMessage),
                 arg0);
  }
  
  default void query(ActorAmp actorDeliver,
                     ActorAmp actorMessage,
                     MethodAmp method,
                     HeadersAmp headers,
                     Result<?> result,
                     Object arg0,
                     Object arg1)
  {
    method.query(headers, result, 
                 actorDeliver.getActor(actorMessage),
                 arg0, arg1);
  }
  
  default void query(ActorAmp actorDeliver,
                     ActorAmp actorMessage,
                     MethodAmp method,
                     HeadersAmp headers,
                     Result<?> result,
                     Object arg0,
                     Object arg1,
                     Object arg2)
  {
    method.query(headers, result, 
                 actorDeliver.getActor(actorMessage),
                 arg0, arg1, arg2);
  }
  
  default void query(ActorAmp actorDeliver,
                     ActorAmp actorMessage,
                     MethodAmp method,
                     HeadersAmp headers,
                     Result<?> result, 
                     Object[] args)
  {
    method.query(headers, result, 
                 actorDeliver.getActor(actorMessage), 
                 args);
  }
  
  default void stream(ActorAmp actorDeliver,
                      ActorAmp actorMessage,
                      MethodAmp method,
                      HeadersAmp headers,
                      ResultStream<?> result, 
                      Object[] args)
  {
    method.stream(headers, result, 
                  actorDeliver.getActor(actorMessage), 
                  args);
  }
  
  default void outPipe(ActorAmp actorDeliver,
                       ActorAmp actorMessage,
                       MethodAmp method,
                       HeadersAmp headers,
                       ResultOutPipe<?> result, 
                       Object[] args)
  {
    method.outPipe(headers, result, 
                   actorDeliver.getActor(actorMessage), 
                   args);
  }
  
  default void inPipe(ActorAmp actorDeliver,
                       ActorAmp actorMessage,
                       MethodAmp method,
                       HeadersAmp headers,
                       ResultInPipe<?> result, 
                       Object[] args)
  {
    method.inPipe(headers, result, 
                   actorDeliver.getActor(actorMessage), 
                   args);
  }
  
  default void queryError(ActorAmp actor,
                          HeadersAmp headers,
                          long qid,
                          Throwable exn)
  {
    ActorAmp queryActor = getActor(actor);
    
    queryActor.queryError(headers, queryActor, qid, exn);
  }

  default void streamCancel(ActorAmp actorDeliver,
                            ActorAmp actorMessage,
                            HeadersAmp headers, 
                            String addressFrom, 
                            long qid)
  {
    ActorAmp queryActor = actorDeliver.getActor(actorMessage);
    
    queryActor.streamCancel(headers, actorMessage, addressFrom, qid);
  }

  default void streamResult(ActorAmp actorDeliver, 
                            ActorAmp actorMessage,
                            HeadersAmp headers,
                            long qid,
                            int sequence,
                            ArrayList<Object> values,
                            Throwable exn,
                            boolean isComplete)
  {
    ActorAmp queryActor = actorDeliver.getActor(actorMessage);
    
    queryActor.streamReply(headers, actorMessage, qid, sequence,
                           values, exn, isComplete);
  }

  default void consume(ActorAmp actor, ServiceRef subscriber)
  {
    actor.consume(subscriber);
  }

  default void flushPending(ActorAmp actorBean, InboxAmp serviceRef)
  {
  }

  default boolean onSave(ActorAmp actor)
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

  default void shutdown(ActorAmp actor, ShutdownModeAmp mode)
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
