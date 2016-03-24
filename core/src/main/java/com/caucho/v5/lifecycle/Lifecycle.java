/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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

package com.caucho.v5.lifecycle;

import static com.caucho.v5.lifecycle.LifecycleState.ACTIVE;
import static com.caucho.v5.lifecycle.LifecycleState.DESTROYED;
import static com.caucho.v5.lifecycle.LifecycleState.DESTROYING;
import static com.caucho.v5.lifecycle.LifecycleState.FAILED;
import static com.caucho.v5.lifecycle.LifecycleState.INIT;
import static com.caucho.v5.lifecycle.LifecycleState.INITIALIZING;
import static com.caucho.v5.lifecycle.LifecycleState.STARTING;
import static com.caucho.v5.lifecycle.LifecycleState.STOPPED;
import static com.caucho.v5.lifecycle.LifecycleState.STOPPED_IDLE;
import static com.caucho.v5.lifecycle.LifecycleState.STOPPING;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.util.CurrentTime;

/**
 * Lifecycle class.
 */
public final class Lifecycle {
  private final Logger _log;
  private String _name;
  
  private Level _level = Level.FINE;
  private Level _lowLevel = Level.FINEST;
  
  private LifecycleState _state = LifecycleState.NEW;

  private long _activeCount;
  private long _failCount;

  private long _lastFailTime;
  private long _lastChangeTime;

  private ArrayList<WeakReference<LifecycleListener>> _listeners;

  /**
   * Creates an anonymous lifecycle.
   */
  public Lifecycle()
  {
    _log = null;
  }

  /**
   * Creates an lifecycle with logger and name.
   */
  public Lifecycle(Logger log)
  {
    _log = log;
  }

  /**
   * Creates an lifecycle with logger and name.
   */
  public Lifecycle(Logger log, String name)
  {
    _log = log;
    _name = name;
  }

  /**
   * Creates an lifecycle with logger,  a name, and a level.
   */
  public Lifecycle(Logger log, String name, Level level)
  {
    _log = log;
    _name = name;
    
    setLevel(level);
  }

  /**
   * Gets the lifecycle name.
   */
  public String getName()
  {
    return _name;
  }

  /**
   * Sets the lifecycle name, and the level to Level.INFO.
   */
  public void setName(String name)
  {
    _name = name;
  }

  /**
   * Gets the lifecycle logging level.
   */
  public Level getLevel()
  {
    return _level;
  }

  /**
   * Sets the lifecycle logging level.
   */
  public void setLevel(Level level)
  {
    _level = level;

    if (level.intValue() < _lowLevel.intValue())
      _lowLevel = level;
  }

  public final LifecycleState getState()
  {
    return _state;
  }
  
  /**
   * Returns the current state.
   */
  public int getStateOrdinal()
  {
    return _state.ordinal();
  }

  /**
   * Returns the state name for the passed state.
   */
  /*
  public static String getStateName(int state)
  {
    switch (state) {
      case IS_NEW:
        return "new";
      case IS_INITIALIZING:
        return "initializing";
      case IS_INIT:
        return "init";
      case IS_STARTING:
        return "starting";
      case IS_ACTIVE:
        return "active";
      case IS_FAILED:
        return "failed";
      case IS_STOPPING:
        return "stopping";
      case IS_STOPPED:
        return "stopped";
      case IS_DESTROYING:
        return "destroying";
      case IS_DESTROYED:
        return "destroyed";
      default:
        return "unknown";
    }
  }
  */

  /**
   * Returns the current state name.
   */
  public String getStateName()
  {
    return getState().toString();
  }

  /**
   * Returns the last lifecycle change time.
   */
  public long getLastChangeTime()
  {
    return _lastChangeTime;
  }

  /**
   * Returns the last failure time.
   */
  public long getLastFailTime()
  {
    return _lastFailTime;
  }

