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

package com.caucho.v5.amp.inbox;

import java.util.concurrent.atomic.AtomicReference;

import com.caucho.v5.amp.spi.ShutdownModeAmp;

/**
 * state for multiworker init/close
 */
class DeliverInboxState
{
  private final AtomicReference<StateCode> _stateRef
    = new AtomicReference<>(StateCode.IDLE);
    
  private ShutdownModeAmp _mode;
  
  boolean toInit()
  {
    return _stateRef.get().toInit(_stateRef);
  }
  
  boolean toActive()
  {
    return _stateRef.get().toActive(_stateRef);
  }

  boolean toShutdown(ShutdownModeAmp mode)
  {
    _mode = mode;
    
    return _stateRef.get().toShutdown(_stateRef);
  }

  public boolean beforeBatch(DeliverInboxState stateSharedRef,
                             DeliverInboxMultiWorker worker)
  {
    if (_stateRef.get() == stateSharedRef._stateRef.get()) {
      return true;
    }
    
    synchronized (worker) {
      StateCode stateSelf = _stateRef.get();
      StateCode stateShared = stateSharedRef._stateRef.get();
      
      if (stateShared.isShutdown()) {
        return false;
      }
      
      boolean isBatch = stateShared.updateBefore(stateSelf, worker);
    
      _stateRef.compareAndSet(stateSelf, stateShared);
      
      return isBatch;
    }
  }
  
  ShutdownModeAmp getMode()
  {
    return _mode;
  }

  public boolean afterBatch(DeliverInboxState stateSharedRef,
                             DeliverInboxMultiWorker worker)
  {
    if (_stateRef.get() == stateSharedRef._stateRef.get()) {
      return true;
    }
    
    synchronized (worker) {
      StateCode stateSelf = _stateRef.get();
      StateCode stateShared = stateSharedRef._stateRef.get();

      _stateRef.compareAndSet(stateSelf, stateShared);
      
      boolean isBatch = stateShared.updateAfter(stateSelf, this, worker);
    
      return isBatch;
    }
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _stateRef.get() + "]";
  }
  
  enum StateCode {
    IDLE {
      @Override
      boolean toInit(AtomicReference<StateCode> stateRef)
      {
        return stateRef.compareAndSet(this, INIT);
      }

      @Override
      void onInit(DeliverInboxMultiWorker worker)
      {
        worker.onInitImpl();
      }

      @Override
      void onActive(DeliverInboxMultiWorker worker)
      {
        worker.onActiveImpl();
      }
    },
    
    INIT {
      @Override
      boolean toInit(AtomicReference<StateCode> stateRef)
      {
        return stateRef.compareAndSet(this, ACTIVE);
      }

      @Override
      boolean updateBefore(StateCode stateSelf, DeliverInboxMultiWorker worker)
      {
        stateSelf.onInit(worker);
        
        return false;
      }

      @Override
      void onActive(DeliverInboxMultiWorker worker)
      {
        worker.onActiveImpl();
      }
    },
    
    ACTIVE {
      @Override
      boolean updateBefore(StateCode stateSelf, DeliverInboxMultiWorker worker)
      {
        stateSelf.onInit(worker);
        stateSelf.onActive(worker);
        
        return false;
      }
    },
    
    SHUTDOWN {
      @Override
      boolean isShutdown()
      {
        return true;
      }
      
      @Override
      public boolean toShutdown(AtomicReference<StateCode> stateRef)
      {
        return false;
      }
      
      @Override
      boolean updateAfter(StateCode stateSelf, 
                          DeliverInboxState inboxState, 
                          DeliverInboxMultiWorker worker)
      {
        stateSelf.onShutdown(worker, inboxState.getMode());
        
        return false;
      }
    };
    
    boolean toInit(AtomicReference<StateCode> stateRef)
    {
      return false;
    }

    boolean isShutdown()
    {
      return false;
    }

    void onInit(DeliverInboxMultiWorker worker)
    {
    }
    
    boolean toActive(AtomicReference<StateCode> stateRef)
    {
      return false;
    }
    
    void onActive(DeliverInboxMultiWorker worker)
    {
    }
    
    boolean toShutdown(AtomicReference<StateCode> stateRef)
    {
      if (stateRef.compareAndSet(this, SHUTDOWN)) {
        return true;
      }
      
      return stateRef.get().toShutdown(stateRef);
    }

    void onShutdown(DeliverInboxMultiWorker worker,
                    ShutdownModeAmp mode)
    {
      worker.onShutdownImpl(mode);
    }
    
    boolean updateBefore(StateCode stateSelf, DeliverInboxMultiWorker worker)
    {
      return true;
    }

    boolean updateAfter(StateCode stateSelf, 
                        DeliverInboxState inboxState, 
                        DeliverInboxMultiWorker worker)
    {
      return true;
    }
  }
}
