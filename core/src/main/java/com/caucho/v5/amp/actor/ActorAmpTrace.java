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

package com.caucho.v5.amp.actor;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Objects;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.deliver.QueueDeliver;
import com.caucho.v5.amp.deliver.QueueDeliverBuilder;
import com.caucho.v5.amp.inbox.InboxQueue;
import com.caucho.v5.amp.journal.JournalAmp;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.ActorAmpState;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.LoadState;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.MethodAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

import io.baratine.service.Result;
import io.baratine.service.ServiceRef;

/**
 * Trace actor for debugging.
 */
public class ActorAmpTrace implements ActorAmpState
{
  private ActorAmp _delegate;
  
  public ActorAmpTrace(ActorAmp delegate)
  {
    Objects.requireNonNull(delegate);
    
    _delegate = delegate;
  }
  
  private ActorAmp delegate()
  {
    return _delegate;
  }
  
  private ActorAmpState delegateState()
  {
    if (_delegate instanceof ActorAmpState) {
      return (ActorAmpState) _delegate;
    }
    else {
      return null;
    }
  }
  
  @Override
  public LoadState loadState()
  {
    return delegate().loadState();
  }
  
  @Override
  public String getName()
  {
    return delegate().getName();
  }
  
  @Override
  public boolean isUp()
  {
    return delegate().isUp();
  }
  
  @Override
  public boolean isClosed()
  {
    return delegate().isClosed();
  }
  
  @Override
  public boolean isExported()
  {
    return delegate().isExported();
  }
  
  @Override
  public Class<?> getApiClass()
  {
    return delegate().getApiClass();
  }
  
  @Override
  public Annotation []getApiAnnotations()
  {
    return delegate().getApiAnnotations();
  }
  
  @Override
  public Object bean()
  {
    return delegate().bean();
  }
  
  @Override
  public Object loadBean()
  {
    return delegate().bean();
  }
  
  @Override
  public Object onLookup(String path, ServiceRefAmp parentRef)
  {
    return delegate().onLookup(path, parentRef);
  }
  
  @Override
  public MethodAmp []getMethods()
  {
    MethodAmp []methods = delegate().getMethods();
    
    MethodAmp []traceMethods = new MethodAmp[methods.length];
    
    for (int i = 0; i < methods.length; i++) {
      traceMethods[i] = new MethodAmpTrace(methods[i]);
    }
    
    return traceMethods;
  }
  
  @Override
  public MethodAmp getMethod(String methodName)
  {
    MethodAmp method = delegate().getMethod(methodName);
    
    return new MethodAmpTrace(method);
  }
  
  @Override
  public boolean isJournalReplay()
  {
    if (delegateState() != null) {
      return delegateState().isJournalReplay();
    }
    else {
      return false;
    }
  }
  
  @Override
  public JournalAmp getJournal()
  {
    return delegate().getJournal();
  }

  @Override
  public void setJournal(JournalAmp journal)
  {
    delegate().setJournal(journal);
  }
  
  @Override
  public String getJournalKey()
  {
    return delegate().getJournalKey();
  }
  
  @Override
  public void queryReply(HeadersAmp headers, 
                         ActorAmp actor,
                         long qid, 
                         Object value)
  {
    delegate().queryReply(headers, actor, qid, value);
  }
  
  @Override
  public void queryError(HeadersAmp headers, 
                         ActorAmp actor,
                         long qid, 
                         Throwable exn)
  {
    delegate().queryError(headers, actor, qid, exn);
  }
  
  @Override
  public void streamReply(HeadersAmp headers, 
                          ActorAmp actor,
                          long qid, 
                          int sequence,
                          List<Object> values,
                          Throwable exn,
                          boolean isComplete)
  {
    delegate().streamReply(headers, actor, qid, sequence,
                              values, exn, isComplete);
  }

  @Override
  public ActorAmp getActor(ActorAmp actor)
  {
    return delegate().getActor(actor);
  }
  
