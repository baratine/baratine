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

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.ActorAmpState;
import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.LoadState;
import com.caucho.v5.amp.spi.LoadStateNull;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.MethodAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;

import io.baratine.io.ResultPipeOut;
import io.baratine.service.Result;
import io.baratine.service.ServiceExceptionClosed;
import io.baratine.stream.ResultStream;

/**
 * Baratine actor skeleton
 */
public enum LoadStateActorAmp implements LoadState
{
  NEW {
    @Override
    public LoadState load(ActorAmp actor,
                          InboxAmp inbox,
                          MessageAmp msg)
    {
      ActorAmpState actorBean = (ActorAmpState) actor;
      
      if (actorBean.isJournalReplay()) {
        return onInitImpl(actor, inbox, msg, INIT_REPLAY);
      }
      else {
        return onInitImpl(actor, inbox, msg, INIT);
      }
    }

    @Override
    public LoadState loadReplay(ActorAmp actor,
                                InboxAmp inbox,
                                MessageAmp msg)
    {
      ActorAmpState actorBean = (ActorAmpState) actor;

      actorBean.queuePendingReplayMessage(msg);
      msg = null;

      return onInitImpl(actor, inbox, msg, INIT_REPLAY);
    }

    @Override
    public void beforeBatch(ActorAmpState actor)
    {
    }

    @Override
    public void afterBatch(ActorAmpState actor)
    {
    }
    
    @Override
    public void flushPending(ActorAmp actor, InboxAmp inbox)
    {
    }
  },

  INIT {
    @Override
    public LoadState load(ActorAmp actor, 
                          InboxAmp inbox,
                          MessageAmp msg)
    {
      //ActorAmpState actorBase = (ActorAmpState) actor;

      actor.beforeBatchImpl();
      
      return onLoadImpl(actor, inbox, msg, LOAD);
    }

    @Override
    public void beforeBatch(ActorAmpState actor)
    {
    }

    @Override
    public void afterBatch(ActorAmpState actor)
    {
    }
    
    @Override
    public void flushPending(ActorAmp actor, InboxAmp inbox)
    {
    }
  },

  INIT_REPLAY {
    @Override
    public LoadState load(ActorAmp actor,
                          InboxAmp inbox,
                          MessageAmp msg)
    {
      ActorAmpState actorBase = (ActorAmpState) actor;

      actorBase.beforeBatchImpl();
      
      return onLoadImpl(actor, inbox, msg, REPLAY);
    }

    @Override
    public void beforeBatch(ActorAmpState actor)
    {
    }

    @Override
    public void afterBatch(ActorAmpState actor)
    {
    }

    
    @Override
    public void flushPending(ActorAmp actor, InboxAmp inbox)
    {
    }

    @Override
    public LoadStateActorAmp toActive(ActorAmp actor)
    {
      return INIT_REPLAY_ACTIVE;
    }
  },

  INIT_REPLAY_ACTIVE {
    @Override
    public LoadState load(ActorAmp actor,
                          InboxAmp inbox,
                          MessageAmp msg)
    {
      actor.beforeBatchImpl();
      
      return onLoadImpl(actor, inbox, msg, REPLAY_ACTIVE);
    }

    @Override
    public void beforeBatch(ActorAmpState actor)
    {
    }

    @Override
    public void afterBatch(ActorAmpState actor)
    {
    }

    
    @Override
    public void flushPending(ActorAmp actor, InboxAmp inbox)
    {
    }
  },

