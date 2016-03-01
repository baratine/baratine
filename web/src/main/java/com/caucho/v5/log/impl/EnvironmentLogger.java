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

package com.caucho.v5.log.impl;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.caucho.v5.loader.DynamicClassLoader;
import com.caucho.v5.loader.EnvLoader;
import com.caucho.v5.loader.EnvLoaderListener;
import com.caucho.v5.loader.EnvironmentLocal;
import com.caucho.v5.util.ModulePrivate;

/**
 * Proxy logger that understands the environment.
 */
@ModulePrivate
public class EnvironmentLogger extends Logger implements EnvLoaderListener
{
  private static final Handler[] EMPTY_HANDLERS = new Handler[0];
  
  private static ClassLoader _systemClassLoader;
  
  // The custom local handlers
  private final EnvironmentLocal<Logger> _localLoggers
    = new EnvironmentLocal<Logger>();
  
  // The environment handlers for the Logger
  private final EnvironmentLocal<Handler[]> _localHandlers
    = new EnvironmentLocal<Handler[]>();
  
  // The environment handlers owned by the Logger
  private final EnvironmentLocal<HandlerEntry> _ownHandlers
    = new EnvironmentLocal<HandlerEntry>();

  // The use-parent-handlers value
  private final EnvironmentLocal<Boolean> _useParentHandlers
    = new EnvironmentLocal<Boolean>();
  
  // Application level override
  private EnvironmentLocal<Level> _localLevel;
  private Level _systemLevel = null;

  private EnvironmentLogger _parent;

  // Can be a weak reference because any configuration in an
  // environment will be held in the EnvironmentLocal.
  private final ArrayList<WeakReference<EnvironmentLogger>> _children
    = new ArrayList<WeakReference<EnvironmentLogger>>();
  
  // Weak list of all the class loaders
  private final ArrayList<WeakReference<ClassLoader>> _loaders
    = new ArrayList<WeakReference<ClassLoader>>();
  
  // The local effective level
  private EnvironmentLocal<Integer> _localEffectiveLevel;

  private boolean _hasLocalEffectiveLevel;
  
  private Level _finestEffectiveLevel = Level.INFO;
  private int _finestEffectiveLevelValue = _finestEffectiveLevel.intValue();
  private int _systemEffectiveLevelValue = Level.INFO.intValue();
  
  public EnvironmentLogger(String name, String resourceBundleName)
  {
    super(name, resourceBundleName);
  }

  /**
   * Sets the logger's parent.  This should only be called by the LogManager
   * code.
   */
  @Override
  public void setParent(Logger parent)
  {
    if (parent.equals(_parent))
      return;
    
    super.setParent(parent);

    if (parent instanceof EnvironmentLogger) {
      _parent = (EnvironmentLogger) parent;

      _parent.addChild(this);
    }
    
    updateEffectiveLevel(_systemClassLoader);
  }
  
  //
  // levels
  //

  /**
   * Returns the logger's assigned level.
   */
  @Override
  public Level getLevel()
  {
    if (_localLevel != null) {
      Level level = _localLevel.get();

      if (level != null) {
        return level;
      }
    }

    return _systemLevel;
  }

  /**
   * Application API to set the level.
   *
   * @param level the logging level to set for the logger.
   */
  @Override
  public void setLevel(Level level)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    
    if (loader == null) {
      loader = _systemClassLoader;
    }

    if (loader != _systemClassLoader) {
      if (_localLevel == null)
        _localLevel = new EnvironmentLocal<Level>();
      
      _localLevel.set(level);
      
      if (level != null) {
        addLoader(loader);
      }
    }
    else {
      _systemLevel = level;
    }
    