  /**
   * Returns the number of times the lifecycle has switched to active.
   */
  public long getActiveCount()
  {
    return _activeCount;
  }

  /**
   * Returns the number of times the lifecycle has switched to failing.
   */
  public long getFailCount()
  {
    return _failCount;
  }

  /**
   * Returns true for the initializing state.
   */
  public boolean isInitializing()
  {
    return getState().isInitializing();
  }

  /**
   * Returns true for the init state.
   */
  public boolean isInit()
  {
    return getState().isInit();
  }

  /**
   * Returns true for the init state.
   */
  public boolean isBeforeInit()
  {
    return getState().isBeforeInit();
  }

  /**
   * True for any state after initialization.
   */
  public boolean isAfterInit()
  {
    return getState().isAfterInit();
  }

  /**
   * True for an idle state, where any request would transition to an
   * active state.
   */
  public boolean isIdle()
  {
    return getState().isIdle();
  }

  /**
   * Returns true if the service is starting.
   */
  public boolean isStarting()
  {
    return getState().isStarting();
  }

  /**
   * Returns true if the service is starting.
   */
  /*
  public boolean isAfterStarting()
  {
    return getState().isAfterStarting();
  }
  */

  /**
   * Returns true for the warmup state.
   */
  public boolean isWarmup()
  {
    return getState().isWarmup();
  }

  /**
   * Returns true for the initializing state.
   */
  public boolean isBeforeActive()
  {
    return getState().isBeforeActive();
  }

  /**
   * Returns true for the closing states
   */
  /*
  public boolean isAfterActive()
  {
    return getState().isAfterActive();
  }
  */

  /**
   * Wait for a period of time until the service starts.
   */
  public boolean waitForActive(long timeout)
  {
    LifecycleState state = getState();

    if (state.isActive()) {
      return true;
    }
    else if (state.isAfterActive()) {
      return false;
    }
    
    // server/1d2j
    long waitEnd = CurrentTime.getCurrentTimeActual() + timeout;

    synchronized (this) {
      while ((state = _state).isBeforeActive()
             && CurrentTime.getCurrentTimeActual() < waitEnd) {
        if (state.isActive()) {
          return true;
        }
        else if (state.isAfterActive()) {
          return false;
        }

        try {
          long delta = waitEnd - CurrentTime.getCurrentTimeActual();
          
          if (delta > 0) {
            wait(delta);
          }
        } catch (InterruptedException e) {
        }
      }
    }
    
    return _state.isActive();
  }

  /**
   * Returns true for the active state.
   */
  public final boolean isActive()
  {
    return getState().isActive();
  }

  /**
   * Returns true for the a runnable state, including warmup
   */
  public boolean isRunnable()
  {
    return getState().isRunnable();
    /*
    int state = _state.get();
    
    return IS_WARMUP <= state && state <= IS_ACTIVE;
    */
  }

  /**
   * Returns true for the closing states
   */
  public boolean isAfterStopping()
  {
    return getState().isAfterStopping();
  }

  /**
   * Returns true for the failed state.
   */
  public boolean isError()
  {
    return getState().isError();
  }

  /**
   * Returns true for the failed state.
   */
  public boolean isFailed()
  {
    return getState().isError();
  }

  /**
   * True if a stop for a restart would be allowed.
   */
  public boolean isAllowStopFromRestart()
  {
    return getState().isAllowStopFromRestart();
  }

  /**
   * Returns true if the state is stopping.
   */
  public boolean isStopping()
  {
    return getState().isStopping();
  }

  /**
   * Returns true if the state is stopping.
   */
  public boolean isStopped()
  {
    return getState().isStopped();
  }

  /**
   * Returns true if the state is closed
   */
  public boolean isDestroying()
  {
    return getState().isDestroying();
  }

  /**
   * Returns true if the state is closed
   */
  public boolean isDestroyed()
  {
    return getState().isDestroyed();
  }
  