  REPLAY {
    @Override
    public LoadState load(ActorAmp actor,
                          InboxAmp inbox,
                          MessageAmp msg)
    {
      ActorAmpState actorBean = (ActorAmpState) actor;
      
      if (msg != null) {
        actorBean.queuePendingMessage(msg);
      }
      
      return new LoadStateNull();
    }
    
    @Override
    public void flushPending(ActorAmp actor, InboxAmp inbox)
    {
      ActorAmpState actorBase = (ActorAmpState) actor;

      actorBase.deliverPendingReplay(inbox);
    }

    @Override
    public LoadState loadReplay(ActorAmp actor,
                                InboxAmp inbox,
                                MessageAmp msg)
    {
      ActorAmpState actorBean = (ActorAmpState) actor;
      
      actorBean.setLoadState(REPLAY_MODIFY);
      
      return this;
    }

    @Override
    public void onActive(ActorAmp actor, InboxAmp inbox)
    {
      onActiveImpl(actor, inbox, null, ACTIVE);
    }

    @Override
    public LoadStateActorAmp toActive(ActorAmp actor)
    {
      return REPLAY_ACTIVE;
    }
  },

  REPLAY_MODIFY {
    @Override
    public LoadState load(ActorAmp actor,
                          InboxAmp inbox,
                          MessageAmp msg)
    {
      ActorAmpStateBase actorBean = (ActorAmpStateBase) actor;

      if (msg != null) {
        actorBean.queuePendingMessage(msg);
      }
      
      return new LoadStateNull();
    }
    
    @Override
    public void flushPending(ActorAmp actor, InboxAmp inbox)
    {
      ActorAmpStateBase actorBase = (ActorAmpStateBase) actor;

      actorBase.deliverPendingReplay(inbox);
    }

    @Override
    public LoadState loadReplay(ActorAmp actor, 
                                InboxAmp inbox,
                                MessageAmp msg)
    {
      return this;
    }
    
    @Override
    public boolean isModified()
    {
      return true;
    }

    @Override
    public void onActive(ActorAmp actor, InboxAmp inbox)
    {
      onActiveImpl(actor, inbox, null, MODIFY);
    }

    @Override
    public LoadStateActorAmp toActive(ActorAmp actor)
    {
      return REPLAY_ACTIVE;
    }
  },

  REPLAY_ACTIVE {
    @Override
    public LoadState load(ActorAmp actor,
                          InboxAmp inbox,
                          MessageAmp msg)
    {
      // ActorAmpStateBase actorBean = (ActorAmpStateBase) actor;

      return onActiveImpl(actor, inbox, null, MODIFY);
      //return onActiveImpl(actor, inbox, msg, MODIFY);
    }
    
    @Override
    public void flushPending(ActorAmp actor, InboxAmp inbox)
    {
      ActorAmpStateBase actorBase = (ActorAmpStateBase) actor;

      actorBase.deliverPendingReplay(inbox);
    }

    @Override
    public LoadState loadReplay(ActorAmp actor,
                                InboxAmp inbox,
                                MessageAmp msg)
    {
      return this;
    }
  },

  LOAD {
    @Override
    public LoadState load(ActorAmp actor, 
                          InboxAmp inbox, 
                          MessageAmp msg)
    {
      // ActorAmpStateBase actorBean = (ActorAmpStateBase) actor;

      /*
      // XXX: not quite right because of timing, e.g. load slow
      // baratine/923a
      if (actorBean.isModifiedChild(actor)) {
        return onActiveImpl(actor, msg, MODIFY);
      }
      else {
        return onActiveImpl(actor, msg, ACTIVE);
      }
      */
      return onActiveImpl(actor, inbox, msg, ACTIVE);
    }

    @Override
    public LoadState loadReplay(ActorAmp actor,
                                InboxAmp inbox,
                                MessageAmp msg)
    {
      return this;
    }
  },

  ACTIVE {
    @Override
    public LoadState load(ActorAmp actor, 
                          InboxAmp inbox, 
                          MessageAmp msg)
    {
      return this;
    }

    @Override
    public void onModify(ActorAmp actor)
    {
      ActorAmpStateBase actorBean = (ActorAmpStateBase) actor;

      actorBean.setLoadState(MODIFY);

      actorBean.addModifiedChild(actor);
    }
    
    @Override
    public boolean isActive()
    {
      return true;
    }
  },

