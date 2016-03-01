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

import java.util.Objects;

import javax.annotation.PostConstruct;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.util.L10N;

/**
 * class-loader: configures class loaders in the current environment.
 */
public class ClassLoaderConfig {
  private final static L10N L = new L10N(ClassLoaderConfig.class);

  private EnvironmentClassLoader _classLoader;

  public ClassLoaderConfig()
    throws ConfigException
  {
    Thread thread = Thread.currentThread();

    ClassLoader loader = thread.getContextClassLoader();

    if (! (loader instanceof EnvironmentClassLoader)) {
      Thread.dumpStack();
      throw new ConfigException(L.l("<class-loader> requires an EnvironmentClassLoader."));
    }

    _classLoader = (EnvironmentClassLoader) loader;
  }

  public ClassLoaderConfig(EnvironmentClassLoader classLoader)
    throws ConfigException
  {
    Objects.requireNonNull(classLoader);
    
    _classLoader = classLoader;
  }

  /**
   * compiling-loader: loads classes in a directory, compiling if necessary
   */
  public CompilingLoader createCompilingLoader()
  {
    return new CompilingLoader(_classLoader);
  }

  /**
   * library-loader: loads jars in a directory, e.g. WEB-INF/lib
   */
  public LibraryLoader createLibraryLoader()
  {
    return new LibraryLoader(_classLoader);
  }

  /**
   * priority-package: used with servlet-hack
   * 
   * Add a package for which this class loader will
   * take precendence over the parent. Any class that
   * has a qualified name that starts with the passed value
   * will be loaded from this classloader instead of the
   * parent classloader.
   */
  public void addPriorityPackage(String priorityPackage)
  {
    _classLoader.addPriorityPackage(priorityPackage);
  }

  /**
   * servlet-hack: reversed normal classloader order
   */
  public void setServletHack(boolean hack)
  {
    //_classLoader.setServletHack(hack);
  }

  /**
   * simple-loader: loads classes in a directory
   */
  public SimpleLoader createSimpleLoader()
  {
    return new SimpleLoader(_classLoader);
  }

  /**
   * tree-loader: loads jars in a directory tree
   */
  public TreeLoader createTreeLoader()
  {
    return new TreeLoader(_classLoader);
  }

  /**
   * Adds an enhancing loader.
   */
  /*
  public EnhancerManager createEnhancer()
    throws ConfigException
  {
    return EnhancerManager.create();
  }
  */

  /**
   * init
   */
  @PostConstruct
  public void init()
    throws ConfigException
  {
    //_classLoader.init();

    //_classLoader.validate();
  }

  public String toString()
  {
    return "ClassLoaderConfig[]";
  }
}


