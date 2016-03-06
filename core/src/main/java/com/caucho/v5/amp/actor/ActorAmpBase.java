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

import io.baratine.service.Result;
import io.baratine.service.ServiceExceptionMethodNotFound;
import io.baratine.service.ServiceRef;

import java.lang.annotation.Annotation;
import java.util.List;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.inbox.InboxQueue;
import com.caucho.v5.amp.journal.JournalAmp;
import com.caucho.v5.amp.queue.QueueService;
import com.caucho.v5.amp.queue.QueueServiceBuilderBase;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.LoadState;
import com.caucho.v5.amp.spi.LoadStateNull;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.MethodAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.util.L10N;

/**
 * Abstract stream for an actor.
 */
public class ActorAmpBase implements ActorAmp
{
  private static final L10N L = new L10N(ActorAmpBase.class);
  
  private LoadState _loadState;
  
  protected ActorAmpBase()
  {
    initLoadState();
  }
  
  public void initLoadState()
  {
    _loadState = createLoadState();
  }
  
  @Override
  public LoadState loadState()
  {
    return _loadState;
  }
  
  public LoadState createLoadState()
  {
    return LoadStateLoad.LOAD;
  }
  
  @Override
  public String getName()
  {
    return "anon:" + getApiClass().getSimpleName();
  }
  
  @Override
  public boolean isUp()
  {
    return ! isClosed();
  }
  
  @Override
  public boolean isClosed()
  {
    return false;
  }
  
  @Override
  public boolean isExported()
  {
    return false;
  }
  
  @Override
  public Class<?> getApiClass()
  {
    return Object.class;
  }
  
  @Override
  public Annotation []getApiAnnotations()
  {
    return new Annotation[0];
  }
  
  @Override
  public Object bean()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public Object loadBean()
  {
    return bean();
  }
  
  @Override
  public Object onLookup(String path, ServiceRefAmp parentRef)
  {
    return null;
  }
  
  @Override
  public MethodAmp []getMethods()
  {
    return new MethodAmp[0];
  }
  
  @Override
  public MethodAmp getMethod(String methodName)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  @Override
  public JournalAmp getJournal()
  {
    return null;
  }

  @Override
  public void setJournal(JournalAmp journal)
  {
  }
  
  @Override
  public String getJournalKey()
  {
    return null;
  }
  
  /*
  @Override
  public boolean requestCheckpoint()
  {
    return false;
  }
  */

  /*
  @Override
  public void onModify()
  {
  }
  */
  
  @Override
  public void queryReply(HeadersAmp headers, 
                         ActorAmp actor,
                         long qid, 
                         Object value)
  {
  }
  
  @Override
  public void queryError(HeadersAmp headers, 
                         ActorAmp actor,
                         long qid, 
                         Throwable exn)
  {
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
    System.out.println("STR: " + values + " " + isComplete + " " + this);
  }

  @Override
  public ActorAmp getActor(ActorAmp actorMessage)
  {
    return actorMessage;
  }
  
  @Override
  public LoadState load(ActorAmp actorMessage, MessageAmp msg)
  {
    return actorMessage.loadState().load(actorMessage, 
                                         msg.inboxTarget(), 
                                         msg);
  }
  
  @Override
  public LoadState load(MessageAmp msg)
  {
    return _loadState.load(this, msg.inboxTarget(), msg);
  }
  
  // @Override
  public LoadState load(InboxAmp inbox, MessageAmp msg)
  {
    return _loadState.load(this, inbox, msg);
  }
  
  @Override
  public LoadState loadReplay(InboxAmp inbox, MessageAmp msg)
  {
    return _loadState.loadReplay(this, inbox, msg);
  }
  
  @Override
  public void onModify()
  {
    loadState().onModify(this);
  }
  
  @Override
  public boolean onSave(Result<Boolean> result)
  {
    SaveResult saveResult = new SaveResult(result);
    
    loadState().onSave(this, saveResult);
    
    saveResult.completeBean();
    
    return true;
  }
  
  public boolean onSaveStartImpl(Result<Boolean> cont)
  {
    cont.ok(true);
    
    return true;
  }
  
