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

package com.caucho.v5.health.shutdown;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.health.warning.WarningSystem;
import com.caucho.v5.io.TempBuffers;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.lifecycle.LifecycleState;
import com.caucho.v5.subsystem.SubSystemBase;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;

import io.baratine.service.Result;

/**
 * The ShutdownSystem manages the server shutdown and includes a timeout
 * thread. If the timeout takes longer than shutdown-wait-max, the ShutdownSystem
 * will force a JVM exit.
 */
public class ShutdownSystem extends SubSystemBase
{
  public static final int START_PRIORITY = 1;

  private static final Logger log = 
    Logger.getLogger(ShutdownSystem.class.getName());
  private static final L10N L = new L10N(ShutdownSystem.class);

  private static final AtomicReference<ShutdownSystem> _activeService
    = new AtomicReference<ShutdownSystem>();
  
  private long _shutdownWaitMax = 120000L;
  
  private boolean _isShutdownOnOutOfMemory = true;

  private WeakReference<SystemManager> _systemManagerRef;
  private WarningSystem _warningService;
  
  private Lifecycle _lifecycle = new Lifecycle();
  
  private FailSafeHaltThread _failSafeHaltThread;
  private FailSafeMemoryFreeThread _failSafeMemoryFreeThread;
  private ShutdownThread _shutdownThread;
  
  private boolean _isEmbedded;
  
  private AtomicReference<ExitCode> _exitCode
    = new AtomicReference<>();
  
  private ArrayList<Runnable> _memoryFreeTasks = new ArrayList<Runnable>();
  
  private ShutdownSystem(boolean isEmbedded)
  {
    _isEmbedded = isEmbedded;
    
    _systemManagerRef = new WeakReference<SystemManager>(SystemManager.getCurrent());
    
    _warningService = SystemManager.getCurrentSystem(WarningSystem.class);
    
    if (_warningService == null) {
      throw new IllegalStateException(L.l("{0} requires an active {1}",
                                           ShutdownSystem.class.getSimpleName(),
                                           WarningSystem.class.getSimpleName()));
    }
  }
  
  public static ShutdownSystem createAndAddSystem()
  {
    return createAndAddSystem(CurrentTime.isTest());
  }

  public static ShutdownSystem createAndAddSystem(boolean isEmbedded)
  {
    SystemManager system = preCreate(ShutdownSystem.class);
      
    ShutdownSystem service = new ShutdownSystem(isEmbedded);
    system.addSystem(ShutdownSystem.class, service);
    
    return service;
  }

  public static ShutdownSystem getCurrent()
  {
    return SystemManager.getCurrentSystem(ShutdownSystem.class);
  }
  
  public long getShutdownWaitMax()
  {
    return _shutdownWaitMax;
  }
  
  public void setShutdownWaitTime(long shutdownTime)
  {
    _shutdownWaitMax = shutdownTime;
  }

  public void setShutdownOnOutOfMemory(boolean isShutdown)
  {
    _isShutdownOnOutOfMemory = isShutdown;
  }

  public boolean isShutdownOnOutOfMemory()
  {
    return _isShutdownOnOutOfMemory;
  }
  
  /**
   * Returns the current lifecycle state.
   */
  public LifecycleState getLifecycleState()
  {
    return _lifecycle.getState();
  }
  
  public ExitCode getExitCode()
  {
    return _exitCode.get();
  }
  
  public void addMemoryFreeTask(Runnable task)
  {
    _memoryFreeTasks.add(task);
  }
  
  /**
   * Start the server shutdown
   */
  public static void shutdownOutOfMemory(String msg)
  {
    freeMemoryBuffers();
    
    ShutdownSystem shutdown = _activeService.get();
    
    if (shutdown != null && ! shutdown.isShutdownOnOutOfMemory()) {
      System.err.println(msg);
      return;
    }
    else {
      shutdownActive(ExitCode.MEMORY, msg);
    }
  }
  
  /**
   * Attempt to free as much memory as possible for OOM handling.
   * These calls must not allocate memory.
   */
  private static void freeMemoryBuffers()
  {
    TempBuffers.clearFreeLists();
  }
  
  public static void startFailsafe(String msg)
  {
    ShutdownSystem shutdown = _activeService.get();
    
    if (shutdown != null) {
      shutdown.startFailSafeShutdown(msg);
      return;
    }
    
    shutdown = getCurrent();
    
    if (shutdown != null) {
      shutdown.startFailSafeShutdown(msg);
      return;
    }
    
    log.warning("ShutdownService is not active: failsafe: " + msg);
    System.out.println("ShutdownService is not active: failsafe: " + msg);
  }
  
