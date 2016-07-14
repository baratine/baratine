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

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.HeadersAmp;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.spi.OutboxAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.spi.StubStateAmp;

import io.baratine.pipe.PipePub;
import io.baratine.service.Result;
import io.baratine.service.ServiceExceptionClosed;
import io.baratine.stream.ResultStream;

/**
 * Baratine actor skeleton
 */
public enum StubStateAmpBean implements StubStateAmp
{
  /**
   * Initial state before onInit.
   */
  NEW {
    @Override
    public StubStateAmp load(StubAmp stub,
                             InboxAmp inbox,
                             MessageAmp msg)
    {
      if (stub.isJournalReplay()) {
        return onInitImpl(stub, inbox, msg, INIT_REPLAY);
      }
      else {
        return onInitImpl(stub, inbox, msg, INIT);
      }
    }

    @Override
    public StubStateAmp loadReplay(StubAmp stub,
                                InboxAmp inbox,
                                MessageAmp msg)
    {
      stub.queuePendingReplayMessage(msg);
      msg = null;

      return onInitImpl(stub, inbox, msg, INIT_REPLAY);
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
    public void flushPending(StubAmp stub, InboxAmp inbox)
    {
    }
  },

  /**
   * onInit completed, before the onLoad.
   */
  INIT {
    @Override
    public StubStateAmp load(StubAmp stub, 
                          InboxAmp inbox,
                          MessageAmp msg)
    {
      stub.beforeBatchImpl();

      if (stub.isAutoCreate()) {
        return onLoadImpl(stub, inbox, msg, LOAD, LOAD);
      }
      else {
        return onLoadImpl(stub, inbox, msg, LOAD, UNLOAD);
      }
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
    public void flushPending(StubAmp stub, InboxAmp inbox)
    {
    }
  },

  /**
   * onLoad, waiting for the onActive to complete.
   */
  LOAD {
    @Override
    public StubStateAmp load(StubAmp stub, 
                             InboxAmp inbox, 
                             MessageAmp msg)
    {
      return onActiveImpl(stub, inbox, msg, ACTIVE);
    }

    @Override
    public StubStateAmp loadReplay(StubAmp stub,
                                   InboxAmp inbox,
                                   MessageAmp msg)
    {
      return this;
    }
  },

  /**
   * onLoad, waiting for the onActive to complete.
   */
  UNLOAD {
    @Override
    public StubStateAmp load(StubAmp stub, 
                             InboxAmp inbox, 
                             MessageAmp msg)
    {
      return onActiveImpl(stub, inbox, msg, ACTIVE_UNLOAD);
    }

    @Override
    public StubStateAmp loadReplay(StubAmp stub,
                                   InboxAmp inbox,
                                   MessageAmp msg)
    {
      return this;
    }
  },

  /**
   * Loaded and active.
   */
  ACTIVE {
    @Override
    public StubStateAmp load(StubAmp stub, 
                             InboxAmp inbox, 
                             MessageAmp msg)
    {
      return this;
    }

    @Override
    public void onModify(StubAmp stub)
    {
      StubAmpBase stubBean = (StubAmpBase) stub;

      stubBean.state(MODIFY);

      stubBean.addModifiedChild(stub);
    }

    @Override
    public void onDelete(StubAmp stub)
    {
      StubAmpBase stubBean = (StubAmpBase) stub;

      stubBean.state(DELETE);

      stubBean.addModifiedChild(stub);
    }
    
    @Override
    public boolean isActive()
    {
      return true;
    }
  },

  /**
   * active, but load failed.
   */
  ACTIVE_UNLOAD {
    @Override
    public StubStateAmp load(StubAmp stub, 
                             InboxAmp inbox, 
                             MessageAmp msg)
    {
      return StubStateAmpUnload.STATE;
    }

    @Override
    public void onModify(StubAmp stub)
    {
      StubAmpBase stubBean = (StubAmpBase) stub;

      stubBean.state(MODIFY);

      stubBean.addModifiedChild(stub);
    }
    
    @Override
    public boolean isActive()
    {
      return true;
    }
  },

  MODIFY {
    @Override
    public void onSave(StubAmp stub, Result<Void> result)
    {
      stub.state(ACTIVE);

      stub.onSave(result);
    }
    
    @Override
    public void onSaveComplete(StubAmp stub)
    {
      stub.state(ACTIVE);
    }

    @Override
    public void onDelete(StubAmp stub)
    {
      StubAmpBase stubBean = (StubAmpBase) stub;

      stubBean.state(DELETE);
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

  DELETE {
    @Override
    public StubStateAmp load(StubAmp stub, 
                             InboxAmp inbox, 
                             MessageAmp msg)
    {
      return StubStateAmpUnload.STATE;
    }

    @Override
    public void onSave(StubAmp stub, Result<Void> result)
    {
      //System.out.println("DELSV: " + stub);
      //stub.state(ACTIVE_UNLOAD);
      
      stub.onSave(result);
    }
    
    @Override
    public void onSaveComplete(StubAmp stub)
    {
      stub.state(ACTIVE_UNLOAD);
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
    
    @Override
    public boolean isDelete()
    {
      return true;
    }
  },

  PENDING {
    @Override
    public StubStateAmp load(StubAmp stub, 
                             InboxAmp inbox,
                             MessageAmp msg)
    {
      StubAmpBase stubBean = (StubAmpBase) stub;

      stubBean.queuePendingMessage(msg);

      return StubStateAmpNull.STATE;
    }

    @Override
    public StubStateAmp loadReplay(StubAmp stub,
                                   InboxAmp inbox,
                                   MessageAmp msg)
    {
      StubAmpBase stubBean = (StubAmpBase) stub;

      stubBean.queuePendingReplayMessage(msg);

      return new StubStateAmpNull();
    }
  },

  FAIL {
    @Override
    public StubStateAmp load(StubAmp stub,
                             InboxAmp inbox,
                             MessageAmp msg)
    {
      System.out.println("XXX-FAIL: " + this);

      return StubStateAmpNull.STATE;
    }
  },

  DESTROY {
    @Override
    public StubStateAmp load(StubAmp stub, 
                             InboxAmp inbox,
                             MessageAmp msg)
    {
      return this;
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
    public void flushPending(StubAmp stub, InboxAmp inbox)
    {
    }
    
    @Override
    public void query(StubAmp stubDeliver,
                      StubAmp stubMessage,
                      MethodAmp method,
                      HeadersAmp headers,
                      Result<?> result)
    {
      RuntimeException exn
      = new ServiceExceptionClosed(stubMessage + " for method " + method);
    exn.fillInStackTrace();
    
    result.fail(exn);
    }
    
    @Override
    public void query(StubAmp stubDeliver,
                       StubAmp stubMessage,
                       MethodAmp method,
                       HeadersAmp headers,
                       Result<?> result,
                       Object arg0)
    {
      RuntimeException exn
        = new ServiceExceptionClosed(stubMessage + " for method " + method);
      exn.fillInStackTrace();
  
      result.fail(exn);
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
      RuntimeException exn
        = new ServiceExceptionClosed(stubMessage + " for method " + method);
      exn.fillInStackTrace();
  
      result.fail(exn);
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
      RuntimeException exn
        = new ServiceExceptionClosed(stubMessage + " for method " + method);
      exn.fillInStackTrace();
  
      result.fail(exn);
    }
    
    @Override
    public void query(StubAmp stubDeliver,
                       StubAmp stubMessage,
                       MethodAmp method,
                       HeadersAmp headers,
                       Result<?> result, 
                       Object[] args)
    {
      RuntimeException exn
        = new ServiceExceptionClosed(stubMessage + " for method " + method);
      exn.fillInStackTrace();
  
      result.fail(exn);
    }
    
    @Override
    public void stream(StubAmp stubDeliver,
                        StubAmp stubMessage,
                        MethodAmp method,
                        HeadersAmp headers,
                        ResultStream<?> result, 
                        Object[] args)
    {
      RuntimeException exn
        = new ServiceExceptionClosed(stubMessage + " for method " + method);
      exn.fillInStackTrace();
  
      result.fail(exn);
    }
    
    @Override
    public void outPipe(StubAmp stubDeliver,
                        StubAmp stubMessage,
                        MethodAmp method,
                        HeadersAmp headers,
                        PipePub<?> result, 
                        Object[] args)
    {
      RuntimeException exn
        = new ServiceExceptionClosed(stubMessage + " for method " + method);
      exn.fillInStackTrace();
  
      result.fail(exn);
    }
  },

  /**
   * onInit completed during a journal replay.
   */
  INIT_REPLAY {
    @Override
    public StubStateAmp load(StubAmp stub,
                             InboxAmp inbox,
                             MessageAmp msg)
    {
      stub.beforeBatchImpl();

      return onLoadImpl(stub, inbox, msg, REPLAY);
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
    public void flushPending(StubAmp stub, InboxAmp inbox)
    {
    }

    @Override
    public StubStateAmpBean toActive(StubAmp stub)
    {
      return INIT_REPLAY_ACTIVE;
    }
  },

  INIT_REPLAY_ACTIVE {
    @Override
    public StubStateAmp load(StubAmp stub,
                             InboxAmp inbox,
                             MessageAmp msg)
    {
      stub.beforeBatchImpl();

      return onLoadImpl(stub, inbox, msg, REPLAY_ACTIVE);
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
    public void flushPending(StubAmp stub, InboxAmp inbox)
    {
    }
  },

  REPLAY {
    @Override
    public StubStateAmp load(StubAmp stub,
                             InboxAmp inbox,
                             MessageAmp msg)
    {
      if (msg != null) {
        stub.queuePendingMessage(msg);
      }

      return StubStateAmpNull.STATE;
    }

    @Override
    public void flushPending(StubAmp stub, InboxAmp inbox)
    {
      stub.deliverPendingReplay(inbox);
    }

    @Override
    public StubStateAmp loadReplay(StubAmp stub,
                                   InboxAmp inbox,
                                   MessageAmp msg)
    {
      stub.state(REPLAY_MODIFY);

      return this;
    }

    @Override
    public void onActive(StubAmp stub, InboxAmp inbox)
    {
      onActiveImpl(stub, inbox, null, ACTIVE);
    }

    @Override
    public StubStateAmpBean toActive(StubAmp stub)
    {
      return REPLAY_ACTIVE;
    }
  },

  REPLAY_MODIFY {
    @Override
    public StubStateAmp load(StubAmp stub,
                             InboxAmp inbox,
                             MessageAmp msg)
    {
      StubAmpBase actorBean = (StubAmpBase) stub;

      if (msg != null) {
        actorBean.queuePendingMessage(msg);
      }

      return StubStateAmpNull.STATE;
    }

    @Override
    public void flushPending(StubAmp stub, InboxAmp inbox)
    {
      StubAmpBase stubBase = (StubAmpBase) stub;

      stubBase.deliverPendingReplay(inbox);
    }

    @Override
    public StubStateAmp loadReplay(StubAmp stub, 
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
    public void onActive(StubAmp stub, InboxAmp inbox)
    {
      onActiveImpl(stub, inbox, null, MODIFY);
    }

    @Override
    public StubStateAmpBean toActive(StubAmp stub)
    {
      return REPLAY_ACTIVE;
    }
  },

  REPLAY_ACTIVE {
    @Override
    public StubStateAmp load(StubAmp stub,
                             InboxAmp inbox,
                             MessageAmp msg)
    {
      return onActiveImpl(stub, inbox, null, ACTIVE);
    }

    @Override
    public void flushPending(StubAmp stub, InboxAmp inbox)
    {
      StubAmpBase stubBase = (StubAmpBase) stub;

      stubBase.deliverPendingReplay(inbox);
    }

    @Override
    public StubStateAmp loadReplay(StubAmp stub,
                                   InboxAmp inbox,
                                   MessageAmp msg)
    {
      return this;
    }
  };

  private static final Logger log
    = Logger.getLogger(StubStateAmpBean.class.getName());

  @Override
  public void onSave(StubAmp stub, Result<Void> result)
  {
    stub.onSave(result);
  }

  @Override
  public StubStateAmp load(StubAmp stub,
                        InboxAmp inbox,
                        MessageAmp msg)
  {
    return this;
  }

  @Override
  public StubStateAmp loadReplay(StubAmp stub, 
                                 InboxAmp inbox,
                                 MessageAmp msg)
  {
    throw new IllegalStateException(this + " " + stub + " " + msg);
  }

  @Override
  public void onModify(StubAmp stub)
  {
  }
  
  @Override
  public void flushPending(StubAmp stub, InboxAmp inbox)
  {
    stub.deliverPendingMessages(inbox);
  }
  
  public StubStateAmpBean toActive(StubAmp stub)
  {
    return this;
  }

  @Override
  public void shutdown(StubAmp stub, ShutdownModeAmp mode)
  {
    stub.state(StubStateAmpBean.DESTROY);
    
    stub.onShutdown(mode);
  }
  
  
  private static StubStateAmp onInitImpl(StubAmp stub, 
                                         InboxAmp inbox,
                                         MessageAmp msg, 
                                         StubStateAmpBean nextState)
  {
    StubStatePending pending
      = new StubStatePending(nextState, stub, inbox, msg);
    
    stub.state(pending);
    
    stub.onInit(pending);
    
    return pending.onPendingNext(stub, msg);
  }

  private static StubStateAmp onLoadImpl(StubAmp stub,
                                         InboxAmp inbox,
                                         MessageAmp msg, 
                                         StubStateAmpBean nextState)
  {
    return onLoadImpl(stub, inbox, msg, nextState, nextState);
  }

  private static StubStateAmp onLoadImpl(StubAmp stub,
                                         InboxAmp inbox,
                                         MessageAmp msg, 
                                         StubStateAmpBean nextState,
                                         StubStateAmpBean nextStateFail)
  {
    StubStatePending pending
      = new StubStatePending(nextState, nextStateFail, stub, inbox, msg);
    
    stub.state(pending);
    
    stub.onLoad(pending);
    
    return pending.onPendingNext(stub, msg);
  }

  private static StubStateAmp onActiveImpl(StubAmp stub, 
                                           InboxAmp inbox,
                                           MessageAmp msg, 
                                           StubStateAmpBean nextState)
  {
    StubStatePending pending
      = new StubStatePending(nextState, stub, inbox, msg);
    
    stub.state(pending);

    stub.onActive(pending);

    return pending.onPendingNext(stub, msg);
  }
  
  private static class StubStatePending implements StubStateAmp, Result<Boolean>
  {
    private StubStateAmpBean _nextStateOk;
    private StubStateAmpBean _nextStateFalse;
    
    private StubAmp _stub;
    private InboxAmp _inbox;
    private MessageAmp _msg;
    private Result<Void> _saveResult;
    
    private boolean _isComplete;
    private boolean _isPending;
    private boolean _isSuccess;
    
    StubStatePending(StubStateAmpBean nextState,
                     StubAmp stub,
                     InboxAmp inbox,
                     MessageAmp msg)
    {
      this(nextState, nextState, stub, inbox, msg);
    }
    
    StubStatePending(StubStateAmpBean nextState,
                     StubStateAmpBean nextStateFalse,
                     StubAmp stub,
                     InboxAmp inbox,
                     MessageAmp msg)
    {
      Objects.requireNonNull(inbox);
      Objects.requireNonNull(stub);
      Objects.requireNonNull(nextState);
      Objects.requireNonNull(nextStateFalse);
      
      _nextStateOk = nextState;
      _nextStateFalse = nextStateFalse;
      _stub = stub;
      
      _inbox = inbox;
      _msg = msg;
    }

    StubStateAmpBean nextState(boolean isSuccess)
    {
      return isSuccess ? _nextStateOk : _nextStateFalse;
    }
    
    @Override
    public StubStateAmp load(StubAmp stub,
                             InboxAmp inbox,
                             MessageAmp msg)
    {
      StubAmpBase stubBean = (StubAmpBase) stub;

      stubBean.queuePendingMessage(msg);

      return StubStateAmpNull.STATE;
    }

    @Override
    public StubStateAmp loadReplay(StubAmp stub, 
                                   InboxAmp inbox, 
                                   MessageAmp msg)
    {
      StubAmpBase stubBean = (StubAmpBase) stub;

      stubBean.queuePendingReplayMessage(msg);

      return StubStateAmpNull.STATE;
    }

    @Override
    public void onModify(StubAmp stub)
    {
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
    public void onSave(StubAmp stub, Result<Void> result)
    {
      StubAmpBase stubBean = (StubAmpBase) stub;
      
      stubBean.queuePendingSave(result);
    }

    @Override
    public void onActive(StubAmp stub, InboxAmp inbox)
    {
      _nextStateOk = _nextStateOk.toActive(stub);
    }
    
    @Override
    public void handle(Boolean result, Throwable exn)
    {
      _isComplete = true;
      _isSuccess = ! Boolean.FALSE.equals(result);
      
      if (exn != null) {
        log.warning("@OnLoad: " + _stub + " " + exn);
        
        if (log.isLoggable(Level.FINER)) {
          log.log(Level.FINER, "@OnLoad: " + _stub + " " + exn.toString(), exn);
        }
      }
      
      if (_isPending) {
        complete(_stub, _msg, _isSuccess);
      }
    }
    
    public StubStateAmp onPendingNext(StubAmp stub, MessageAmp msg)
    {
      if (_isComplete) {
        stub.state(nextState(_isSuccess));

        stub.state().flushPending(stub, _inbox);
        
        if (_saveResult != null) {
          stub.state().onSave(stub, _saveResult);
        }
        
        return stub.load(_inbox, msg); 
      }
      else {
        _isPending = true;
        
        _stub = stub;
        stub.queuePendingMessage(msg);
        
        return new StubStateAmpNull();
      }
    }
    
    void complete(StubAmp stubBean, 
                  MessageAmp msg,
                  boolean isSuccess)
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
        stubBean.state(nextState(isSuccess));

        stubBean.state().flushPending(stubBean, _inbox);
        stubBean.load(_inbox, null);
        //actorBean.load(msg);
      
        if (_saveResult != null) {
          stubBean.state().onSave(stubBean, _saveResult);
        }

        /*
         // baratine/10e8
        if (msg != null) {
          actorBean.queuePendingMessage(msg);
        }
        */

        stubBean.state().flushPending(stubBean, _inbox);
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
      return getClass().getSimpleName() + "[" + _nextStateOk + "]";
    }
  }
}
