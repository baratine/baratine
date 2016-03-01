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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */
package com.caucho.v5.loader;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Creates a ClassLoader dependent variable. The value of the ClassLoaderLocal
 * variable depends on the context ClassLoader.
 */
public class EnvironmentLocal<E>
{
  // true on initialization if getting the system classloader is allowed,
  // i.e. not forbiggen by the security manager

  private static Boolean _isSystemClassLoader;

  private static AtomicLong _varCount = new AtomicLong();

  private String _varName;
  private E _globalValue;

  /**
   * Creates a new environment local variable with an anonymous identifier.
   */
  public EnvironmentLocal()
  {
    _varName = "baratine:var-" + _varCount.incrementAndGet();
  }

  public EnvironmentLocal(String varName)
  {
    _varName = varName;
  }

  public String getVariable()
  {
    return _varName;
  }

  /**
   * Returns the variable for the context classloader.
   */
  @SuppressWarnings("unchecked")
  public E get()
  {
    Thread thread = Thread.currentThread();
    ClassLoader loader = thread.getContextClassLoader();

    Object value = null;

    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        EnvironmentClassLoader envLoader = (EnvironmentClassLoader) loader;

        value = envLoader.getAttribute(_varName);

        if (value != null)
          return (E) value;
      }
    }

    return _globalValue;
  }

  /**
   * Returns the variable for the context classloader.
   */
  @SuppressWarnings("unchecked")
  public E get(ClassLoader loader)
  {
    Object value = null;

    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        EnvironmentClassLoader envLoader = (EnvironmentClassLoader) loader;

        value = envLoader.getAttribute(_varName);

        if (value != null)
          return (E) value;
      }
    }

    return _globalValue;
  }

  /**
   * Returns the variable for the context classloader.
   */
  @SuppressWarnings("unchecked")
  public E getLevel()
  {
    Thread thread = Thread.currentThread();
    ClassLoader loader = thread.getContextClassLoader();

    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        EnvironmentClassLoader envLoader = (EnvironmentClassLoader) loader;

        return (E) envLoader.getAttribute(_varName);
      }
    }

    return _globalValue;
  }

  /**
   * Returns the variable for the context classloader.
   */
  @SuppressWarnings("unchecked")
  public E getLevel(ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        EnvironmentClassLoader envLoader = (EnvironmentClassLoader) loader;

        return (E) envLoader.getAttribute(_varName);
      }
    }

    return _globalValue;
  }

  /**
   * Sets the variable for the context classloader.
   * 
   * @param value
   *          the new value
   * 
   * @return the old value
   */
  @SuppressWarnings("unchecked")
  public final E set(E value)
  {
    Thread thread = Thread.currentThread();
    ClassLoader loader = thread.getContextClassLoader();

    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        EnvironmentClassLoader envLoader = (EnvironmentClassLoader) loader;

        return (E) envLoader.setAttribute(_varName, value);
      }
    }

    return setGlobal(value);
  }

  /**
   * Sets the variable for the context classloader.
   * 
   * @param value
   *          the new value
   * 
   * @return the old value
   */
  @SuppressWarnings("unchecked")
  public final E set(E value, ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        EnvironmentClassLoader envLoader = (EnvironmentClassLoader) loader;

        return (E) envLoader.setAttribute(_varName, value);
      }
    }

    return setGlobal(value);
  }

  public void setIfAbsent(E value)
  {
    set(value);
  }

  /**
   * Removes this variable
   * 
   * @return the old value
   */
  @SuppressWarnings("unchecked")
  public final E remove()
  {
    Thread thread = Thread.currentThread();
    ClassLoader loader = thread.getContextClassLoader();

    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        EnvironmentClassLoader envLoader = (EnvironmentClassLoader) loader;

        return (E) envLoader.removeAttribute(_varName);
      }
    }

    return setGlobal(null);
  }

  /**
   * Removes the variable for the context classloader.
   * 
   * @return the old value
   */
  @SuppressWarnings("unchecked")
  public final E remove(ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        EnvironmentClassLoader envLoader = (EnvironmentClassLoader) loader;

        return (E) envLoader.removeAttribute(_varName);
      }
    }

    return setGlobal(null);
  }

  /**
   * Sets the global value.
   * 
   * @param value
   *          the new value
   * 
   * @return the old value
   */
  public E setGlobal(E value)
  {
    E oldValue = _globalValue;

    _globalValue = value;

    ClassLoader systemLoader = getSystemClassLoader();

    if (systemLoader instanceof EnvironmentClassLoader)
      ((EnvironmentClassLoader) systemLoader).setAttribute(_varName, value);
    else
      _globalValue = value;

    return oldValue;
  }

  /**
   * Returns the global value.
   */
  @SuppressWarnings("unchecked")
  public E getGlobal()
  {
    ClassLoader systemLoader = getSystemClassLoader();

    if (systemLoader instanceof EnvironmentClassLoader)
      return (E) ((EnvironmentClassLoader) systemLoader).getAttribute(_varName);
    else
      return _globalValue;
  }

  public static ClassLoader getSystemClassLoader()
  {
    if (_isSystemClassLoader == null) {
      _isSystemClassLoader = false;

      try {
        ClassLoader.getSystemClassLoader();

        _isSystemClassLoader = true;
      } catch (Throwable e) {
      }
    }

    if (_isSystemClassLoader)
      return ClassLoader.getSystemClassLoader();
    else
      return null;
  }
}