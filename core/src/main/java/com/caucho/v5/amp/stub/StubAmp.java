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

import java.lang.reflect.AnnotatedType;
import java.util.List;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.deliver.QueueDeliver;
import com.caucho.v5.amp.journal.JournalAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.StubStateAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

import io.baratine.service.Result;
import io.baratine.service.ResultChain;

/**
 * StubAmp marshals message calls to a service implementation.
 */
public interface StubAmp
{
  String name();

  AnnotatedType api();
  
  boolean isPublic();
  
  Object bean();
  Object loadBean();
  
  /**
   * Returns a child service
   */
  Object onLookup(String path, ServiceRefAmp parentRef);
  
  //
  // pub/sub
  //

  //void consume(ServiceRef consumer);
  //void subscribe(ServiceRef consumer);
  //void unsubscribe(ServiceRef consumer);

  default boolean isAutoCreate()
  {
    return true;
  }

  /**
   * Returns an stub method.
   * 
   * @param methodName the name of the method
   */
  default MethodAmp method(String methodName, Class<?> []param)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  default MethodAmp methodByName(String methodName)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  MethodAmp []getMethods();

  void queryReply(HeadersAmp headers, 
                  StubAmp stub,
                  long qid, 
                  Object value);
  
  void queryError(HeadersAmp headers, 
                  StubAmp stub,
                  long qid,
                  Throwable exn);

  /**
   * Conditional completion depending on the stub type.
   * 
   * The journal uses this to skip its own completion, leaving the processing
   * for the main stub.
   */
  default <T> boolean ok(ResultChain<T> result, T value)
  {
    result.ok(value);
    
    return true;
  }
  
  /**
   * Conditional completion depending on the stub type.
   * 
   * The journal uses this to skip its own completion, leaving the processing
   * for the main stub.
   */
  default boolean fail(ResultChain<?> result, Throwable exn)
  {
    result.fail(exn);
    
    return true;
  }

  void streamReply(HeadersAmp headers, 
                   StubAmp stub,
                   long qid,
                   int sequence,
                   List<Object> values,
                   Throwable exn,
                   boolean isComplete);

  default void streamCancel(HeadersAmp headers,
                            StubAmp queryStub,
                            String addressFrom, 
                            long qid)
  {
    System.out.println("CANCEL: " + this);
  }
  
  //
  // result
  //
  
  /*
  default <V> void onComplete(Result<V> result, V value)
  {
    result.ok(value);
  }
  */
  
  /*
  default void onFail(Result<?> result, Throwable exn)
  {
    result.fail(exn);
  }
  */

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

  JournalAmp journal();
  void journal(JournalAmp journal);
  String journalKey();
  // boolean requestCheckpoint();
  
  //
  // lifecycle
  //

  boolean isLifecycleAware();
  
  /**
   * True for stubs that enable lazy-start. This returns false for the
   * journal stub.
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
              Result<Boolean> result);
  
  void onActive(Result<? super Boolean> result);
  
  default void onCreate()
  {
  }
  
  default void onDelete()
  {
  }
  
  default void onModify()
  {
  }
  
  boolean onSave(Result<Boolean> result);
  void onSaveEnd(boolean isValid);
  
  void onShutdown(ShutdownModeAmp mode);

  StubAmp worker(StubAmp stubMessage);

  StubStateAmp load(StubAmp stubMessage, MessageAmp msg);
  StubStateAmp load(MessageAmp msg);
  StubStateAmp load(InboxAmp inbox, MessageAmp msg);
  StubStateAmp loadReplay(InboxAmp inbox, MessageAmp msg);
  StubStateAmp state();

  default void onLru(ServiceRefAmp serviceRef)
  {
  }
  
  /**
   * True for the main stub.
   * 
   * The journal uses isMain to skip stream invocation.
   */
  // XXX: logic can be removed/replaced with LoadState?
  default boolean isMain()
  {
    return true;
  }

  /*
  default StubAmp delegateMain()
  {
    return this;
  }
*/
  
  //
  // state methods
  //
  
  default boolean isJournalReplay()
  {
    return false;
  }
  
  default void queuePendingReplayMessage(MessageAmp msg)
  {
  }
  
  default void queuePendingMessage(MessageAmp msg)
  {
  }
  
  default void state(StubStateAmp state)
  {
  }
  
  default void deliverPendingMessages(InboxAmp inbox)
  {
  }
  
  default void deliverPendingReplay(InboxAmp inbox)
  {
  }
  
  default boolean onSaveStartImpl(Result<Boolean> addBean)
  {
    return false;
  }
  
  default void onLoad(Result<? super Boolean> result)
  {
  }
  
  default void afterBatchImpl()
  {
  }
  
  default void onSaveChildren(SaveResult saveResult)
  {
    
  }
}