  /**
   * Changes to the initializing state.
   *
   * @return true if the transition is allowed
   */
  public boolean toInitializing()
  {
    return toNextState(INITIALIZING);
  }

  /**
    * Changes to the init state.
    *
    * @return true if the transition is allowed
    */
   public boolean toInit()
   {
     return toNextState(INIT);
   }
  
  /**
    * Changes to the init from the stopped state.
    *
    * @return true if the transition is allowed
    */
   public boolean toPostInit()
   {
     synchronized (this) {
       if (_state == STOPPED) {
         _state = INIT;

         _lastChangeTime = CurrentTime.currentTime();

         notifyListeners(STOPPED, INIT);

         return true;
       }
       else {
         return _state.isInit();
       }
     }
   }

  /**
   * Changes to the starting state.
   *
   * @return true if the transition is allowed
   */
  public boolean toStarting()
  {
    LifecycleState state;
    
    synchronized (this) {
      state = _state;

      if (state.isAfterStarting() && ! state.isStopped()) {
        return false;
      }
      
      _state = STARTING;

      _lastChangeTime = CurrentTime.currentTime();

      if (_log != null && _log.isLoggable(_level) && _log.isLoggable(Level.FINER)) {
        _log.finer("starting " + _name);
      }
    }

    notifyListeners(state, STARTING);

    return true;
  }
  
  /**
   * Changes to the active state.
   *
   * @return true if the transition is allowed
   */
  public boolean toActive()
  {
    LifecycleState state;
    
    synchronized (this) {
      state = _state;

      if (state.isAfterActive() && ! state.isStopped()) {
        return false;
      }
      
      _state = ACTIVE;

      _lastChangeTime = CurrentTime.currentTime();
    }

    if (_log != null && _log.isLoggable(_level))
      _log.log(_level, "active " + _name);

    notifyListeners(state, ACTIVE);
    
    return true;
  }
  
  private void notifyWait()
  {
    synchronized (this) {
      notifyAll();
    }
  }
  
  /**
   * Changes to the error state.
   *
   * @return true if the transition is allowed
   */
  public boolean toError()
  {
    return toFail();
  }
  
  /**
   * Changes to the failed state.
   *
   * @return true if the transition is allowed
   */
  public boolean toFail()
  {
    LifecycleState state;

    synchronized (this) {
      state = _state;
      
      if (state.isAfterDestroying()) {
        return false;
      }
      
      _state = FAILED;

      _lastChangeTime = CurrentTime.currentTime();
    }

    if (_log != null && _log.isLoggable(_level))
      _log.log(_level, "fail " + _name);

    notifyListeners(state, FAILED);

    _failCount++;
    
    return true;
  }
  
  /**
   * Changes to the stopping state.
   *
   * @return true if the transition is allowed
   */
  public boolean toStopping()
  {
    LifecycleState state;
    
    synchronized (this) {
      state = _state;
      
      if (state.isAfterStopping() || state.isStarting()) {
        return false;
      }
      
      _state = STOPPING;

      _lastChangeTime = CurrentTime.currentTime();
    }

    if (_log != null && _log.isLoggable(_level)) {
      _log.log(_level, "stopping " + _name);
    }

    notifyListeners(state, STOPPING);

    return true;
  }
  
  /**
   * Changes to the idle (stopped) state.
   *
   * @return true if the transition is allowed
   */
  public boolean toIdle()
  {
    return toState(STOPPED_IDLE);
  }
  
  /**
   * Changes to the stopped state.
   *
   * @return true if the transition is allowed
   */
  public boolean toStop()
  {
    return toNextState(STOPPED);
  }
  
  /**
   * Changes to the destroying state.
   *
   * @return true if the transition is allowed
   */
  public boolean toDestroying()
  {
    return toNextState(DESTROYING);
  }
  
  /**
   * Changes to the closed state.
   *
   * @return true if the transition is allowed
   */
  public boolean toDestroy()
  {
    return toNextState(DESTROYED);
  }
  