  /**
   * Start the server shutdown
   */
  public static void shutdownActive(ExitCode exitCode, 
                                    String msg)
  {
    shutdownActive(ShutdownModeAmp.GRACEFUL, exitCode, msg, null);
  }
    
  /**
   * Start the server shutdown
   */
  public static void shutdownActive(ShutdownModeAmp mode,
                                    ExitCode exitCode, 
                                    String msg,
                                    Result<String> result)
  {
    ShutdownSystem shutdown = _activeService.get();
    
    if (shutdown != null) {
      shutdown.shutdown(mode, exitCode, msg, result);
      return;
    }
    
    shutdown = getCurrent();
    
    if (shutdown != null) {
      shutdown.shutdown(mode, exitCode, msg, result);
      return;
    }
    
    msg = ShutdownSystem.class.getSimpleName() + " is not active:\n  " + msg;
    
    log.warning(msg);
    System.out.println(msg);
    
    if (result != null) {
      result.ok(msg);
    }
  }

  /**
   * Start the server shutdown
   */
  public void shutdown(ShutdownModeAmp mode,
                       ExitCode exitCode, 
                       String msg)
  {
    shutdown(mode, exitCode, msg, null);
  }

  /**
   * Start the server shutdown
   */
  public void shutdown(ShutdownModeAmp mode,
                       ExitCode exitCode, 
                       String msg,
                       Result<String> result)
  {
    startFailSafeShutdown(msg);

    ShutdownThread shutdownThread = _shutdownThread;
    
    if (shutdownThread != null) {
      shutdownThread.startShutdown(mode, exitCode, result);

      if (! _isEmbedded) {
        waitForShutdown();
      
        System.out.println("Shutdown timeout");
        System.exit(exitCode.ordinal());
      }
    }
    else {
      shutdownImpl(mode, exitCode, result);
    }
  }

  public void startFailSafeShutdown(String msg)
  {
    startFailSafeShutdown(msg, _shutdownWaitMax);
  }

