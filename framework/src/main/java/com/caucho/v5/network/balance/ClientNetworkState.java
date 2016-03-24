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

package com.caucho.v5.network.balance;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.caucho.v5.util.CurrentTime;

/**
 * State of a network connection to a target server.
 * 
 * <h3>Fail Recover Time</h3>
 * 
 * The fail recover time is dynamic. The first timeout is 1s. After the 1s,
 * the client tries again. If that fails, the timeout is doubled until
 * reaching the maximum _loadBalanceRecoverTime.
 */
public class ClientNetworkState
{
  private final String _id;

  private final long _recoverTimeout;

  private final AtomicReference<State> _state
    = new AtomicReference<State>(State.ACTIVE);
  
  private AtomicLong _dynamicRecoverTimeout
    = new AtomicLong();
  
  private AtomicInteger _connectionCount = new AtomicInteger();

  // load management data
  private volatile long _firstFailTime;
  private volatile long _lastFailTime;

  private volatile long _firstSuccessTime;
  
  public ClientNetworkState(String id,
                            long recoverTimeout)
  {
    _id = id;
    
    _recoverTimeout = recoverTimeout;
  }

  /**
   * Returns the user-readable id of the target server.
   */
  public String getId()
  {
    return _id;
  }
  
  /**
   * Return the max recover time.
   */
  public long getRecoverTimeout()
  {
    return _recoverTimeout;
  }

  /**
   * Returns the lifecycle state.
   */
  public final String getState()
  {
    // updateWarmup();

    return String.valueOf(_state);
  }

  /**
   * Returns true if the server is active.
   */
  public final boolean isActive()
  {
    return _state.get().isActive();
  }

  /**
   * Return true if enabled.
   */
  public boolean isEnabled()
  {
    return _state.get().isEnabled();
  }

  /**
   * Returns true if the server is dead.
   */
  public final boolean isDead()
  {
    return ! isActive();
  }
  
  //
  // action callbacks
  //

  /**
   * Enable the client.
   */
  public void enable()
  {
    toState(State.ENABLED);
  }

  /**
   * Disable the client.
   */
  public void disable()
  {
    toState(State.DISABLED);

    _firstSuccessTime = 0;
  }

  /**
   * Called when the server has a successful response
   */
  public void onSuccess()
  {
    if (_firstSuccessTime <= 0) {
      _firstSuccessTime = CurrentTime.currentTime();
    }
    
    // reset the connection fail recover time
    _dynamicRecoverTimeout.set(1000L);
    _firstFailTime = 0;
  }

  /**
   * Called when the connection fails.
   */
  public void onFail()
  {
    _lastFailTime = CurrentTime.currentTime();
    
    if (_firstFailTime == 0) {
      _firstFailTime = _lastFailTime;
    }
    _firstSuccessTime = 0;
    
    toState(State.FAIL);
    
    long recoverTimeout = _dynamicRecoverTimeout.get();
    
    long nextRecoverTimeout = Math.min(recoverTimeout + 1000L, _recoverTimeout);
    
    _dynamicRecoverTimeout.compareAndSet(recoverTimeout, nextRecoverTimeout);
  }
  
  /**
   * Start a new connection. Returns true if the connection can be
   * started.
   */
  public boolean startConnection()
  {
    State state = _state.get();
    
    if (state.isActive()) {
      // when active, always start a connection
      
      _connectionCount.incrementAndGet();
      
      return true;
    }
    
    long now = CurrentTime.currentTime();
    long lastFailTime = _lastFailTime;
    long recoverTimeout = _dynamicRecoverTimeout.get();
    
    if (now < lastFailTime + recoverTimeout) {
      // if the fail recover hasn't timed out, return false
      return false;
    }
    
    // when fail, only start a single connection
    int count;

    do {
      count = _connectionCount.get();
      
      if (count > 0) {
        return false;
      }
    } while (! _connectionCount.compareAndSet(count, count + 1));
    
    return true;
  }
  
  public void completeConnection()
  {
    _connectionCount.decrementAndGet();
  }

  /**
   * Close the client
   */
  public void close()
  {
    toState(State.CLOSED);
  }
  
  private State toState(State targetState)
  {
    State oldState;
    State newState;

    do {
      oldState = _state.get();

      newState = targetState.toState(oldState);
    } while (! _state.compareAndSet(oldState, newState));
    
    return _state.get();
  }

  @Override
  public String toString()
  {
    return (getClass().getSimpleName() + "[" + getId() + "]");
  }

  enum State {
    ACTIVE {
      boolean isActive() { return true; }
      boolean isEnabled() { return true; }

      State toState(State state) { return state.toActive(); }
      
      State toFail() { return FAIL; }
      State toDisable() { return DISABLED; }
    },
    
    FAIL {
      boolean isEnabled() { return true; }
      
      State toState(State state) { return state.toFail(); }
      State toActive() { return ACTIVE; }
      State toDisable() { return DISABLED; }
    },
    
    DISABLED {
      State toState(State state) { return state.toDisable(); }
      
      State toActive() { return this; }
      State toFail() { return this; }
      State toEnable() { return State.ACTIVE; }
    },
    
    ENABLED {
      State toState(State state) { return state.toEnable(); }
    },
    
    CLOSED {
      boolean isClosed() { return true; }
      
      State toState(State state) { return CLOSED; }

      State toActive() { return this; }
      State toFail() { return this; }
      State toEnable() { return this; }
      State toDisable() { return this; }
    };

    boolean isActive() { return false; }
    boolean isEnabled() { return false; }
    boolean isClosed() { return false; }

    State toActive() { return this; }
    State toFail() { return this; }
    
    State toEnable() { return this; }
    State toDisable() { return DISABLED; }
    
    State toState(State state)
    {
      throw new UnsupportedOperationException(toString());
    }
  }
}