  @Override
  public LoadState load(ActorAmp actorMessage, MessageAmp msg)
  {
    return delegate().load(actorMessage, msg);
  }
  
  @Override
  public LoadState load(MessageAmp msg)
  {
    return delegate().load(msg);
  }
  
  @Override
  public LoadState load(InboxAmp inbox, MessageAmp msg)
  {
    return delegate().load(inbox, msg);
  }
  
  @Override
  public LoadState loadReplay(InboxAmp inbox, MessageAmp msg)
  {
    return delegate().loadReplay(inbox, msg);
  }
  
  @Override
  public void onModify()
  {
    delegate().onModify();
  }
  
  @Override
  public boolean onSave(Result<Boolean> result)
  {
    return delegate().onSave(result);
  }
  
  @Override
  public void beforeBatch()
  {
    delegate().beforeBatch();
  }
  
  @Override
  public void beforeBatchImpl()
  {
    delegate().beforeBatchImpl();
  }
  
  @Override
  public void afterBatch()
  {
    delegate().afterBatch();
  }

  /*
  @Override
  public QueueService<MessageAmp> buildQueue(QueueServiceBuilder<MessageAmp> queueBuilder,
                                             InboxQueue inbox)
  {
    return delegate().buildQueue(queueBuilder, inbox);
  }
  */
  
  @Override
  public boolean isLifecycleAware()
  {
    return delegate().isLifecycleAware();
  }
  
  @Override
  public boolean isStarted()
  {
    return delegate().isStarted();
  }
  
  @Override
  public void replay(InboxAmp inbox,
                     QueueDeliver<MessageAmp> queue,
                     Result<Boolean> result)
  {
    delegate().replay(inbox, queue, result);
  }
  
  @Override
  public void onInit(Result<? super Boolean> result)
  {
    delegate().onInit(result);
  }
  
  @Override
  public void onActive(Result<? super Boolean> result)
  {
    delegate().onActive(result);
  }
  
  @Override
  public void checkpointEnd(boolean isValid)
  {
    delegate().checkpointEnd(isValid);
  }
  
  @Override
  public void onShutdown(ShutdownModeAmp mode)
  {
    delegate().onShutdown(mode);
  }

  @Override
  public void consume(ServiceRef consumer)
  {
    delegate().consume(consumer);
  }

  @Override
  public void subscribe(ServiceRef service)
  {
    delegate().subscribe(service);
  }

  @Override
  public void unsubscribe(ServiceRef service)
  {
    delegate().unsubscribe(service);
  }

  @Override
  public void queuePendingMessage(MessageAmp msg)
  {
    delegateState().queuePendingMessage(msg);
  }

  @Override
  public void queuePendingReplayMessage(MessageAmp msg)
  {
    delegateState().queuePendingReplayMessage(msg);
  }

  @Override
  public void setLoadState(LoadState state)
  {
    delegateState().setLoadState(state);
  }

  @Override
  public void deliverPendingReplay(InboxAmp inbox)
  {
    delegateState().deliverPendingReplay(inbox);
  }

  @Override
  public void deliverPendingMessages(InboxAmp inbox)
  {
    delegateState().deliverPendingMessages(inbox);
  }

  @Override
  public boolean onSaveStartImpl(Result<Boolean> result)
  {
    return delegateState().onSaveStartImpl(result);
  }

  @Override
  public void onLoad(Result<? super Boolean> result)
  {
    delegateState().onLoad(result);
  }

  @Override
  public void afterBatchImpl()
  {
    delegateState().afterBatchImpl();
  }

  @Override
  public void onSaveChildren(SaveResult saveResult)
  {
    delegateState().onSaveChildren(saveResult);
  }
  
  @Override
  public ActorAmp getDelegateMain()
  {
    return delegate().getDelegateMain();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + delegate() + "]";
  }
}
