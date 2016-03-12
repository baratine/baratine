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

import java.lang.annotation.Annotation;
import java.util.List;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.deliver.QueueDeliver;
import com.caucho.v5.amp.deliver.QueueDeliverBuilder;
import com.caucho.v5.amp.inbox.InboxQueue;
import com.caucho.v5.amp.journal.JournalAmp;

import io.baratine.service.Result;
import io.baratine.service.ServiceRef;

/**
 * An AMP Actor sends and receives messages as the core class in a
 * service-oriented architecture.
 *
 * <h2>Core API</h2>
 *
 * Each actor has a unique address, which is the address for messages sent to
 * the actor.  addresses are typically URLs.
 *
 */
public interface ActorAmp
{
  String getName();

  boolean isExported();
  
  Class<?> getApiClass();
  
  Annotation []getApiAnnotations();
  
  Object bean();
  Object loadBean();
  
  /**
   * Returns a child actor
   */
  Object onLookup(String path, ServiceRefAmp parentRef);
  
  //
  // pub/sub
  //

  void consume(ServiceRef consumer);
  void subscribe(ServiceRef consumer);
  void unsubscribe(ServiceRef consumer);

  /**
   * Returns an actor method.
   * 
   * @param methodName the name of the method
   */
  MethodAmp getMethod(String methodName);
  
  MethodAmp []getMethods();

  void queryReply(HeadersAmp headers, 
                  ActorAmp actor,
                  long qid, 
                  Object value);
  
  void queryError(HeadersAmp headers, 
                  ActorAmp actor,
                  long qid,
                  Throwable exn);
  
  default <T> boolean complete(Result<T> result, T value)
  {
    result.ok(value);
    
    return true;
  }
  
  default boolean fail(Result<?> result, Throwable exn)
  {
    result.fail(exn);
    
    return true;
  }

  void streamReply(HeadersAmp headers, 
                   ActorAmp actor,
                   long qid,
                   int sequence,
                   List<Object> values,
                   Throwable exn,
                   boolean isComplete);

  default void streamCancel(HeadersAmp headers,
                            ActorAmp queryActor,
                            String addressFrom, 
                            long qid)
  {
    System.out.println("CANCEL: " + this);
  }
  
  //
  // send where this actor is the deliver actor.
  //
  
  /*
  void send(MethodAmp method, 
            HeadersAmp headers);
  
  void send(MethodAmp method, 
            HeadersAmp headers,
            Object a1);
  
  void send(MethodAmp method, 
            HeadersAmp headers,
            Object a1,
            Object a2);
  
  void send(MethodAmp method, 
            HeadersAmp headers,
            Object a1,
            Object a2,
            Object a3);
  
  void send(MethodAmp method, 
            HeadersAmp headers, 
            Object[] args);
  */
  
  //
  // query, where this actor is the deliver actor
  //

  /*
  void query(MethodAmp method,
             HeadersAmp headers,
             QueryRefAmp queryRef);
  
  void query(MethodAmp method,
             HeadersAmp headers,
             QueryRefAmp queryRef,
             Object a1);
  
  void query(MethodAmp method,
             HeadersAmp headers,
             QueryRefAmp queryRef,
             Object a1,
             Object a2);
  
  void query(MethodAmp method,
             HeadersAmp headers,
             QueryRefAmp queryRef,
             Object a1,
             Object a2,
             Object a3);
  
  void query(MethodAmp method,
             HeadersAmp headers,
             QueryRefAmp queryRef, 
             Object[] args);
  
  void query(MessageAmp msg,
             ServiceRefAmp serviceRef,
             MethodAmp method,
             HeadersAmp headers,
             QueryRefAmp queryRef, 
             Object[] args);
  */
  
  //
  // result
  //
  
  default <V> void onComplete(Result<V> result, V value)
  {
    result.ok(value);
  }
  
  default void onFail(Result<?> result, Throwable exn)
  {
    result.fail(exn);
  }

  //
  // Stream (map/reduce)
  //
  
  /*
  <T,R> void stream(MethodAmp method,
                    HeadersAmp headers,
                    QueryRefAmp queryRef,
                    CollectorAmp<T,R> stream,
                    Object[] args);
                    */
  
  /**
   * Called before delivering a batch of messages.
   */
  void beforeBatch();
  void beforeBatchImpl();

  /**
   * Called after delivering a batch of messages.
   */
  void afterBatch();
  
  /**
   * Queue building.
   */
  /*
  QueueService<MessageAmp> buildQueue(QueueServiceBuilder<MessageAmp> queueBuilder,
                                       InboxQueue queueMailbox);
                                       */

  JournalAmp getJournal();
  void setJournal(JournalAmp journal);
  String getJournalKey();
  // boolean requestCheckpoint();
  
  //
  // lifecycle
  //

  boolean isLifecycleAware();
  
  /**
   * True for actors that enable lazy-start. This returns false for the
   * journal actor.
   */
  boolean isStarted();
  
  /**
   * Returns true if the service is up. For example a remote client might
   * return false if the connection has failed.
   */
  boolean isUp();
  
  /**
   * The service is valid unless it's been deleted.
   */
  boolean isClosed();
  
  void onInit(Result<? super Boolean> result);
  void replay(InboxAmp inbox,
              QueueDeliver<MessageAmp> queue, 
              Result<Boolean> cont);
  //void afterReplay();
  
  void onActive(Result<? super Boolean> result);
  void onModify();
  
  boolean onSave(Result<Boolean> result);
  void checkpointEnd(boolean isValid);
  
  void onShutdown(ShutdownModeAmp mode);

  ActorAmp getActor(ActorAmp actorMessage);

  LoadState load(ActorAmp actorMessage, MessageAmp msg);
  LoadState load(MessageAmp msg);
  LoadState load(InboxAmp inbox, MessageAmp msg);
  LoadState loadReplay(InboxAmp inbox, MessageAmp msg);
  LoadState loadState();

  default void onLru(ServiceRefAmp serviceRef)
  {
  }

  default boolean isPrimary()
  {
    return true;
  }

  default ActorAmp getDelegateMain()
  {
    return this;
  }


}