  /**
   * Changes to the next state.
   *
   * @return true if the transition is allowed
   */
  private boolean toNextState(LifecycleState newState)
  {
    LifecycleState state;
    
    synchronized (this) {
      state = _state;
      
      if (newState.ordinal() <= state.ordinal()) {
        return false;
      }
      
      _state = newState;

      _lastChangeTime = CurrentTime.currentTime();
    }

    if (_log != null && _log.isLoggable(_lowLevel)) {
      _log.log(_lowLevel, newState + " " + _name);
    }

    notifyListeners(state, newState);

    return true;
  }
  
  /**
   * Changes to the next state.
   *
   * @return true if the transition is allowed
   */
  private boolean toState(LifecycleState newState)
  {
    LifecycleState state;
    
    synchronized (this) {
      state = _state;
      _state = newState;

      _lastChangeTime = CurrentTime.currentTime();
    }

    if (_log != null && _log.isLoggable(_lowLevel)) {
      _log.log(_lowLevel, newState + " " + _name);
    }

    notifyListeners(state, newState);

    return true;
  }

  //
  // listeners
  //

  /**
   * Adds a listener to detect lifecycle changes.
   */
  public void addListener(LifecycleListener listener)
  {
    synchronized (this) {
      if (isDestroyed()) {
        IllegalStateException e = new IllegalStateException("attempted to add listener to a destroyed lifecyle " + this);

        if (_log != null)
          _log.log(Level.WARNING, e.toString(), e);
        else
          Logger.getLogger(Lifecycle.class.getName()).log(Level.WARNING, e.toString(), e);

        return;
      }

      if (_listeners == null)
        _listeners = new ArrayList<WeakReference<LifecycleListener>>();

      for (int i = _listeners.size() - 1; i >= 0; i--) {
        LifecycleListener oldListener = _listeners.get(i).get();

        if (listener == oldListener)
          return;
        else if (oldListener == null)
          _listeners.remove(i);
      }

      _listeners.add(new WeakReference<LifecycleListener>(listener));
    }
  }

  /**
   * Removes a listener.
   */
  public void removeListener(LifecycleListener listener)
  {
    synchronized (this) {
      if (_listeners == null)
        return;

      for (int i = _listeners.size() - 1; i >= 0; i--) {
        LifecycleListener oldListener = _listeners.get(i).get();

        if (listener == oldListener) {
          _listeners.remove(i);

          return;
        }
        else if (oldListener == null)
          _listeners.remove(i);
      }
    }
  }

  /**
   * Returns the listeners.
   */
  private void notifyListeners(LifecycleState oldState, LifecycleState newState)
  {
    notifyWait();
    
    // initial must be outside synchronized to avoid any blocking
    // for fail-safe shutdown
    if (_listeners == null) {
      return;
    }
    
    ArrayList<LifecycleListener> listeners = null;
    
    synchronized (this) {
      notifyAll();

      if (_listeners != null) {
        for (int i = 0; i < _listeners.size(); i++) {
          LifecycleListener listener = _listeners.get(i).get();

          if (listener != null) {
            if (listeners == null) {
              listeners = new ArrayList<LifecycleListener>();
            }
            
            listeners.add(listener);
          }
          else {
            _listeners.remove(i);
            i--;
          }
        }
      }
    }

    if (listeners != null) {
      for (LifecycleListener listener : listeners) {
        listener.lifecycleEvent(oldState, newState);
      }
    }
  }
  
  /**
   * Copies from a target state.
   *
   * @return true if the transition is allowed
   */
  public void copyState(Lifecycle source)
  {
    _state = source._state;
  }

  /**
   * Debug string value.
   */
  public String toString()
  {
    if (_name != null)
      return "Lifecycle[" + _name + ", " + getStateName() + "]";
    else
      return "Lifecycle[" + getStateName() + "]";
  }
}
