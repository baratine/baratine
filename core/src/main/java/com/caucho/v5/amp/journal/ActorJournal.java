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

package com.caucho.v5.amp.journal;

import io.baratine.service.Result;

import java.util.Objects;

import com.caucho.v5.amp.deliver.QueueDeliver;
import com.caucho.v5.amp.message.OnSaveRequestMessage;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.LoadState;
import com.caucho.v5.amp.spi.MessageAmp;
import com.caucho.v5.amp.stub.MethodAmp;
import com.caucho.v5.amp.stub.StubAmp;
import com.caucho.v5.amp.stub.StubAmpBase;
import com.caucho.v5.amp.thread.ThreadPool;

/**
 * Journaling actor
 */
public final class ActorJournal extends StubAmpBase
  // implements ActorAmp
{
  private final StubAmp _actor;
  private final JournalAmp _journal;
  private InboxAmp _inbox;
  private JournalAmp _toPeerJournal;
  private JournalAmp _fromPeerJournal;
  private LoadStateJournal _loadState;
  
  public ActorJournal(StubAmp actor, 
                      JournalAmp journal,
                      JournalAmp toPeerJournal,
                      JournalAmp fromPeerJournal)
  {
    Objects.requireNonNull(actor);
    Objects.requireNonNull(journal);
    
    _actor = actor;
    _journal = journal;
    
    _toPeerJournal = toPeerJournal;
    _fromPeerJournal = fromPeerJournal;
    
    _loadState = new LoadStateJournal(this);
  }
  
  @Override
  public LoadState loadState()
  {
    return _loadState;
  }
  
  @Override
  public StubAmp worker(StubAmp actor)
  {
    return this;
  }
  
  /*
  @Override
  public LoadState load(MessageAmp msg, ActorAmp actor)
  {
    return getLoadState();
  }
  */
  
  @Override
  public LoadState load(StubAmp actorMessage, MessageAmp msg)
  {
    return loadState();
  }
  
  @Override
  public LoadState load(MessageAmp msg)
  {
    return loadState();
  }
  
  public void setInbox(InboxAmp inbox)
  {
    _inbox = inbox;
    
    getJournal().setInbox(inbox);
  }

  public InboxAmp getInbox()
  {
    return _inbox;
  }
  
  @Override
  public boolean isUp()
  {
    return _actor.isUp();
  }
  
  @Override
  public boolean isPrimary()
  {
    return false;
  }
  
  @Override
  public boolean isLifecycleAware()
  {
    return true;
  }
  
  @Override
  public JournalAmp getJournal()
  {
    return _journal;
  }

  public JournalAmp getToPeerJournal()
  {
    return _toPeerJournal;
  }
  
  @Override
  public boolean isStarted()
  {
    return false;
  }
  
  @Override
  public void replay(InboxAmp inbox,
                     QueueDeliver<MessageAmp> queue,
                     Result<Boolean> result)
  {
    JournalTask task = new JournalTask(inbox, queue, result);
    ThreadPool.current().execute(task);
  }

  /**
   * Journal getMethod returns null because the replay bypasses the journal.
   */
  @Override
  public MethodAmp getMethod(String methodName)
  {
    /*
    MethodAmp method = _actor.getMethod(methodName);
    
    return method;
    */
    
    MethodAmp method = _actor.getMethod(methodName);
    
    return new MethodJournal(method, 
                             _journal,
                             _toPeerJournal,
                             _inbox);
  }

  @Override
  public void beforeBatch()
  {
  }

  @Override
  public void afterBatch()
  {
    _journal.flush();
    
    if (_toPeerJournal != null) {
      _toPeerJournal.flush();
    }
    
    if (_journal.isSaveRequest()) {
      _inbox.offerAndWake(new OnSaveRequestMessage(_inbox, Result.ignore()), 0);
    }
  }

  @Override
  public <V> void onComplete(Result<V> result, V value)
  {
  }
  
  @Override
  public void onFail(Result<?> result, Throwable exn)
  {
  }
  
  @Override
  public <T> boolean complete(Result<T> result, T value)
  {
    return false;
  }
  
  @Override
  public boolean fail(Result<?> result, Throwable exn)
  {
    result.fail(exn);
    
    return false;
  }
  
  @Override
  public boolean onSave(Result<Boolean> cont)
  {
    if (! _journal.saveStart()) {
      return false;
    }
    
    if (_toPeerJournal != null) {
      _toPeerJournal.saveStart();
    }
    
    return true;
  }
  
  @Override
  public void checkpointEnd(boolean isValid)
  {
    _journal.saveEnd(isValid);
    
    if (_toPeerJournal != null) {
      _toPeerJournal.saveEnd(isValid);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _actor + "]";
  }
  
  private class JournalTask implements Runnable {
    private InboxAmp _inbox;
    private QueueDeliver<MessageAmp> _queue;
    private Result<Boolean> _result;
    
    JournalTask(InboxAmp inbox,
                QueueDeliver<MessageAmp> queue, 
                Result<Boolean> result)
    {
      Objects.requireNonNull(inbox);
      Objects.requireNonNull(queue);
      Objects.requireNonNull(result);
      
      _inbox = inbox;
      _queue = queue;
      _result = result;
    }
    
    @Override
    public void run()
    {
      try {
        if (_fromPeerJournal != null) {
          long peerSequence = _fromPeerJournal.getReplaySequence();
          long selfSequence = _journal.getReplaySequence();
          
          if (peerSequence < selfSequence) {
            _journal.replayStart(_result, _inbox, _queue);
          }
          else if (selfSequence < peerSequence) {
            _fromPeerJournal.replayStart(_result, _inbox, _queue);
          }
          else {
            _fromPeerJournal.replayStart(null, _inbox, _queue);
            _journal.replayStart(_result, _inbox, _queue);
          }
        }
        else {
          _journal.replayStart(_result, _inbox, _queue);
        }
    
      } catch (Throwable e) {
        _result.fail(e);
      }
    }
  }
}
