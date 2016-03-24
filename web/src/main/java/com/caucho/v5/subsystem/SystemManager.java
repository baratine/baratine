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

package com.caucho.v5.subsystem;

import java.lang.ref.WeakReference;
import java.net.BindException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.config.ConfigException;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.lifecycle.LifecycleListener;
import com.caucho.v5.loader.EnvLoader;
import com.caucho.v5.loader.EnvironmentClassLoader;
import com.caucho.v5.loader.EnvironmentLocal;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.Version;

public class SystemManager implements AutoCloseable
{
  private static final L10N L = new L10N(SystemManager.class);
  private static final Logger log
    = Logger.getLogger(SystemManager.class.getName());

  private static final EnvironmentLocal<SystemManager> _systemLocal
    = new EnvironmentLocal<SystemManager>();

  private static WeakReference<SystemManager> _globalSystemRef;

  private String _id;
  private EnvironmentClassLoader _classLoader;

  private final ConcurrentHashMap<Class<?>,SubSystem> _systemMap
    = new ConcurrentHashMap<Class<?>,SubSystem>();

  private final TreeSet<SubSystem> _pendingStart
    = new TreeSet<>(new StartComparator());

  private final ArrayList<AfterServerStartListener> _afterStartListeners
    = new ArrayList<>();

  // private InjectManager _injectManager;

  private Throwable _configException;

  // private long _shutdownWaitMax = 60 * 1000;

  // private ServerAdmin _admin;

  private final Lifecycle _lifecycle;

  // stats

  private long _startTime;
  private ShutdownModeAmp _shutdownMode = ShutdownModeAmp.GRACEFUL;

  /**
   * Creates a new servlet server.
   */
  public SystemManager(String id)
  {
    this(id, (ClassLoader) null);
  }