    updateEffectiveLevel(loader);
  }
  
  //
  // handlers
  //

  /**
   * Returns the handlers.
   */
  @Override
  public Handler []getHandlers()
  {
    Handler []handlers = _localHandlers.get();
    
    if (handlers != null)
      return handlers;
    else
      return EMPTY_HANDLERS;
  }

  /**
   * Adds a handler.
   */
  @Override
  public void addHandler(Handler handler)
  {
    synchronized (this) {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      
      if (loader == null) {
        loader = _systemClassLoader;
      }

      boolean hasLoader = false;
      for (int i = _loaders.size() - 1; i >= 0; i--) {
        WeakReference<ClassLoader> ref = _loaders.get(i);
        ClassLoader refLoader = ref.get();

        if (refLoader == null)
          _loaders.remove(i);

        if (refLoader == loader)
          hasLoader = true;

        if (isParentLoader(loader, refLoader))
          addHandler(handler, refLoader);
      }

      if (! hasLoader) {
        _loaders.add(new WeakReference<ClassLoader>(loader));
        addHandler(handler, loader);
        EnvLoader.addClassLoaderListener(this, loader);
      }

      HandlerEntry ownHandlers = _ownHandlers.get();
      if (ownHandlers == null) {
        ownHandlers = new HandlerEntry(this);
        _ownHandlers.set(ownHandlers);
      }
    
      ownHandlers.addHandler(handler);
    }
  }

  /**
   * Removes a handler.
   */
  @Override
  public void removeHandler(Handler handler)
  {
    synchronized (this) {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      
      if (loader == null) {
        loader = _systemClassLoader;
      }

      for (int i = _loaders.size() - 1; i >= 0; i--) {
        WeakReference<ClassLoader> ref = _loaders.get(i);
        ClassLoader refLoader = ref.get();

        if (refLoader == null) {
          _loaders.remove(i);
        }

        if (isParentLoader(loader, refLoader)) {
          removeHandler(handler, refLoader);
        }
      }

      HandlerEntry ownHandlers = _ownHandlers.get();
      if (ownHandlers != null)
        ownHandlers.removeHandler(handler);
    }
  }
  
  //
  // logging
  //
  
  /**
   * True if the level is loggable
   */
  @Override
  public final boolean isLoggable(Level level)
  {
    if (level == null)
      return false;

    int intValue = level.intValue();
    
    if (intValue < _finestEffectiveLevelValue)
      return false;
    else if (! _hasLocalEffectiveLevel) {
      return true;
    }
    else {
      Integer localValue = _localEffectiveLevel.get();
      
      if (localValue != null) {
        int localIntValue = localValue.intValue();
        
        if (localIntValue == Level.OFF.intValue())
          return false;
        else
          return localIntValue <= intValue;
      }
      else {
        if (_systemEffectiveLevelValue == Level.OFF.intValue())
          return false;
        else
          return _systemEffectiveLevelValue <= intValue;
      }
    }
  }

  /**
   * Returns the use-parent-handlers
   */
  @Override
  public boolean getUseParentHandlers()
  {
    Boolean value = _useParentHandlers.get();

    if (value != null)
      return Boolean.TRUE.equals(value);
    else
      return true;
  }

  /**
   * Sets the use-parent-handlers
   */
  @Override
  public void setUseParentHandlers(boolean useParentHandlers)
  {
    _useParentHandlers.set(new Boolean(useParentHandlers));
  }

  /**
   * Logs the message.
   */
  @Override
  public void log(LogRecord record)
  {
    if (record == null)
      return;
    
    Level recordLevel = record.getLevel();

    if (! isLoggable(recordLevel))
      return;
    
    for (Logger ptr = this; ptr != null; ptr = ptr.getParent()) {
      Handler handlers[] = ptr.getHandlers();

      if (handlers != null) {
        for (int i = 0; i < handlers.length; i++) {
          handlers[i].publish(record);
        }
      }

      if (! ptr.getUseParentHandlers())
        break;
    }
  }
  
  //
  // implementation methods
  //

  /**
   * Adds a new logger as a child, triggered by a setParent.
   */
  void addChild(EnvironmentLogger child)
  {
    _children.add(new WeakReference<EnvironmentLogger>(child));
    
    updateChildren();
  }

  /**
   * Adds a new handler with a given classloader context.
   */
  private void addHandler(Handler handler, ClassLoader loader)
  {
    // handlers ordered by level
    ArrayList<Handler> handlers = new ArrayList<Handler>();

    handlers.add(handler);

    for (ClassLoader ptr = loader; ptr != null; ptr = ptr.getParent()) {
      Handler []localHandlers = _localHandlers.getLevel(ptr);

      if (localHandlers != null) {
        for (int i = 0; i < localHandlers.length; i++) {
          int p = handlers.indexOf(localHandlers[i]);

          if (p < 0) {
            handlers.add(localHandlers[i]);
          }
          else {
            Handler oldHandler = handlers.get(p);

            if (localHandlers[i].getLevel().intValue()
                < oldHandler.getLevel().intValue()) {
              handlers.set(p, localHandlers[i]);
            }
          }
        }
      }
    }

    Handler []newHandlers = new Handler[handlers.size()];
    handlers.toArray(newHandlers);
    
    if (loader == _systemClassLoader)
      loader = null;

    _localHandlers.set(newHandlers, loader);
  }

  private void removeHandler(Handler handler, ClassLoader loader)
  {
    ArrayList<Handler> handlers = new ArrayList<Handler>();

    for (ClassLoader ptr = loader; ptr != null; ptr = ptr.getParent()) {
      Handler []localHandlers = _localHandlers.getLevel(ptr);

      if (localHandlers != null) {
        for (int i = 0; i < localHandlers.length; i++) {
          if (! localHandlers[i].equals(handler)) {
            int p = handlers.indexOf(localHandlers[i]);

            if (p < 0) {
              handlers.add(localHandlers[i]);
            }
            else {
              Handler oldHandler = handlers.get(p);

              if (localHandlers[i].getLevel().intValue()
                  < oldHandler.getLevel().intValue()) {
                handlers.set(p, localHandlers[i]);
              }
            }
          }
        }
      }
    }

    Handler []newHandlers = new Handler[handlers.size()];
    handlers.toArray(newHandlers);

    _localHandlers.set(newHandlers, loader);
  }

  /**
   * Returns true if 'parent' is a parent classloader of 'child'.
   *
   * @param parent the classloader to test as a parent.
   * @param child the classloader to test as a child.
   */
  private boolean isParentLoader(ClassLoader parent, ClassLoader child)
  {
    for (; child != null; child = child.getParent()) {
      if (child == parent)
        return true;
    }

    return false;
  }

  /**
   * Sets a custom logger if possible
   */
  public boolean addCustomLogger(Logger logger)
  {
    if (logger.getClass().getName().startsWith("java"))
      return false;
    
    Logger oldLogger = _localLoggers.get();

    if (oldLogger != null)
      return false;

    _localLoggers.set(logger);
    
    if (_parent != null) {
      logger.setParent(_parent);
    }

    return true;
  }

  /**
   * Gets the custom logger if possible
   */
  public Logger getLogger()
  {
    return _localLoggers.get();
  }

  /**
   * Adds a class loader to the list of dependency  loaders.
   */
  private void addLoader(ClassLoader loader)
  {
    for (int i = _loaders.size() - 1; i >= 0; i--) {
      WeakReference<ClassLoader> ref = _loaders.get(i);
      ClassLoader refLoader = ref.get();

      if (refLoader == null)
        _loaders.remove(i);

      if (refLoader == loader)
        return;
    }

    _loaders.add(new WeakReference<ClassLoader>(loader));
    EnvLoader.addClassLoaderListener(this, loader);
  }

  /**
   * Returns the assigned level, calculated through the normal
   * Logger rules, i.e. if unassigned, use the parent's value.
   */
  /*
  private Level getAssignedLevel()
  {
    for (Logger log = this; log != null; log = log.getParent()) {
      Level level = log.getLevel();

      if (level != null)
        return level;
    }

    return Level.INFO;
  }
  */

  /**
   * Recalculate the dynamic assigned levels.
   */
  private synchronized void updateEffectiveLevel(ClassLoader loader)
  {
    if (loader == null)
      loader = _systemClassLoader;

    int oldEffectiveLevel = getEffectiveLevel(loader);
    
    Level newEffectiveLevel = calculateEffectiveLevel(loader);

    /*
    if (loader == _systemClassLoader) {
      _finestEffectiveLevel = newEffectiveLevel;
      _finestEffectiveLevelValue = newEffectiveLevel.intValue();
      super.setLevel(_finestEffectiveLevel);
    }
    */
    
    if (oldEffectiveLevel == newEffectiveLevel.intValue()
        && loader != _systemClassLoader)
      return;
    
    _finestEffectiveLevel = newEffectiveLevel;
    _hasLocalEffectiveLevel = false;
    
    updateEffectiveLevelPart(_systemClassLoader);
    
    updateEffectiveLevelPart(loader);
        
    for (int i = 0; i < _loaders.size(); i++) {
      WeakReference<ClassLoader> loaderRef = _loaders.get(i);
      ClassLoader classLoader = loaderRef.get();
      
      if (classLoader != null)
        updateEffectiveLevelPart(classLoader);
    }
    
    super.setLevel(_finestEffectiveLevel);
        
    _finestEffectiveLevelValue = _finestEffectiveLevel.intValue();

    updateChildren();
  }
  
  private void updateChildren()
  {
    updateChildren(_systemClassLoader);
    for (int i = _loaders.size() - 1; i >= 0; i--) {
      WeakReference<ClassLoader> loaderRef = _loaders.get(i);
      ClassLoader subLoader = loaderRef.get();
      
      if (subLoader != null)
        updateChildren(subLoader);
      else
        _loaders.remove(i);
    }
  }
  
  private void updateEffectiveLevelPart(ClassLoader loader)
  {
    Level level = getOwnEffectiveLevel(loader);
    
    if (loader == _systemClassLoader) {
      _systemEffectiveLevelValue
        = (level != null ? level.intValue() : Level.INFO.intValue());
    }
    
    if (level == null) {
      if (_localEffectiveLevel != null)
        _localEffectiveLevel.remove(loader);
      
      return;
    }
    
    if (_finestEffectiveLevel == null)
      _finestEffectiveLevel = level;
    else if (level.intValue() < _finestEffectiveLevel.intValue()) {
      _finestEffectiveLevel = level;
    }
    
    if (loader == _systemClassLoader) {
      _systemEffectiveLevelValue = level.intValue();
    }
    else {
      _hasLocalEffectiveLevel = true;
      
      addLoader(loader);
      
      if (_localEffectiveLevel == null)
        _localEffectiveLevel = new EnvironmentLocal<Integer>();
      
      _localEffectiveLevel.set(level.intValue(), loader);
    }
  }
  
  private Level getOwnEffectiveLevel(ClassLoader loader)
  {
    Level level = null;
    
    if (loader == _systemClassLoader) {
      level = _systemLevel;
    }
    else if (_localLevel != null) {
      level = _localLevel.getLevel(loader);
    }
    
    if (level != null)
      return level;
    else if (_parent != null)
      return _parent.getOwnEffectiveLevel(loader);
    else
      return null;
  }
  
  private int getEffectiveLevel(ClassLoader loader)
  {
    int oldEffectiveLevel = _systemEffectiveLevelValue;
    
    if (_localEffectiveLevel != null) {
      Integer intLevel = _localEffectiveLevel.get(loader);
      
      if (intLevel != null)
        oldEffectiveLevel = intLevel;
    }
    
    return oldEffectiveLevel;
  }
  
  private Level calculateEffectiveLevel(ClassLoader loader)
  {
    Level level = getLevel(loader);
    
    if (level != null)
      return level;
    else if (_parent != null)
      return _parent.calculateEffectiveLevel(loader);
    else
      return Level.INFO;
  }

  private Level getLevel(ClassLoader loader)
  {
    if (_localLevel != null) {
      Level level = _localLevel.get(loader);

      if (level != null) {
        return level;
      }
    }

    return _systemLevel;
  }

  /**
   * Returns the finest assigned level for any classloader environment.
   */
  private Level getFinestLevel()
  {
    Level level;
    
    if (_parent == null)
      level = Level.INFO;
    else if (_parent.isLocalLevel())
      level = selectFinestLevel(_systemLevel, _parent.getFinestLevel());
    else if (_systemLevel != null)
      level = _systemLevel;
    else
      level = _parent.getFinestLevel();
    
    if (_localLevel == null)
      return level;
    
    for (int i = _loaders.size() - 1; i >= 0; i--) {
      WeakReference<ClassLoader> ref = _loaders.get(i);
      ClassLoader loader = ref.get();

      if (loader == null)
        _loaders.remove(i);

      level = selectFinestLevel(level, _localLevel.get(loader));
    }
    
    return level;
  }
  
  private boolean isLocalLevel()
  {
    if (_localLevel != null)
      return false;
    else if (_parent != null && ! _parent.isLocalLevel())
      return false;
    else
      return true;
  }

  /**
   * Returns the finest of the two levels.
   */
  private Level selectFinestLevel(Level a, Level b)
  {
    if (a == null)
      return b;
    else if (b == null)
      return a;
    else if (b.intValue() < a.intValue())
      return b;
    else
      return a;
  }
  
  /**
   * Returns the most specific assigned level for the given classloader, i.e.
   * children override parents.
   */
  private Level getAssignedLevel(ClassLoader loader)
  {
    Level level = null;
    
    if (_localLevel != null) {
      return _localLevel.get(loader);
    }
    
    return null;
  }

  private void updateClassLoaderLevel(ClassLoader loader)
  {
    if (_localLevel == null) {
      if (_systemLevel != null)
        super.setLevel(_systemLevel);
      return;
    }
    
    Level localLevel = _localLevel.get(loader);

    if (localLevel != null) {
      if (! _hasLocalEffectiveLevel)
        super.setLevel(localLevel);
      else if (localLevel.intValue() < super.getLevel().intValue())
        super.setLevel(localLevel);

      _hasLocalEffectiveLevel = true;
    }
  }
  
  private void updateChildren(ClassLoader loader)
  {
    for (int i = _children.size() - 1; i >= 0; i--) {
      WeakReference<EnvironmentLogger> ref = _children.get(i);
      EnvironmentLogger child = ref.get();

      if (child != null)
        child.updateEffectiveLevel(loader);
      else
        _children.remove(i);
    }
  }

  /**
   * Removes the specified loader.
   */
  private synchronized void removeLoader(ClassLoader loader)
  {
    int i;
    for (i = _loaders.size() - 1; i >= 0; i--) {
      WeakReference<ClassLoader> ref = _loaders.get(i);
      ClassLoader refLoader = ref.get();

      if (refLoader == null)
        _loaders.remove(i);
      else if (refLoader == loader)
        _loaders.remove(i);
    }
  }

  /**
   * Classloader init callback
   */
  public void classLoaderInit(DynamicClassLoader env)
  {
  }

  /**
   * Classloader destroy callback
   */
  @Override
  public void classLoaderDestroy(DynamicClassLoader loader)
  {
    removeLoader(loader);

    _localHandlers.remove(loader);

    HandlerEntry ownHandlers = _ownHandlers.getLevel(loader);
    if (ownHandlers != null)
      _ownHandlers.remove(loader);

    if (ownHandlers != null)
      ownHandlers.destroy();

    if (_localLevel != null)
      _localLevel.remove(loader);

    updateEffectiveLevel(_systemClassLoader);
  }

  @Override
  public String toString()
  {
    return "EnvironmentLogger[" + getName() + "]";
  }

  /**
   * Encapsulates the handler for this logger, keeping a reference in
   * the local environment to avoid GC.
   */
  static class HandlerEntry {
    private final EnvironmentLogger _logger;
    private ArrayList<Handler> _handlers = new ArrayList<Handler>();

    HandlerEntry(EnvironmentLogger logger)
    {
      _logger = logger;
    }

    void addHandler(Handler handler)
    {
      _handlers.add(handler);
    }

    void removeHandler(Handler handler)
    {
      _handlers.remove(handler);
    }

    void destroy()
    {
      ArrayList<Handler> handlers = _handlers;
      _handlers = null;
      
      for (int i = 0; handlers != null && i < handlers.size(); i++) {
        Handler handler = handlers.get(i);

        try {
          handler.close();
        } catch (Throwable e) {
          e.printStackTrace();
        }
      }
    }
  }
  
  static {
    try {
      _systemClassLoader = ClassLoader.getSystemClassLoader();
    } catch (Exception e) {
      // too early to log
    }
  }
}