  MODIFY {
    @Override
    public void onSave(ActorAmp actor, SaveResult saveResult)
    {
      ActorAmpState actorBean = (ActorAmpState) actor;

      actorBean.setLoadState(ACTIVE);

      actorBean.onSaveStartImpl(saveResult.addBean());
    }
    
    @Override
    public boolean isActive()
    {
      return true;
    }
    
    @Override
    public boolean isModified()
    {
      return true;
    }
  },

  PENDING {
    @Override
    public LoadState load(ActorAmp actor, 
                          InboxAmp inbox,
                          MessageAmp msg)
    {
      ActorAmpStateBase actorBean = (ActorAmpStateBase) actor;

      actorBean.queuePendingMessage(msg);

      return new LoadStateNull();
    }

    @Override
    public LoadState loadReplay(ActorAmp actor,
                                InboxAmp inbox,
                                MessageAmp msg)
    {
      ActorAmpStateBase actorBean = (ActorAmpStateBase) actor;

      actorBean.queuePendingReplayMessage(msg);

      return new LoadStateNull();
    }
  },

  FAIL {
    @Override
    public LoadState load(ActorAmp actor,
                          InboxAmp inbox,
                          MessageAmp msg)
    {
      //ActorAmpBeanChild actorChild = (ActorAmpBeanChild) actor;

      //actorChild.queuePendingMessage(msg);

      System.out.println("XXX-FAIL: " + this);

      return new LoadStateNull();
    }
  },

  DESTROY {
    @Override
    public LoadState load(ActorAmp actor, 
                          InboxAmp inbox,
                          MessageAmp msg)
    {
      // return new LoadStateNull();
      return this;
    }

    @Override
    public void beforeBatch(ActorAmpState actor)
    {
    }

    @Override
    public void afterBatch(ActorAmpState actor)
    {
    }
    
    @Override
    public void flushPending(ActorAmp actor, InboxAmp inbox)
    {
    }
    
    public void query(ActorAmp actorDeliver,
                      ActorAmp actorMessage,
                      MethodAmp method,
                      HeadersAmp headers,
                      Result<?> result)
    {
      RuntimeException exn
      = new ServiceExceptionClosed(actorMessage + " for method " + method);
    exn.fillInStackTrace();
    
    result.fail(exn);
    }
    
    public void query(ActorAmp actorDeliver,
                       ActorAmp actorMessage,
                       MethodAmp method,
                       HeadersAmp headers,
                       Result<?> result,
                       Object arg0)
    {
      RuntimeException exn
        = new ServiceExceptionClosed(actorMessage + " for method " + method);
      exn.fillInStackTrace();
  
      result.fail(exn);
    }
    
    @Override
    public void query(ActorAmp actorDeliver,
                       ActorAmp actorMessage,
                       MethodAmp method,
                       HeadersAmp headers,
                       Result<?> result,
                       Object arg0,
                       Object arg1)
    {
      RuntimeException exn
        = new ServiceExceptionClosed(actorMessage + " for method " + method);
      exn.fillInStackTrace();
  
      result.fail(exn);
    }
    
    @Override
    public void query(ActorAmp actorDeliver,
                       ActorAmp actorMessage,
                       MethodAmp method,
                       HeadersAmp headers,
                       Result<?> result,
                       Object arg0,
                       Object arg1,
                       Object arg2)
    {
      RuntimeException exn
        = new ServiceExceptionClosed(actorMessage + " for method " + method);
      exn.fillInStackTrace();
  
      result.fail(exn);
    }
    
    @Override
    public void query(ActorAmp actorDeliver,
                       ActorAmp actorMessage,
                       MethodAmp method,
                       HeadersAmp headers,
                       Result<?> result, 
                       Object[] args)
    {
      RuntimeException exn
        = new ServiceExceptionClosed(actorMessage + " for method " + method);
      exn.fillInStackTrace();
  
      result.fail(exn);
    }
    
    @Override
    public void stream(ActorAmp actorDeliver,
                        ActorAmp actorMessage,
                        MethodAmp method,
                        HeadersAmp headers,
                        ResultStream<?> result, 
                        Object[] args)
    {
      RuntimeException exn
        = new ServiceExceptionClosed(actorMessage + " for method " + method);
      exn.fillInStackTrace();
  
      result.fail(exn);
    }
    
    @Override
    public void outPipe(ActorAmp actorDeliver,
                        ActorAmp actorMessage,
                        MethodAmp method,
                        HeadersAmp headers,
                        ResultPipeOut<?> result, 
                        Object[] args)
    {
      RuntimeException exn
        = new ServiceExceptionClosed(actorMessage + " for method " + method);
      exn.fillInStackTrace();
  
      result.fail(exn);
    }
  };

