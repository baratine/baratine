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
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.deliver.QueueDeliver;
import com.caucho.v5.amp.journal.JournalAmp;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.spi.StubStateAmp;
import com.caucho.v5.inject.type.AnnotatedTypeClass;

import io.baratine.service.Result;

/**
 * stub
 */
public class StubAmpBase implements StubAmp
{
  //private static final L10N L = new L10N(StubAmpBase.class);
  private static final Logger log
    = Logger.getLogger(StubAmpBase.class.getName());
  
  private PendingMessages _pendingMessages;
  
  private static final AnnotatedType _annotatedTypeObject
    = new AnnotatedTypeClass(Object.class);
  
  private StubStateAmp _state;
  
  protected StubAmpBase()
  {
    initLoadState();
  }
  
  public void initLoadState()
  {
    _state = createLoadState();
  }

  /*
  public StubStateAmp createLoadState()
  {
    return LoadStateLoad.LOAD;
  }
  */
  
  //@Override
  protected StubStateAmp createLoadState()
  {
    return StubStateAmpBean.NEW;
  }
  
  @Override
  public StubStateAmp state()
  {
    return _state;
  }
  
  @Override
  public String name()
  {
    AnnotatedType annType = api();
    Type type = annType.getType();
    
    if (type instanceof Class) {
      Class<?> cl = (Class<?>) type;
      
      return "anon:" + cl.getSimpleName();
    }
    else {
      return "anon:" + type;
    }
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
  public boolean isPublic()
  {
    return false;
  }
  
  @Override
  public AnnotatedType api()
  {
    return _annotatedTypeObject;
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
  public JournalAmp journal()
  {
    return null;
  }

  @Override
  public void journal(JournalAmp journal)
  {
  }
  
  @Override
  public String journalKey()
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
                         StubAmp actor,
                         long qid, 
                         Object value)
  {
  }
  
  @Override
  public void queryError(HeadersAmp headers, 
                         StubAmp actor,
                         long qid, 
                         Throwable exn)
  {
  }
  
  @Override
  public void streamReply(HeadersAmp headers, 
                          StubAmp actor,
                          long qid,
                          int sequence,
                          List<Object> values,
                          Throwable exn,
                          boolean isComplete)
  {
    System.out.println("STR: " + values + " " + isComplete + " " + this);
  }

  @Override
  public StubAmp worker(StubAmp actorMessage)
  {
    return actorMessage;
  }
  
  @Override
  public StubStateAmp load(StubAmp stubMessage, MessageAmp msg)
  {
    return stubMessage.state().load(stubMessage, 
                                    msg.inboxTarget(), 
                                    msg);
  }
  
  @Override
  public StubStateAmp load(MessageAmp msg)
  {
    return _state.load(this, msg.inboxTarget(), msg);
  }
  
  // @Override
  public StubStateAmp load(InboxAmp inbox, MessageAmp msg)
  {
    return _state.load(this, inbox, msg);
  }
  
  @Override
  public StubStateAmp loadReplay(InboxAmp inbox, MessageAmp msg)
  {
    return _state.loadReplay(this, inbox, msg);
  }
  
  @Override
  public void onDelete()
  {
    state().onDelete(this);
  }
  
  @Override
  public void onModify()
  {
    state().onModify(this);
  }
  
  @Override
  public void onSave(Result<Void> result)
  {
    onSaveChild(result);
  }
  
  @Override
  public void onSaveChild(Result<Void> result)
  {
    result.ok(null);
  }
  
  /*
  public boolean onSaveStartImpl(Result<Boolean> cont)
  {
    cont.ok(true);
    
    return true;
  }
  */
  
  @Override
  public void state(StubStateAmp loadState)
  {
    _state = loadState;
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
  
  /*
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
  */

  /*
  @Override
  public QueueService<MessageAmp> buildQueue(QueueServiceBuilder<MessageAmp> queueBuilder,
                                              InboxQueue queueMailbox)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */
  
  @Override
  public boolean isLifecycleAware()
  {
    return false;
  }
  
  @Override
  public boolean isStarted()
  {
    return state().isActive();
  }
  
  @Override
  public void replay(InboxAmp mailbox,
                     QueueDeliver<MessageAmp> queue,
                     Result<Boolean> cont)
  {
  }
  
  /*
  @Override
  public void afterReplay()
  {
  }
  */
  
  /*
  @Override
  public void onInit(Result<? super Boolean> result)
  {
    if (result != null) {
      result.ok(true);
    }
  }
  */
  
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
  public void onSaveEnd(boolean isValid)
  {
  }
  
  @Override
  public void onShutdown(ShutdownModeAmp mode)
  {
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + name() + "]";
  }

  @Override
  public void onInit(Result<? super Boolean> result)
  {
    result.ok(true);
  }

  public void onLoad(Result<? super Boolean> result)
  {
    result.ok(true);
  }
  
  @Override
  public void beforeBatch()
  {
    state().beforeBatch(this);
  }
  
  public void beforeBatchImpl()
  {
  }
  
  @Override
  public void afterBatch()
  {
    state().afterBatch(this);
  }
  
  public void afterBatchImpl()
  {
  }
  
  
  @Override
  public boolean isJournalReplay()
  {
    return journal() != null;
  }

  protected boolean isModifiedChild(StubAmp actor)
  {
    return false;
  }

  protected void addModifiedChild(StubAmp actor)
  {
  }
  
  protected void flushModified()
  {
  }

  public void onSaveChildren(SaveResult saveResult)
  {
  }  
  
  @Override
  public void queuePendingMessage(MessageAmp msg)
  {
    if (msg == null) {
      return;
    }
    
    //System.err.println("PEND: " + msg);
    //Thread.dumpStack();
    
    if (_pendingMessages == null) {
      _pendingMessages = new PendingMessages();
    }
    
    _pendingMessages.addMessage(msg);
  }

  void queuePendingSave(Result<Void> saveResult)
  {
    if (saveResult == null) {
      return;
    }
    
    if (_pendingMessages == null) {
      _pendingMessages = new PendingMessages();
    }
    
    //System.err.println("MSG1: " + msg);
    //Thread.dumpStack();
    
    _pendingMessages.addSave(saveResult);
  }

  @Override
  public void queuePendingReplayMessage(MessageAmp msg)
  {
    if (msg == null) {
      return;
    }
    
    if (_pendingMessages == null) {
      _pendingMessages = new PendingMessages();
    }
    
    //System.err.println("PEND-REPLAY: " + msg);
    //Thread.dumpStack();
    System.out.println("  Q2: " + msg + " " + this);
    
    if (_pendingMessages.addReplay(msg)) {
      addModifiedChild(this);
    }
  }
  
  @Override
  public void deliverPendingMessages(InboxAmp inbox)
  {
    PendingMessages pending = _pendingMessages;
    
    if (pending == null) {
      return;
    }
    
    _pendingMessages = null;

    pending.deliver(inbox);
  }
  
  @Override
  public void deliverPendingReplay(InboxAmp inbox)
  {
    PendingMessages pending = _pendingMessages;
    
    if (pending == null) {
      return;
    }

    pending.deliverReplay(inbox);
  }
  
  private class PendingMessages
  {
    private ArrayList<MessageAmp> _pendingReplay = new ArrayList<>();
    private ArrayList<MessageAmp> _pendingMessages = new ArrayList<>();
    private Result<Void> _saveResult;
    
    boolean addReplay(MessageAmp msg)
    {
      boolean isNew = _pendingReplay.size() == 0;
      
      _pendingReplay.add(msg);
      
      return isNew;
    }
    
    public void addSave(Result<Void> saveResult)
    {
      Objects.requireNonNull(saveResult);

      if (_saveResult != null && _saveResult != saveResult) {
        System.out.println("Double pending save");
      }
      
      _saveResult = saveResult;
    }

    void addMessage(MessageAmp msg)
    {
      _pendingMessages.add(msg);
    }
    
    void deliverReplay(InboxAmp inbox)
    {
      ArrayList<MessageAmp> pendingMessages = new ArrayList<>(_pendingReplay);
      _pendingReplay.clear();
      
      for (MessageAmp msg : pendingMessages) {
        try {
          msg.invoke(inbox, StubAmpBase.this);
        } catch (Exception e) {
          e.printStackTrace();
          log.log(Level.WARNING, e.toString(), e);
        }
      }
    }
    
    void deliverSave()
    {
      Result<Void> saveResult = _saveResult;
      
      if (saveResult != null) {
        _saveResult = null;
        
        state().onSave(StubAmpBase.this, saveResult);
      }
    }
    
    void deliver(InboxAmp inbox)
    {
      deliverReplay(inbox);
      deliverSave();
      
      ArrayList<MessageAmp> pendingMessages = new ArrayList<>(_pendingMessages);
      _pendingMessages.clear();
      
      for (MessageAmp msg : pendingMessages) {
        //System.err.println("DPM: " + msg);
        //Thread.dumpStack();
        
        try {
          msg.invoke(inbox, StubAmpBase.this);
        } catch (Exception e) {
          e.printStackTrace();
          log.log(Level.WARNING, e.toString(), e);
        }
      }
      
    }
  }

  /*
  private static class MethodBase extends MethodAmpBase {
    public MethodBase(String methodName)
    {
    }

    @Override
    public void send(HeadersAmp headers,
                     StubAmp actor,
                     Object []args)
    {
    }

    @Override
    public void query(HeadersAmp headers,
                      ResultChain<?> result,
                      StubAmp actor,
                      Object []args)
    {
      result.fail(new ServiceExceptionMethodNotFound(
                                       L.l("'{0}' is an undefined method for {1}",
                                           this, actor)));
    }
  }
  */
}
