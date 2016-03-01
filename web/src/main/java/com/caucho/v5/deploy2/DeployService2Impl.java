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

package com.caucho.v5.deploy2;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.lifecycle.LifecycleState;

import io.baratine.service.Result;


/**
 * DeployController controls the lifecycle of the DeployInstance.
 */
public class DeployService2Impl<I extends DeployInstance2>
  implements DeployService2<I>
{
  private static final Logger log
    = Logger.getLogger(DeployService2Impl.class.getName());
  
  private final Lifecycle _lifecycle;
  
  private final String _id;
  
  private DeployFactory2<I> _factory;
  
  private DeployStrategy2<I> _strategy
    = Strategy2StartManualRedeployAuto.strategy();
  
  private I _instance;
  
  private Logger _log;

  DeployService2Impl(String id, Logger log)
  {
    _id = id;
    
    _log = log;
    
    _lifecycle = new Lifecycle(log, id, Level.FINEST);
  }

  I get()
  {
    return _instance;
  }
  
  public void get(Result<I> result)
  {
    result.ok(_instance);
  }

  Logger getLog()
  {
    return _log;
  }

  public void setStrategy(DeployStrategy2<I> strategy)
  {
    Objects.requireNonNull(strategy);
    
    _strategy = strategy;
  }
  
  public void builder(Result<DeployFactory2<I>> result)
  {
    result.ok(builder());
  }
  
  private DeployFactory2<I> builder()
  {
    return _factory;
  }

  @Override
  public void factory(DeployFactory2<I> builder)
  {
    DeployFactory2<I> oldBuilder = builder();
    
    if (builder == oldBuilder) {
      return;
    }
    
    if (oldBuilder != null) {
      shutdown(ShutdownModeAmp.GRACEFUL, Result.ignore());
    }
    
    _instance = null;
    _factory = builder;
    
    if (_lifecycle.isActive()) {
      startImpl(Result.ignore());
    }
  }
  
  public void configException(Result<Throwable> result)
  {
    DeployFactory2<I> builder = builder();
    
    if (builder != null) {
      result.ok(builder.configException());
    }
    else {
      result.ok(null);
    }
  }

  public LifecycleState getState()
  {
    return _lifecycle.getState();
  }
  
  //
  // dependency/modified
  //
  
  /**
   * Returns true if the entry is modified.
   */
  public boolean isModified()
  {
    I instance = _instance;

    if (instance == null) {
      return true;
    }
    
    if (DeployMode.MANUAL.equals(_strategy.redeployMode())) {
      return false;
    }
    
    return instance.isModified();
  }
  
  /**
   * Returns true if the entry is modified.
   */
  public boolean isModifiedNow()
  {
    I instance = _instance;

    if (instance == null) {
      return true;
    }
    
    if (DeployMode.MANUAL.equals(_strategy.redeployMode())) {
      return false;
    }
    
    return instance.isModifiedNow();
  }
  
  /**
   * Log the reason for modification
   */
  final public boolean logModified(Logger log)
  {
    return false;
  }

  /**
   * Starts the entry on initialization
   */
  public void startOnInit(Result<I> result)
  {
    
    DeployFactory2<I> builder = builder();
    
    if (builder == null) {
      result.ok(null);
      return;
    }
    
    if (! _lifecycle.toInit()) {
      result.ok(get());
      return;
    }
    
    _strategy.startOnInit(this, result);
  }

  /**
   * Force an instance start from an admin command.
   */
  public final void start(Result<I> result)
  {
    _strategy.start(this, result);
  }

  /**
   * Stops the controller from an admin command.
   */
  public final void shutdown(ShutdownModeAmp mode, 
                             Result<Boolean> result)
  {
    _strategy.shutdown(this, mode, result);
  }

  /**
   * Update the controller from an admin command.
   */
  public final void update(Result<I> result)
  {
    _strategy.update(this, result);
  }

  /**
   * Returns the instance for a top-level request
   * @return the request object or null for none.
   */
  public final void request(Result<I> result)
  {
    I instance = _instance;
    
    if (instance != null && _lifecycle.isActive() && ! isModified()) {
      result.ok(instance);
    }
    else if (_lifecycle.isDestroyed()) {
      result.ok(null);
    }
    else {
      _strategy.request(this, result);
    }
  }
  
  //
  // strategy implementation
  //

  /**
   * Starts the entry.
   */
  public void startImpl(Result<I> result)
  {
    DeployFactory2<I> builder = builder();
    
    if (builder == null) {
      result.ok(null);
      return;
    }
    
    if (! _lifecycle.toStarting()) {
      result.ok(_instance);
      return;
    }
    

    I deployInstance = null;

    boolean isActive = false;
    
    try {
      deployInstance = builder.get();
      
      _instance = deployInstance;

      isActive = true;
      
      result.ok(deployInstance);
    } catch (ConfigException e) {
      log.log(Level.FINEST, e.toString(), e);
      log.log(Level.FINE, e.toString(), e);
      
      result.fail(e);
    } catch (Throwable e) {
      log.log(Level.FINEST, e.toString(), e);
      log.log(Level.FINE, e.toString(), e);
      
      result.fail(e);
    } finally {
      if (isActive) {
        _lifecycle.toActive();
      }
      else {
        _lifecycle.toError();
      }
    }
  }

  /**
   * Stops the current instance.
   */
  protected void shutdownImpl(ShutdownModeAmp mode, Result<Boolean> result)
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    I oldInstance = _instance;
    
    if (oldInstance == null) {
      result.ok(true);
      return;
    }
    
    boolean isStopping = false;

    try {
      thread.setContextClassLoader(oldInstance.classLoader());
      
      isStopping = _lifecycle.toStopping();

      _lifecycle.toStop();

      if (! isStopping) {
        return;
      }

      _instance = null;

      oldInstance.shutdown(mode);
      
      result.ok(true);
    } catch (Throwable e) {
      result.fail(e);
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }

  /**
   * Stops the current instance.
   */
  protected void restartImpl(Result<I> result)
  {
    shutdownImpl(ShutdownModeAmp.GRACEFUL, Result.ignore());
    startImpl(result);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _id + "]";
  }
}
