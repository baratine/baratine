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

package com.caucho.v5.loader;

import java.net.URL;

import com.caucho.v5.util.TimedCache;

/**
 * Root class loader.
 */
public class RootDynamicClassLoader
  extends DynamicClassLoader
{
  private static final URL NULL_URL;
  
  private static final ClassLoader _systemClassLoader;
  private static final DynamicClassLoader _systemRootClassLoader;
  
  private TimedCache<String,String> _classNotFoundCache
    = new TimedCache<>(8192, 60 * 1000);
  
  private TimedCache<String,URL> _resourceCache
    = new TimedCache<>(8192, 60 * 1000);
  
  /**
   * Creates a new RootDynamicClassLoader.
   */
  private RootDynamicClassLoader(ClassLoader parent)
  {
    super(parent, false, true);
    
    if (parent instanceof DynamicClassLoader) {
      throw new IllegalStateException();
    }
  }
  
  static DynamicClassLoader create(ClassLoader parent)
  {
    if (parent instanceof DynamicClassLoader)
      return (DynamicClassLoader) parent;
    
    if (parent == _systemClassLoader)
      return _systemRootClassLoader;
    
    return new RootDynamicClassLoader(parent);
  }
  
  public static DynamicClassLoader getSystemRootClassLoader()
  {
    return _systemRootClassLoader;
  }
  
  @Override
  public boolean isRoot()
  {
    return true;
  }

  /**
   * Load a class using this class loader
   *
   * @param name the classname to load
   * @param resolve if true, resolve the class
   *
   * @return the loaded classes
   */
  @Override
  public Class<?> loadClassImpl(String name, boolean resolve)
    throws ClassNotFoundException
  {
    // The JVM has already cached the classes, so we don't need to
    Class<?> cl = findLoadedClass(name);

    if (cl != null) {
      if (resolve)
        resolveClass(cl);
      return cl;
    }
    // System.out.println("ROOT: " + name);
    if (_classNotFoundCache.get(name) != null) {
      return null;
    }
    
    try {
      cl = super.loadClassImpl(name, resolve);
    } catch (ClassNotFoundException e) {
      _classNotFoundCache.put(name, name);
      
      throw e;
    }
    
    if (cl == null) {
      _classNotFoundCache.put(name, name);
    }
    
    return cl;
  }
  
  @Override
  public URL getResource(String name)
  {
    URL url = _resourceCache.get(name);
    
    if (url == null) {
      url = super.getResource(name);
      
      if (url != null)
        _resourceCache.put(name, url);
      else
        _resourceCache.put(name, NULL_URL);
    }
    else if (url == NULL_URL) {
      url = null;
    }
    
    return url;
  }

  /*
  private void initSecurity()
  {
    addPermission(new AllPermission());
  }
  */
  static {
    URL nullUrl = null;
    
    try {
      nullUrl = new URL("file:///caucho.com/null");
    } catch (Exception e) {
      e.printStackTrace();
    }
    
    NULL_URL = nullUrl;
    
    ClassLoader systemClassLoader = null;
    
    try {
      systemClassLoader = ClassLoader.getSystemClassLoader();
    } catch (Exception e) {
    }
    
    try {
      if (systemClassLoader == null) {
        systemClassLoader = RootDynamicClassLoader.class.getClassLoader();
      }
    } catch (Exception e) {
    }
    
    _systemClassLoader = systemClassLoader;
    
    if (_systemClassLoader instanceof DynamicClassLoader)
      _systemRootClassLoader = (DynamicClassLoader) _systemClassLoader;
    else
      _systemRootClassLoader = new RootDynamicClassLoader(_systemClassLoader);
  }
}

