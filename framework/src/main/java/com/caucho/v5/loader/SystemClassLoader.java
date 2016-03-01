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

import java.io.File;
import java.io.IOException;
import java.security.AllPermission;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.VfsOld;

/**
 * ClassLoader that initalizes the environment and allows byte code
 * enhancement of classes in the system classpath.
 * <pre>
 * java -Djava.system.class.loader=com.caucho.loader.SystemClassLoader ...
 * </pre>
 * If the system property "system.conf" is defined, it is used as a path
 * to a configuration file that initializes the enviornment.  Relative paths
 * are relative to the current directory (See {@link com.caucho.v5.vfs.VfsOld#getPwd()}.
 * <p/>
 * Resources defined in system.conf are available to all classes loaded within the jvm.
 * <pre>
 * java -Dsystem.conf=tests/system.conf -Djava.system.class.loader=com.caucho.loader.SystemClassLoader ...
 * </pre>
 */
public class SystemClassLoader
  extends EnvironmentClassLoader
  implements EnvironmentBean
{
  private static Logger _log;
  private final AtomicBoolean _isInit = new AtomicBoolean();
  private boolean _hasBootClassPath;

  /**
   * Creates a new SystemClassLoader.
   */
  public SystemClassLoader(ClassLoader parent)
  {
    super(parent, "system", true);

    String preScan = System.getProperty("caucho.jar.prescan");
    String osArch = System.getProperty("os.arch");
    
    // #4420 - major performance for Spring-like startup if preScan is disabled
    // preScan = "false";
    
    if (! "false".equals(preScan) && ! "arm".equals(osArch)) {
      DynamicClassLoader.setJarCacheEnabled(true);
    }

    String smallmem = System.getProperty("caucho.smallmem");
    
    if (smallmem != null && ! "false".equals(smallmem)) {
      DynamicClassLoader.setJarCacheEnabled(false);
    }
  }

  @Override
  public boolean isJarCacheEnabled()
  {
    return DynamicClassLoader.isJarCacheEnabledDefault();
  }

  @Override
  public ClassLoader getClassLoader()
  {
    return this;
  }
  
  @Override
  public boolean isRoot()
  {
    return true;
  }

  @Override
  public void init()
  {
    if (_isInit.getAndSet(true))
      return;
    
    initSecurity();

    initClasspath();
    
    // This causes problems with JCE
    _hasBootClassPath = false;

    super.init();

    /*
    String systemConf = System.getProperty("system.conf");

    if (systemConf != null) {
      try {
        Path path = Vfs.lookup(systemConf);

        Config config = new Config();

        config.configure(this, path, getSchema());
      }
      catch (Exception ex) {
        ex.printStackTrace();

        throw new RuntimeException(ex.toString());
      }
    }
    */
  }

  private void initClasspath()
  {
    boolean isValid = false;
    
    try {
      String boot = System.getProperty("sun.boot.class.path");
      if (boot != null) {
        initClasspath(boot);
        _hasBootClassPath = true;

        initExtDirs("java.ext.dirs");
        initExtDirs("java.endorsed.dirs");
      }
    
      initClasspath(System.getProperty("java.class.path"));

      isValid = true;
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (! isValid)
        _hasBootClassPath = false;
    }
  }

  private void initExtDirs(String prop)
    throws IOException
  {
    String extDirPath = System.getProperty(prop);

    if (extDirPath == null)
      return;

    for (String extDir : extDirPath.split(File.pathSeparator, 512)) {
      PathImpl dir = VfsOld.lookup(extDir);

      for (String fileName : dir.list()) {
        PathImpl root = dir.lookup(fileName);

        try {
          // #2659
          if (root.isDirectory()
              || root.isFile() && (root.getPath().endsWith(".jar")
                                   || root.getPath().endsWith(".zip"))) {
            // XXX:addRoot(root);
          }
        } catch (Throwable e) {
          _hasBootClassPath = false;
          e.printStackTrace();
        }
      }
    }
  }

  private void initClasspath(String classpath)
  {
    String[] classpathElements = classpath.split(File.pathSeparator, 512);

    for (String classpathElement : classpathElements) {
      PathImpl root = VfsOld.lookup(classpathElement);

      try {
        if (root.exists()) {
          // XXX: addRoot(root);
        }
      } catch (Throwable e) {
        _hasBootClassPath = false;
        e.printStackTrace();
      }
    }
  }

  @Override
  protected void initEnvironment()
  {
    // disable for terracotta
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
      if (resolve) {
        resolveClass(cl);
      }
      
      return cl;
    }

    String className = name;
    
    if (_hasBootClassPath) {
      className = name.replace('.', '/') + ".class";

      if (findPath(className) == null) {
        return null;
      }
    }

    try {
      return super.loadClassImpl(name, resolve);
    } catch (Error e) {
      className = name.replace('.', '/') + ".class";
      
      if (findPath(className) != null) {
        String msg =  (e + "\n  while loading " + name + " in " + this
                       + "\n  which exists in " + findPath(className)
                       + "\n  check for missing dependencies");
        
        log().warning(msg);
      }
      
      throw e;
    } catch (ClassNotFoundException e) {
      className = name.replace('.', '/') + ".class";
      
      if (findPath(className) != null) {
        String msg =  (e + "\n  " + name + " in " + this
            + "\n  exists in " + findPath(className)
            + "\n  check for missing dependencies");
        
        log().fine(msg);
        
        throw new ClassNotFoundException(e.getMessage() + "\n" + msg, e);
      }
      
      throw e;
    }
  }

  protected String getSchema()
  {
    return "com/caucho/loader/system.rnc";
  }
  
  private void initSecurity()
  {
    addPermission(new AllPermission());
  }
  
  private static Logger log()
  {
    if (_log == null) {
      _log = Logger.getLogger(SystemClassLoader.class.getName());
    }
    
    return _log;
  }
}