  public void setLoadState(LoadState loadState)
  {
    _loadState = loadState;
    if (loadState instanceof LoadStateNull) {
      System.out.println("LSN: " + this);
    }
  }
  
  //
  // stream (map/reduce)
  //
  
  /*
  @Override
  public <T,R> void stream(MethodAmp method,
                           HeadersAmp headers,
                           QueryRefAmp queryRef,
                           CollectorAmp<T,R> stream,
                           Object[] args)
  {
    method.stream(headers, queryRef, this, stream, args);
  }
  */
  
  @Override
  public void beforeBatch()
  {
  }
  
  @Override
  public void beforeBatchImpl()
  {
  }
  
  @Override
  public void afterBatch()
  {
  }

  @Override
  public QueueService<MessageAmp> buildQueue(QueueServiceBuilderBase<MessageAmp> queueBuilder,
                                              InboxQueue queueMailbox)
  {
    throw new UnsupportedOperationException(getClass().getName());
    /*
    ActorFactory<RampMessage> factory
      = queueMailbox.createActorFactory(getRampManager(),
                                        new RampActorSupplier(this),
                                        null);
    
    ActorDisruptorBuilder<RampMessage> builder;
    
    builder = queueBuilder.createDisruptorBuilder(factory);
    
    return builder.build(queueBuilder);
    */
  }
  
  @Override
  public boolean isLifecycleAware()
  {
    return false;
  }
  
  @Override
  public boolean isStarted()
  {
    return loadState().isActive();
  }
  
  @Override
  public void replay(InboxAmp mailbox,
                     QueueService<MessageAmp> queue,
                     Result<Boolean> cont)
  {
  }
  
  /*
  @Override
  public void afterReplay()
  {
  }
  */
  
  @Override
  public void onInit(Result<? super Boolean> result)
  {
    if (result != null) {
      result.ok(true);
    }
  }
  
  @Override
  public void onActive(Result<? super Boolean> result)
  {
    result.ok(true);
  }
  
  /*
  @Override
  public boolean checkpointStart(Result<Boolean> result)
  {
    result.complete(true);
    
    return true;
  }
  */
  
  @Override
  public void checkpointEnd(boolean isValid)
  {
  }
  
  @Override
  public void onShutdown(ShutdownModeAmp mode)
  {
  }

  @Override
  public void consume(ServiceRef consumer)
  {
  }

  @Override
  public void subscribe(ServiceRef service)
  {
    // XXX: exception?
  }

  @Override
  public void unsubscribe(ServiceRef service)
  {
    // XXX: exception?
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getName() + "]";
  }
  
  static class MethodBase extends MethodAmpBase {
    public MethodBase(String methodName)
    {
    }

    @Override
    public void send(HeadersAmp headers,
                     ActorAmp actor,
                     Object []args)
    {
    }

    @Override
    public void query(HeadersAmp headers,
                      Result<?> result,
                      ActorAmp actor,
                      Object []args)
    {
      result.fail(new ServiceExceptionMethodNotFound(
                                       L.l("'{0}' is an undefined method for {1}",
                                           this, actor)));
    }
  }
  
  private static class LoadStateLoad implements LoadState {
    private static final LoadStateLoad LOAD = new LoadStateLoad();
    
    @Override
    public LoadState load(ActorAmp actor,
                          InboxAmp inbox,
                          MessageAmp msg)
    {
      return this;
    }
    
    @Override
    public LoadState loadReplay(ActorAmp actor,
                                InboxAmp inbox,
                                MessageAmp msg)
    {
      return this;
    }

    @Override
    public void send(ActorAmp actorDeliver,
                     ActorAmp actorMessage,
                      MethodAmp method, 
                      HeadersAmp headers,
                      Object[] args)
    {
      method.send(headers, actorDeliver.getActor(actorMessage), args);
    }

    @Override
    public void query(ActorAmp actorDeliver,
                      ActorAmp actorMessage,
                      MethodAmp method, 
                      HeadersAmp headers,
                      Result<?> result, 
                      Object[] args)
    {
      method.query(headers, result, actorDeliver.getActor(actorMessage), args);
    }

    @Override
    public void onModify(ActorAmp actorAmpBase)
    {
      // TODO Auto-generated method stub
      
    }
  }
}
