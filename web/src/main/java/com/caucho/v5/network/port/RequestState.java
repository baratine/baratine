/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Baratine is finishThread software; you can redistribute it and/or modify
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

package com.caucho.v5.network.port;

import java.util.concurrent.atomic.AtomicReference;

import com.caucho.v5.util.ModulePrivate;

/**
 * State for an external request.
 * 
 * xxx_S are states with detached threads (sleep).
 * xxx_A are states with an attached thread (active).
 * 
 * toXxxWake() requests a thread attachment (sleep to active)
 * toXxxSleep() requests a thread detach (active to sleep)
 */
@ModulePrivate
enum RequestState {
  /**
   * The allocated, ready to accept state
   */
  IDLE_S {
    @Override
    final boolean toAcceptWake(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, ACCEPT_A)) {
        return true;
      }
      else {
        return stateRef.get().toAcceptWake(stateRef);
      }
    }

    @Override
    final public boolean toTimeoutWake(AtomicReference<RequestState> stateRef)
    {
      return false;
    }
    
    @Override
    final boolean toIdleSleep(AtomicReference<RequestState> stateRef)
    {
      return false;
    }
  },
  
  /**
   * The allocated, ready to accept state
   */
  ACCEPT_A {
    @Override
    final public StateConnection getNextState()
    {
      return StateConnection.ACCEPT;
    }

    @Override
    boolean toActive(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, ACTIVE_A)) {
        return true;
      }
      else {
        return stateRef.get().toActive(stateRef);
      }
    }

    @Override
    boolean toIdleSleep(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, IDLE_S)) {
        return true;
      }
      else {
        return stateRef.get().toIdleSleep(stateRef);
      }
    }

    @Override
    final public boolean toTimeoutWake(AtomicReference<RequestState> stateRef)
    {
      return false;
    }
  },

  /**
   * Handling a request
   */
  ACTIVE_A {
    @Override
    final public StateConnection getNextState()
    {
      return StateConnection.ACTIVE;
    }

    @Override
    boolean toActive(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    boolean toPollRequested(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, POLL_REQUESTED_A)) {
        return true;
      }
      else  {
        return stateRef.get().toPollRequested(stateRef);
      }
    }

    @Override
    final public boolean toSuspendSleep(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, SUSPEND_S)) {
        return true;
      }
      else {
        return stateRef.get().toSuspendSleep(stateRef);
      }
    }

    @Override
    final public boolean toWake(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, WAKE_A)) {
        return false;
      }
      else {
        return stateRef.get().toWake(stateRef);
      }
    }

    @Override
    final public boolean toTimeoutWake(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, TIMEOUT_A)) {
        return false;
      }
      else {
        return stateRef.get().toTimeoutWake(stateRef);
      }
    }

    @Override
    final public boolean toCloseReadWake(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, CLOSE_READ_A)) {
        return false;
      }
      else {
        return stateRef.get().toCloseReadWake(stateRef);
      }
    }

    @Override
    final public boolean toCloseRead(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, CLOSE_READ_A)) {
        return true;
      }
      else {
        return stateRef.get().toCloseRead(stateRef);
      }
    }
  },

  /**
   * Pre-woken up
   */
  WAKE_A {
    @Override
    final public StateConnection getNextState()
    {
      return StateConnection.ACTIVE;
    }

    @Override
    boolean toPollRequested(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    boolean toPollSleep(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    final public boolean toSuspendSleep(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    final public boolean toWake(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    boolean toActive(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, ACTIVE_A)) {
        return true;
      }
      else {
        return stateRef.get().toActive(stateRef);
      }
    }
  },
 
  /**
   * Keepalive select started, but original thread not done.
   */
  POLL_REQUESTED_A {
    @Override
    final public boolean toWake(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, WAKE_A)) {
        return false;
      }
      else {
        return stateRef.get().toWake(stateRef);
      }
    }

    @Override
    boolean toPollSleep(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, POLL_S)) {
        return true;
      }
      else {
        return stateRef.get().toPollSleep(stateRef);
      }
    }

    @Override
    final public boolean toTimeoutWake(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, TIMEOUT_A)) {
        return false;
      }
      else {
        return stateRef.get().toTimeoutWake(stateRef);
      }
    }

    @Override
    final public boolean toCloseReadWake(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, CLOSE_READ_A)) {
        return false;
      }
      else {
        return stateRef.get().toCloseReadWake(stateRef);
      }
    }

    @Override
    final public boolean toCloseRead(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, CLOSE_READ_A)) {
        return true;
      }
      else {
        return stateRef.get().toCloseRead(stateRef);
      }
    }
  },

  /**
   * poll suspended
   */
  POLL_S {
    @Override
    public boolean isTimeoutCapable()
    {
      return true;
    }
    
    @Override
    final public boolean toWake(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, ACTIVE_A)) {
        return true;
      }
      else {
        return stateRef.get().toWake(stateRef);
      }
    }

    @Override
    final public boolean toTimeoutWake(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, TIMEOUT_A)) {
        return true;
      }
      else {
        return stateRef.get().toTimeoutWake(stateRef);
      }
    }

    @Override
    final public boolean toCloseReadWake(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, CLOSE_READ_A)) {
        return true;
      }
      else {
        return stateRef.get().toCloseReadWake(stateRef);
      }
    }

    @Override
    final public boolean toDestroyWake(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, DESTROY_REQUEST_A)) {
        return true;
      }
      else { 
        return stateRef.get().toDestroyWake(stateRef);
      }
    }
  },

  /**
   * Comet suspend
   */
  SUSPEND_S {
    @Override
    public boolean isTimeoutCapable()
    {
      return true;
    }

    @Override
    public boolean isSuspend()
    {
      return true;
    }

    @Override
    final public boolean toWake(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, WAKE_A)) {
        return true;
      }
      else {
        return stateRef.get().toWake(stateRef);
      }
    }

    @Override
    final public boolean toTimeoutWake(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, TIMEOUT_A)) {
        return true;
      }
      else {
        return stateRef.get().toTimeoutWake(stateRef);
      }
    }

    @Override
    final public boolean toDestroyWake(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, DESTROY_REQUEST_A)) {
        return true;
      }
      else {
        return stateRef.get().toDestroyWake(stateRef);
      }
    }
  },

  /**
   * timeout/disconnect from client.
   */
  TIMEOUT_A {
    // XXX: check with perf on this to make sure a normal disconnect
    // doesn't trigger this
    /*
    @Override
    public boolean isAllowIdle()
    {
      return true;
    }
    */

    @Override
    public boolean isClosed()
    {
      return true;
    }
    
    @Override
    final public StateConnection getNextState()
    {
      return StateConnection.TIMEOUT;
    }

    @Override
    public boolean toTimeoutWake(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    boolean toPollRequested(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    boolean toPollSleep(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    final public boolean toWake(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    final public boolean toCloseReadWake(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    final public boolean toCloseRead(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, CLOSE_READ_A)) {
        return true;
      }
      else {
        return stateRef.get().toCloseRead(stateRef);
      }
    }
  },

  /**
   * half-closed state
   */
  CLOSE_READ_A {
    @Override
    final public StateConnection getNextState()
    {
      return StateConnection.CLOSE_READ_A;
    }
    
    @Override
    final public boolean toWake(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    final public boolean toTimeoutWake(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    final public boolean toCloseReadWake(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    final public boolean toCloseRead(AtomicReference<RequestState> stateRef)
    {
      return false;
    }
  },

  /**
   * half-closed state
   */
  DESTROY_REQUEST_A {
    @Override
    final public StateConnection getNextState()
    {
      return StateConnection.DESTROY;
    }
    
    @Override
    final public boolean toWake(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    final public boolean toTimeoutWake(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    final public boolean toCloseReadWake(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    final public boolean toCloseRead(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    final public boolean toDestroyWake(AtomicReference<RequestState> stateRef)
    {
      return false;
    }
  },

  /**
   * closed state
   */
  CLOSE_A {
    @Override
    public boolean isClosed()
    {
      return true;
    }

    @Override
    boolean toPollSleep(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    final public boolean toWake(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    final public boolean toCloseReadWake(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    final public boolean toCloseRead(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    boolean toIdleSleep(AtomicReference<RequestState> stateRef)
    {
      if (stateRef.compareAndSet(this, IDLE_S)) {
        return true;
      }
      else {
        return stateRef.get().toIdleSleep(stateRef);
      }
    }

    @Override
    boolean toPollRequested(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    final public boolean toTimeoutWake(AtomicReference<RequestState> stateRef)
    {
      return false;
    }

    @Override
    final public boolean toClose(AtomicReference<RequestState> stateRef)
    {
      return false;
    }
  },

    /**
     * destroyed state
     */
    DESTROY {
      @Override
      public boolean isClosed()
      {
        return true;
      }

      @Override
      public boolean isDestroyed()
      {
        return true;
      }

      @Override
      boolean toPollSleep(AtomicReference<RequestState> stateRef)
      {
        // keepalive suspend can complete, because the new thread will
        // finish the close
        return false;
      }

      @Override
      boolean toPollRequested(AtomicReference<RequestState> stateRef)
      {
        return false;
      }
      
      @Override
      final public boolean toWake(AtomicReference<RequestState> stateRef)
      {
        return false;
      }

      @Override
      final public boolean toTimeoutWake(AtomicReference<RequestState> stateRef)
      {
        return false;
      }

      @Override
      final public boolean toCloseRead(AtomicReference<RequestState> stateRef)
      {
        return false;
      }

      @Override
      final public boolean toCloseReadWake(AtomicReference<RequestState> stateRef)
      {
        return false;
      }

      @Override
      final public boolean toClose(AtomicReference<RequestState> stateRef)
      {
        // cloud/0420
        // XXX: allow close transition, because double closes shouldn't be
        // an issue. Since a destroyed connection has something wrong with it
        // (or the server is shutting down), the double close is ok.
        return false;
      }

      @Override
      final public boolean toDestroyWake(AtomicReference<RequestState> stateRef)
      {
        return false;
      }
      
      final public boolean toDestroy(AtomicReference<RequestState> stateRef)
      {
        return false;
      }
  };

  public boolean isAsyncWake()
  {
    return false;
  }
  
  public StateConnection getNextState()
  {
    return StateConnection.DESTROY;
  }

  boolean toIdleSleep(AtomicReference<RequestState> stateRef)
  {
    return false;
  }

  boolean toAcceptWake(AtomicReference<RequestState> stateRef)
  {
    throw new IllegalStateException(toString());
  }

  boolean toActive(AtomicReference<RequestState> stateRef)
  {
    throw new IllegalStateException(toString());
  }

  boolean toPollRequested(AtomicReference<RequestState> stateRef)
  {
    throw new IllegalStateException(toString());
  }

  boolean toPollSleep(AtomicReference<RequestState> stateRef)
  {
    throw new IllegalStateException(toString());
  }

  public boolean toSuspendSleep(AtomicReference<RequestState> stateRef)
  {
    throw new IllegalStateException(toString());
  }

  public boolean toWake(AtomicReference<RequestState> stateRef)
  {
    throw new IllegalStateException(toString());
  }

  public boolean toTimeoutWake(AtomicReference<RequestState> stateRef)
  {
    throw new IllegalStateException(toString());
  }

  public boolean toCloseReadWake(AtomicReference<RequestState> stateRef)
  {
    throw new IllegalStateException(toString());
  }

  public boolean toCloseRead(AtomicReference<RequestState> stateRef)
  {
    throw new IllegalStateException(toString());
  }

  public boolean toClose(AtomicReference<RequestState> stateRef)
  {
    if (stateRef.compareAndSet(this, CLOSE_A)) {
      return true;
    }
    else {
      return stateRef.get().toClose(stateRef);
    }
  }

  public boolean toDestroyWake(AtomicReference<RequestState> stateRef)
  {
    if (stateRef.compareAndSet(this, DESTROY_REQUEST_A)) {
      return false;
    }
    else {
      return stateRef.get().toDestroyWake(stateRef);
    }
  }

  public boolean toDestroy(AtomicReference<RequestState> stateRef)
  {
    if (stateRef.compareAndSet(this, DESTROY)) {
      return true;
    }
    else {
      return stateRef.get().toDestroy(stateRef);
    }
  }

  public boolean isDestroyed()
  {
    return false;
  }

  public boolean isClosed()
  {
    return false;
  }

  public boolean isIdle()
  {
    return false;
  }

  public boolean isActive()
  {
    return false;
  }

  public boolean isSuspend()
  {
    return false;
  }

  public boolean isTimeoutCapable()
  {
    return false;
  }
}