  private static final Logger log
    = Logger.getLogger(LoadStateActorAmp.class.getName());

  @Override
  public void onSave(ActorAmp actor, SaveResult saveResult)
  {
    ActorAmpState actorBean = (ActorAmpState) actor;

    actorBean.onSaveChildren(saveResult);
  }

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
    throw new IllegalStateException(this + " " + actor + " " + msg);
  }

  @Override
  public void onModify(ActorAmp actor)
  {
  }
  
  @Override
  public void flushPending(ActorAmp actor, InboxAmp inbox)
  {
    ActorAmpState actorBase = (ActorAmpState) actor;

    actorBase.deliverPendingMessages(inbox);
  }
  
  public LoadStateActorAmp toActive(ActorAmp actor)
  {
    return this;
  }

  @Override
  public void shutdown(ActorAmp actor, ShutdownModeAmp mode)
  {
    ActorAmpState actorBase = (ActorAmpState) actor;
    
    actorBase.setLoadState(LoadStateActorAmp.DESTROY);
    
    actor.onShutdown(mode);
  }
  
  
  private static LoadState onInitImpl(ActorAmp actor, 
                                      InboxAmp inbox,
                                      MessageAmp msg, 
                                      LoadStateActorAmp nextState)
  {
    ActorAmpState actorBean = (ActorAmpState) actor;

    LoadStatePending pending
      = new LoadStatePending(nextState, actorBean, inbox, msg);
    
    actorBean.setLoadState(pending);
    
    actorBean.onInit(pending);
    
    return pending.onPendingNext(actorBean, msg);
  }

  private static LoadState onLoadImpl(ActorAmp actor,
                                      InboxAmp inbox,
                                      MessageAmp msg, 
                                      LoadStateActorAmp nextState)
  {
    ActorAmpState actorBean = (ActorAmpState) actor;

    LoadStatePending pending
      = new LoadStatePending(nextState, actorBean, inbox, msg);
    
    actorBean.setLoadState(pending);

    actorBean.onLoad(pending);
    
    return pending.onPendingNext(actorBean, msg);
  }

  private static LoadState onActiveImpl(ActorAmp actor, 
                                        InboxAmp inbox,
                                        MessageAmp msg, 
                                        LoadStateActorAmp nextState)
  {
    ActorAmpState actorBean = (ActorAmpState) actor;

    LoadStatePending pending
      = new LoadStatePending(nextState, actorBean, inbox, msg);
    
    actorBean.setLoadState(pending);

    actorBean.onActive(pending);

    return pending.onPendingNext(actorBean, msg);
  }
  
  private static class LoadStatePending implements LoadState, Result<Object>
  {
    private LoadStateActorAmp _nextState;
    private ActorAmpState _actor;
    private InboxAmp _inbox;
    private MessageAmp _msg;
    private SaveResult _saveResult;
    
    private boolean _isComplete;
    private boolean _isPending;
    
    LoadStatePending(LoadStateActorAmp nextState,
                     ActorAmpState actor,
                     InboxAmp inbox,
                     MessageAmp msg)
    {
      Objects.requireNonNull(inbox);
      
      _nextState = nextState;
      _actor = actor;
      
      _inbox = inbox;
      _msg = msg;
    }

    LoadStateActorAmp getNextState()
    {
      return _nextState;
    }
    
    @Override
    public LoadState load(ActorAmp actor,
                          InboxAmp inbox,
                          MessageAmp msg)
    {
      ActorAmpStateBase actorBean = (ActorAmpStateBase) actor;

      actorBean.queuePendingMessage(msg);

      return new LoadStateNull();
    }

    @Override
    public LoadState loadReplay(ActorAmp actor, 
                                InboxAmp inbox, 
                                MessageAmp msg)
    {
      ActorAmpStateBase actorBean = (ActorAmpStateBase) actor;

      actorBean.queuePendingReplayMessage(msg);

      return new LoadStateNull();
    }

    @Override
    public void onModify(ActorAmp actorAmpBase)
    {
    }
    
    @Override
    public void beforeBatch(ActorAmpState actor)
    {
    }
    
    @Override
    public void afterBatch(ActorAmpState actor)
    {
    }
    

    @Override
    public void onSave(ActorAmp actor, SaveResult saveResult)
    {
      ActorAmpStateBase actorBean = (ActorAmpStateBase) actor;
      
      actorBean.queuePendingSave(saveResult);
      
      /*
      if (_saveResult != null && _saveResult != saveResult) {
        System.out.println("LoadState save issue: " + saveResult);
        Thread.dumpStack();
      }
      
      _saveResult = saveResult;
      */
    }

    @Override
    public void onActive(ActorAmp actor, InboxAmp inbox)
    {
      _nextState = _nextState.toActive(actor);
      
      // super.onActive(actor);
    }
    
    @Override
    public void handle(Object result, Throwable exn)
    {
      _isComplete = true;
      
      if (exn != null) {
        log.warning("@OnLoad: " + _actor + " " + exn);
        
        if (log.isLoggable(Level.FINER)) {
          log.log(Level.FINER, "@OnLoad: " + _actor + " " + exn.toString(), exn);
        }
      }
      
      if (_isPending) {
        complete(_actor, _msg);
      }
    }
    
    public LoadState onPendingNext(ActorAmpState actorBean, MessageAmp msg)
    {
      if (_isComplete) {
        actorBean.setLoadState(getNextState());
        actorBean.loadState().flushPending(actorBean, _inbox);
        
        if (_saveResult != null) {
          actorBean.loadState().onSave(actorBean, _saveResult);
        }
        
        return actorBean.load(_inbox, msg); 
      }
      else {
        _isPending = true;
        
        _actor = actorBean;
        actorBean.queuePendingMessage(msg);
        
        return new LoadStateNull();
      }
    }
    
    void complete(ActorAmpState actorBean, MessageAmp msg)
    {
      // OutboxAmp oldOutbox = OutboxAmp.current();
      //OutboxAmp outbox = oldOutbox;
      OutboxAmp outbox = OutboxAmp.current();
      
      /*
      if (outbox == null) {
        outbox = new OutboxAmpBase();
        OutboxThreadLocal.setCurrent(outbox);
      }
      */
      
      InboxAmp oldInbox = outbox.inbox();
      
      outbox.inbox(_inbox);
      
      try {
        actorBean.setLoadState(getNextState());

        actorBean.loadState().flushPending(actorBean, _inbox);
        actorBean.load(_inbox, null);
        //actorBean.load(msg);
      
        if (_saveResult != null) {
          actorBean.loadState().onSave(actorBean, _saveResult);
        }

        /*
         // baratine/10e8
        if (msg != null) {
          actorBean.queuePendingMessage(msg);
        }
        */

        actorBean.loadState().flushPending(actorBean, _inbox);
      } finally {
        /*
        if (oldOutbox == null) {
          outbox.flush();
          OutboxThreadLocal.setCurrent(oldOutbox);
        }
        else {
          oldOutbox.setInbox(oldInbox);
        }
        */
        
        outbox.inbox(oldInbox);
      }
    }
    
    public String toString()
    {
      return getClass().getSimpleName() + "[" + _nextState + "]";
    }
  }
}