  /**
   * Creates a new servlet server.
   */
  public SystemManager(String id, ClassLoader parentLoader)
  {
    if (id == null) {
      id = "default";
    }

    _id = id;
    
    if (parentLoader == null) {
      parentLoader = Thread.currentThread().getContextClassLoader();
    }

    EnvLoader.addCloseListener(this, parentLoader);

    /*
    if (parentLoader instanceof EnvironmentClassLoader) {
      _classLoader = (EnvironmentClassLoader) parentLoader;
    }
    else {
      // the environment id must be independent of the server because
      // of cluster cache requirements.
      _classLoader = EnvironmentClassLoader.create(parentLoader, "system:");
    }
    */
    _classLoader = EnvironmentClassLoader.create(parentLoader, "system:");
    
    _systemLocal.set(this, _classLoader);

    _globalSystemRef = new WeakReference<>(this);

    _lifecycle = new Lifecycle(log, toString(), Level.FINE);

    // lifecycle for the classloader binding (cdi beans)
    addSystem(new ClassLoaderSystemBind());
    // lifecycle for the classloader itself
    addSystem(new ClassLoaderSystemStart());

    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();

    try {
      thread.setContextClassLoader(_classLoader);

      EnvLoader.init();
      
      // JmxUtil.addContextProperty("Server", getId());
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }

  public void setId(String id)
  {
    _id = id;
  }

  /**
   * Returns the current server
   */
  public static SystemManager getCurrent()
  {
    SystemManager system = _systemLocal.get();

    if (system == null) {
      WeakReference<SystemManager> globalRef = _globalSystemRef;

      if (globalRef != null) {
        system = globalRef.get();
      }
    }

    return system;
  }

  /**
   * Returns the current server
   */
  public static SystemManager getCurrent(ClassLoader loader)
  {
    SystemManager system = _systemLocal.get(loader);

    if (system == null) {
      WeakReference<SystemManager> globalRef = _globalSystemRef;

      if (globalRef != null) {
        system = globalRef.get();
      }
    }

    return system;
  }

  /**
   * Returns the current identified system.
   */
  public static <T extends SubSystem> T
  getCurrentSystem(Class<T> systemClass)
  {
    SystemManager manager = getCurrent();

    if (manager != null)
      return manager.getSystem(systemClass);
    else
      return null;
  }

  /**
   * Returns the current identified system.
   */
  public static <T extends SubSystem> T
  getCurrentSystem(Class<T> systemClass, ClassLoader loader)
  {
    SystemManager manager = getCurrent(loader);

    if (manager != null)
      return manager.getSystem(systemClass);
    else
      return null;
  }

  /**
   * Returns the current system id.
   */
  public static String getCurrentId()
  {
    SystemManager system = getCurrent();

    if (system == null) {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();

      throw new IllegalStateException(L.l("{0} is not available in this context.\n  {1}",
                                          SystemManager.class.getSimpleName(),
                                          loader));
    }

    return system.getId();
  }

  /**
   * Returns the server id
   */
  public String getId()
  {
    return _id;
  }

  /**
   * Returns the classLoader
   */
  public EnvironmentClassLoader getClassLoader()
  {
    return _classLoader;
  }

  /**
   * Returns the configuration exception
   */
  public Throwable getConfigException()
  {
    return _configException;
  }

  /**
   * Returns the configuration instance.
   */
  public void setConfigException(Throwable exn)
  {
    _configException = exn;
  }

  //
  // statistics
  //

  /**
   * Returns the time the server started in ms.
   */
  public long getStartTime()
  {
    return _startTime;
  }

  /**
   * Returns the lifecycle state
   */
  public String getState()
  {
    return _lifecycle.getStateName();
  }

  public void addLifecycleListener(LifecycleListener listener)
  {
    _lifecycle.addListener(listener);
  }

  /**
   * Returns true if the server has been modified and needs restarting.
   */
  public boolean isModified()
  {
    boolean isModified = _classLoader.isModified();

    if (isModified)
      _classLoader.logModified(log);

    return isModified;
  }

  /**
   * Returns true if the server has been modified and needs restarting.
   */
  public boolean isModifiedNow()
  {
    boolean isModified = _classLoader.isModifiedNow();

    if (isModified)
      log.fine("server is modified");

    return isModified;
  }

  /**
   * Returns true if the server is starting or active
   */
  public boolean isAfterStarting()
  {
    return _lifecycle.getState().isAfterStarting();
  }

  /**
   * Returns true before the startup has completed.
   */
  public boolean isBeforeActive()
  {
    return _lifecycle.getState().isBeforeActive();
  }

  /**
   * Returns true if the server is stopped.
   */
  public boolean isStopping()
  {
    return _lifecycle.isStopping();
  }

  /**
   * Returns true if the server is stopped.
   */
  public boolean isStopped()
  {
    return _lifecycle.isStopped();
  }

  /**
   * Returns true if the server is closed.
   */
  public boolean isDestroyed()
  {
    return _lifecycle.isDestroyed();
  }

  /**
   * Returns true if the server is closed.
   */
  public boolean isDestroying()
  {
    return _lifecycle.isDestroying();
  }

  /**
   * Returns true if the server is currently active and accepting requests
   */
  public boolean isActive()
  {
    return _lifecycle.isActive();
  }

  //
  // System operations
  //

  /**
   * Adds a new system.
   */
  public void addSystem(SubSystem system)
  {
    @SuppressWarnings("unchecked")
    Class<SubSystem> cl = (Class<SubSystem>) system.getClass();
    
    addSystem(cl, system);
  }

  /**
   * Adds a new system.
   */
  public <T extends SubSystem> void addSystem(Class<T> systemApi, T system)
  {
    SubSystem oldSystem
      = _systemMap.putIfAbsent(systemApi, system);

    if (oldSystem != null) {
      throw new IllegalStateException(L.l("duplicate system '{0}' is not allowed because another system with that class is already registered '{1}'",
                                          system, oldSystem));
    }

    _pendingStart.add(system);

    if (_lifecycle.isActive()) {
      startSystems();
    }
  }

  /**
   * Adds a new service.
   */
  public <T extends SubSystem> T addSystemIfAbsent(T system)
  {
    return addSystemIfAbsent(system.getClass(), system);
  }

  /**
   * Adds a new system.
   */
  @SuppressWarnings("unchecked")
  public <T extends SubSystem> T
  addSystemIfAbsent(Class<?> systemApi, T system)
  {
    SubSystem oldService
      = _systemMap.putIfAbsent(systemApi, system);

    if (oldService != null) {
      return (T) oldService;
    }

    _pendingStart.add(system);

    if (_lifecycle.isActive()) {
      startSystems();
    }

    return null;
  }

  /**
   * Returns the system for the given class.
   */
  @SuppressWarnings("unchecked")
  public <T extends SubSystem> T getSystem(Class<T> cl)
  {
    return (T) _systemMap.get(cl);
  }

  //
  // listeners
  //

  /**
   * Adds a new AfterStart listener for post-startup cleanup
   */
  public void addListener(AfterServerStartListener listener)
  {
    _afterStartListeners.add(listener);
  }

  //
  // lifecycle operations
  //

  /**
   * Start the server.
   */
  public void start()
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();

    try {
      thread.setContextClassLoader(_classLoader);

      if (! _lifecycle.toStarting())
        return;

      _startTime = CurrentTime.currentTime();

      if (! CurrentTime.isTest()) {
        log.info("");

        log.info(Version.getFullVersion());

        log.info("");

        log.info(System.getProperty("os.name")
                 + " " + System.getProperty("os.version")
                 + " " + System.getProperty("os.arch"));

        log.info(System.getProperty("java.runtime.name")
                 + " " + System.getProperty("java.runtime.version")
                 + ", " + System.getProperty("file.encoding")
                 + ", " + System.getProperty("user.language"));

        log.info(System.getProperty("java.vm.name")
                 + " " + System.getProperty("java.vm.version")
                 + ", " + System.getProperty("sun.arch.data.model")
                 + ", " + System.getProperty("java.vm.info")
                 + ", " + System.getProperty("java.vm.vendor"));

        log.info("");

        log.info("user.name  = " + System.getProperty("user.name"));
      }

      startSystems();

      _lifecycle.toActive();

      for (AfterServerStartListener listener : _afterStartListeners) {
        listener.afterStart();
      }
    } finally {
      if (! _lifecycle.isActive())
        _lifecycle.toError();

      thread.setContextClassLoader(oldLoader);
    }
  }

