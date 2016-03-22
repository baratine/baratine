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

import java.util.ArrayList;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.ActorAmpState;
import com.caucho.v5.amp.spi.InboxAmp;
import com.caucho.v5.amp.spi.LoadState;
import com.caucho.v5.amp.spi.MessageAmp;

import io.baratine.service.Result;

/**
 * Baratine actor skeleton
 */
public class StubAmpStateBase extends StubAmpBase implements ActorAmpState
{
  private static final Logger log
    = Logger.getLogger(StubAmpStateBase.class.getName());
  
  private PendingMessages _pendingMessages;
  
  @Override
  public LoadState createLoadState()
  {
    return LoadStateActorAmp.NEW;
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
    loadState().beforeBatch(this);
  }
  
  public void beforeBatchImpl()
  {
  }
  
  @Override
  public void afterBatch()
  {
    loadState().afterBatch(this);
  }
  
  public void afterBatchImpl()
  {
  }
  
  
  @Override
  public boolean isJournalReplay()
  {
    return getJournal() != null;
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

  void queuePendingSave(SaveResult saveResult)
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
    private SaveResult _saveResult;
    
    boolean addReplay(MessageAmp msg)
    {
      boolean isNew = _pendingReplay.size() == 0;
      
      _pendingReplay.add(msg);
      
      return isNew;
    }
    
    public void addSave(SaveResult saveResult)
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
          msg.invoke(inbox, StubAmpStateBase.this);
        } catch (Exception e) {
          e.printStackTrace();
          log.log(Level.WARNING, e.toString(), e);
        }
      }
    }
    
    void deliverSave()
    {
      SaveResult saveResult = _saveResult;
      
      if (saveResult != null) {
        _saveResult = null;
        
        loadState().onSave(StubAmpStateBase.this, saveResult);
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
          msg.invoke(inbox, StubAmpStateBase.this);
        } catch (Exception e) {
          e.printStackTrace();
          log.log(Level.WARNING, e.toString(), e);
        }
      }
      
    }
  }
}
