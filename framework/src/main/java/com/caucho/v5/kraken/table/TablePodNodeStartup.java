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

package com.caucho.v5.kraken.table;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.bartender.ServerBartender;

/**
 * State for the startup of a sharding node.
 */

class TablePodNodeStartup
{
  private static final Logger log
    = Logger.getLogger(TablePodNodeStartup.class.getName());

  private final TablePodNode _nodePodTable;

  private boolean _isComplete;
  
  private long _startupLastUpdateTime;

  private UpdateStatus[] _state;

  TablePodNodeStartup(TablePodNode nodePodTable)
  {
    _nodePodTable = nodePodTable;
    
    if (! nodePodTable.isSelf()) {
      _isComplete = true;
    }
    
    if (_nodePodTable.getReplicateSize() <= 1) {
      _isComplete = true;
    }
    
    _state = new UpdateStatus[_nodePodTable.getReplicateSize()];
    
    for (int i = 0; i < _state.length; i++) {
      if (_isComplete) {
        _state[i] = UpdateStatus.OK;
        continue;
      }
      
      _state[i] = UpdateStatus.UNKNOWN;
      
      ServerBartender server = nodePodTable.getServer(i);
      
      if (server != null && server.isSelf()) {
        _state[i] = _state[i].toComplete();
      }
    }

    // _startupLastUpdateTime = getLocalBacking().getStartupLastUpdateTime();
  }
  
  public boolean isComplete()
  {
    return _isComplete;
  }
  
  void clearComplete()
  {
  }
  
  public boolean isFailed()
  {
    for (UpdateStatus status : _state) {
      if (status.isFailed()) {
        return true;
      }
    }
    
    return false;
  }
  
  public void clearFailure()
  {
    for (int i = 0; i < _state.length; i++) {
      _state[i] = _state[i].clearFailure();
    }
  }

  public TablePodNode getNode()
  {
    return _nodePodTable;
  }

  boolean startRequestUpdates(TablePod tablePod,
                              long startupLastUpdateTime)
  {
    _startupLastUpdateTime = startupLastUpdateTime;
    
    requestStartupUpdatesImpl(tablePod);

    updateTriadUpdateComplete();
    
    return isComplete();
  }
  
  private void updateTriadUpdateComplete()
  {
    boolean isComplete = true;
    
    for (UpdateStatus status : _state) {
      if (! status.isComplete()) {
        isComplete = false;
      }
    }
    
    _isComplete = isComplete;
  }

  private void requestStartupUpdatesImpl(TablePod clientKraken)
  {
    for (int i = 0; i < _state.length; i++) {
      UpdateStatus state = _state[i];

      if (state.isComplete()) {
        continue;
      }
      
      if (state.isFailed()) {
        state = UpdateStatus.UNKNOWN;
        _state[i] = state;
      }

      if (state.isStartAllowed()) {
        state = state.toStart();
        
        _state[i] = state;

        clientKraken.startUpdates(this, i);
      }
    }
  }
  
  void onStartupCompleted(int server)
  {
    _state[server] = _state[server].toComplete();
    
    updateTriadUpdateComplete();
  }
  
  void onStartupFailed(int server,
                       Throwable exn)
  {
    if (exn != null) {
      log.fine(exn.toString());
      log.log(Level.FINEST, exn.toString(), exn);
    }
    
    _state[server] = _state[server].toError();
    
    updateTriadUpdateComplete();
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + _nodePodTable.getIndex()
            + "," + _nodePodTable.getTable().getName()
            + "]");
  }
  
  enum UpdateStatus {
    UNKNOWN {
      UpdateStatus toComplete() { return OK; }
      boolean isStartAllowed() { return true; }
      UpdateStatus toStart() { return IN_PROGRESS; }
    },
    IN_PROGRESS {
      UpdateStatus toComplete() { return OK; }
      UpdateStatus toError() { return ERROR; }
    },
    OK {
      boolean isComplete() { return true; }
    },
    ERROR {
      boolean isComplete() { return false; }
      boolean isFailed() { return true; }
      
      UpdateStatus clearFailure() { return UNKNOWN; }
    };
    
    boolean isComplete() { return false; }
    boolean isFailed() { return false; }

    UpdateStatus toComplete() { return this; }
    UpdateStatus toError() { return this; }
    
    boolean isStartAllowed() { return false; }
    UpdateStatus toStart() { throw new IllegalStateException(toString()); }
    
    UpdateStatus clearFailure() { return this; }
  }
}