  private void startSystems()
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();

    try {
      thread.setContextClassLoader(_classLoader);

      while (_pendingStart.size() > 0) {
        SubSystem system = _pendingStart.first();
        _pendingStart.remove(system);

        thread.setContextClassLoader(_classLoader);

        if (log.isLoggable(Level.FINEST)) {
          log.finest(system + " starting");
        }

        system.start();

        if (log.isLoggable(Level.FINEST)) {
          log.finest(system + " active");
        }
      }
    } catch (RuntimeException e) {
      log.log(Level.WARNING, e.toString(), e);

      _lifecycle.toError();

      throw e;
    } catch (BindException e) {
      //log.log(Level.WARNING, e.toString(), e);

      _lifecycle.toError();

      // if the system can't start, it needs to completely fail, especially
      // for the watchdog
      throw ConfigException.createConfig(e);
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);

      _lifecycle.toError();

      // if the system can't start, it needs to completely fail, especially
      // for the watchdog
      throw new RuntimeException(e);
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }
  
  public LoaderContext openContext()
  {
    Thread thread = Thread.currentThread();
    
    ClassLoader loader = thread.getContextClassLoader();
    
    thread.setContextClassLoader(getClassLoader());
    
    return new LoaderContext(loader);
  }

  public ShutdownModeAmp getShutdownMode()
  {
    return _shutdownMode;
  }

  /**
   * stops the server.
   */
  private void stop(ShutdownModeAmp mode)
  {
    _shutdownMode = mode;
    
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();

    try {
      thread.setContextClassLoader(_classLoader);

      if (! _lifecycle.toStopping()) {
        return;
      }

      TreeSet<SubSystem> systems
        = new TreeSet<SubSystem>(new StopComparator());

      systems.addAll(_systemMap.values());

      // sort

      for (SubSystem system : systems) {
        try {
          thread.setContextClassLoader(_classLoader);

          if (log.isLoggable(Level.FINEST)) {
            log.finest(system + " stopping");
          }

          system.stop(mode);
        } catch (Throwable e) {
          log.log(Level.WARNING, e.toString(), e);
        }
      }

    } finally {
      _lifecycle.toStop();

      thread.setContextClassLoader(oldLoader);
    }
  }

  /**
   * Closes the server.
   */
  @Override
  public void close()
  {
    shutdown(ShutdownModeAmp.GRACEFUL);
  }
  
  /**
   * Shuts down the server in the given mode.
   */
  public void shutdown(ShutdownModeAmp mode)
  {
    stop(mode);

    if (! _lifecycle.toDestroy()) {
      return;
    }
    
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();

    try {
      thread.setContextClassLoader(_classLoader);

      TreeSet<SubSystem> systems
        = new TreeSet<SubSystem>(new StopComparator());

      systems.addAll(_systemMap.values());

      _systemMap.clear();

      for (SubSystem system : systems) {
        try {
          system.destroy();
        } catch (Throwable e) {
          log.log(Level.WARNING, e.toString(), e);
        }
      }

      WeakReference<SystemManager> globalRef = _globalSystemRef;

      if (globalRef != null && globalRef.get() == this) {
        _globalSystemRef = null;
      }

      /*
       * destroy
       */

      log.fine(this + " destroyed");

      _classLoader.destroy();
    } finally {
      // DynamicClassLoader.setOldLoader(thread, oldLoader);
      
      thread.setContextClassLoader(oldLoader);

      _classLoader = null;
    }
  }

  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[id=" + getId() + "]");
  }

  class ClassLoaderSystemBind extends SubSystemBase {
    @Override
    public int getStartPriority()
    {
      return START_PRIORITY_CLASSLOADER_BIND;
    }

    @Override
    public void start()
    {
      // cloud/0316
      
      _classLoader.start();
    }
  }

  class ClassLoaderSystemStart extends SubSystemBase {
    @Override
    public int getStartPriority()
    {
      return START_PRIORITY_CLASSLOADER;
    }

    @Override
    public void start()
    {
      _classLoader.start();
    }

    @Override
    public void stop(ShutdownModeAmp mode)
    {
      _classLoader.stop();
    }

    @Override
    public void destroy()
    {
      _classLoader.destroy();
    }
  }
  
  public static class LoaderContext implements AutoCloseable {
    private final ClassLoader _oldLoader;
    
    LoaderContext(ClassLoader loader)
    {
      _oldLoader = loader;
    }
    
    public void close()
    {
      Thread.currentThread().setContextClassLoader(_oldLoader);
    }
  }

  static class StartComparator implements Comparator<SubSystem> {
    @Override
    public int compare(SubSystem a, SubSystem b)
    {
      int cmp = a.getStartPriority() - b.getStartPriority();

      if (cmp != 0)
        return cmp;
      else
        return a.getClass().getName().compareTo(b.getClass().getName());
    }
  }

  static class StopComparator implements Comparator<SubSystem> {
    @Override
    public int compare(SubSystem a, SubSystem b)
    {
      int cmp = b.getStopPriority() - a.getStopPriority();

      if (cmp != 0)
        return cmp;
      else
        return b.getClass().getName().compareTo(a.getClass().getName());
    }
  }
}