  public void startFailSafeShutdown(String msg, long period)
  {
    // start the fail-safe thread in case the shutdown fails
    FailSafeHaltThread haltThread = _failSafeHaltThread;

    if (haltThread != null) {
      haltThread.startShutdown(period);
    }
    
    FailSafeMemoryFreeThread memoryFreeThread = _failSafeMemoryFreeThread;
    
    if (memoryFreeThread != null) {
      memoryFreeThread.startShutdown();
    }

    try {
      _warningService.sendWarning(this, "Shutdown: " + msg);
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  /**
   * Closes the server.
   */
  private void shutdownImpl(ShutdownModeAmp mode, 
                            ExitCode exitCode,
                            Result<String> result)
  {
    // start the fail-safe thread in case the shutdown fails
    FailSafeHaltThread haltThread = _failSafeHaltThread;
    
    if (haltThread != null) {
      haltThread.startShutdown();
    }

    if (exitCode == null) {
      exitCode = ExitCode.FAIL_SAFE_HALT;
    }
    
    _exitCode.compareAndSet(null, exitCode);

    try {
      SystemManager systemManager = _systemManagerRef.get();
        
      if (systemManager != null) {
        systemManager.shutdown(mode);
      }
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
    } finally {
      _systemManagerRef = null;
      _lifecycle.toDestroy();

      System.err.println("\nShutdown reason: " + exitCode + "\n");

      log.warning("Shutdown reason: " + exitCode);
      
      if (result != null) {
        result.ok("Shutdown reason: " + exitCode);
      }

      if (! _isEmbedded) {
        System.exit(exitCode.ordinal());
      }
    }
  }
  
  private SystemManager getSystemManager()
  {
    WeakReference<SystemManager> systemManagerRef = _systemManagerRef;
    
    if (systemManagerRef != null)
      return systemManagerRef.get();
    else
      return null;
  }

  /**
   * Dump threads for debugging
   */
  public void dumpThreads()
  {
  }
  
  //
  // Service API
  //
  
  @Override
  public int getStartPriority()
  {
    return START_PRIORITY;
  }

  /**
   * Starts the server.
   */
  @Override
  public void start()
  {
    _lifecycle.toActive();
    
    _exitCode.set(null);
    
    if (! _isEmbedded) {
      // _activeService.compareAndSet(null, this);
      _activeService.set(this);
    }
    
    if (! CurrentTime.isTest() && ! _isEmbedded) {
      _failSafeHaltThread = new FailSafeHaltThread();
      _failSafeHaltThread.start();
      
      _failSafeMemoryFreeThread = new FailSafeMemoryFreeThread();
      _failSafeMemoryFreeThread.start();
    }

    if (! _isEmbedded) {
      _shutdownThread = new ShutdownThread();
      _shutdownThread.setDaemon(true);
      _shutdownThread.start();
    }
  }
  
  /**
   * Starts the server.
   */
  @Override
  public void stop(ShutdownModeAmp mode)
  {
    _lifecycle.toDestroy();
    
    _activeService.set(null);
    
    FailSafeHaltThread failSafeThread = _failSafeHaltThread;
    
    if (failSafeThread != null) {
      failSafeThread.wake();
    }
    
    FailSafeMemoryFreeThread memoryFreeThread = _failSafeMemoryFreeThread;
    
    if (memoryFreeThread != null)
      memoryFreeThread.startShutdown();
    
    ShutdownThread shutdownThread = _shutdownThread;
    
    if (shutdownThread != null)
      shutdownThread.wake();
  }

  @Override
  public void destroy()
  {
    _lifecycle.toDestroy();
  }
  
  private void waitForShutdown()
  {
    waitForShutdown(-1);
  }
  
  private void waitForShutdown(long period)
  {
    if (period <= 0)
      period = _shutdownWaitMax;
    
    long expire = System.currentTimeMillis() + period;
    long now;

    while ((now = System.currentTimeMillis()) < expire) {
      try {
        Thread.interrupted();
        Thread.sleep(expire - now);
      } catch (Exception e) {
      }
    }
  }

  @Override
  public String toString()
  {
    SystemManager system = getSystemManager();
    
    if (system != null)
      return getClass().getSimpleName() + "[id=" + system.getId() + "]";
    else
      return getClass().getSimpleName() + "[closed]";
  }

  class ShutdownThread extends Thread {
    private ShutdownModeAmp _shutdownMode;
    
    private AtomicReference<ExitCode> _shutdownExitCode
      = new AtomicReference<>();
    
    private AtomicReference<Result<String>> _shutdownResult
      = new AtomicReference<>();

    ShutdownThread()
    {
      setName("baratine-shutdown");
      setDaemon(true);
    }

    /**
     * Starts the destroy sequence
     */
    void startShutdown(ShutdownModeAmp mode, 
                       ExitCode exitCode,
                       Result<String> result)
    {
      _shutdownMode = mode;
      
      _shutdownResult.compareAndSet(null, result);
      _shutdownExitCode.compareAndSet(null, exitCode);
      
      wake();
    }
    
    void wake()
    {
      LockSupport.unpark(this);
    }

    @Override
    public void run()
    {
      while (_shutdownExitCode.get() == null 
             && _lifecycle.isActive()
             && _activeService.get() == ShutdownSystem.this) {
        try {
          Thread.interrupted();
          LockSupport.park();
        } catch (Exception e) {
        }
      }

      ExitCode exitCode = _shutdownExitCode.get();
      
      if (exitCode != null) {
        shutdownImpl(_shutdownMode, exitCode, _shutdownResult.get());
      }
    }
  }

  class FailSafeMemoryFreeThread extends Thread {
    private volatile boolean _isShutdown;

    FailSafeMemoryFreeThread()
    {
      setName("baratine-fail-safe-memory-free");
      setDaemon(true);
    }

    /**
     * Starts the shutdown sequence
     */
    void startShutdown()
    {
      _isShutdown = true;

      LockSupport.unpark(this);
    }

    @Override
    public void run()
    {
      while (! _isShutdown && _lifecycle.isActive()) {
        try {
          Thread.interrupted();
          LockSupport.park();
        } catch (Exception e) {
        }
      }
      
      for (int i = 0; i < _memoryFreeTasks.size(); i++) {
        Runnable task = _memoryFreeTasks.get(i);
        
        try {
          task.run();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      
      if (! _lifecycle.isActive())
        return;
    }
  }

  class FailSafeHaltThread extends Thread {
    private volatile boolean _isShutdown;
    private volatile long _period = -1;

    FailSafeHaltThread()
    {
      setName("baratine-fail-safe-halt");
      setDaemon(true);
    }

    /**
     * Starts the shutdown sequence
     */
    void startShutdown()
    {
      startShutdown(-1);
    }
    
    /**
     * Starts the shutdown sequence
     */
    void startShutdown(long period)
    {
      _isShutdown = true;
      _period = period;

      wake();
    }
    
    void wake()
    {
      LockSupport.unpark(this);
    }

    @Override
    public void run()
    {
      while (! _isShutdown && _lifecycle.isActive()) {
        try {
          Thread.interrupted();
          LockSupport.park();
        } catch (Exception e) {
        }
      }
      
      if (! _lifecycle.isActive())
        return;
      
      waitForShutdown(_period);

      if (_lifecycle.isActive()) {
        Runtime.getRuntime().halt(ExitCode.FAIL_SAFE_HALT.ordinal());
      }
    }
  }
}
