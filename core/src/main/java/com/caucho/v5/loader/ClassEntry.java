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

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.security.CodeSource;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.Dependency;
import com.caucho.v5.io.IoUtil;
import com.caucho.v5.io.Path;
import com.caucho.v5.io.Source;
import com.caucho.v5.util.ByteArrayBuffer;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.ModulePrivate;

/**
 * Describes a cached loaded class entry.
 */
@ModulePrivate
public class ClassEntry implements Dependency
{
  private static final L10N L = new L10N(ClassEntry.class);
  private static final Logger log
    = Logger.getLogger(ClassEntry.class.getName());
  
  private static boolean _hasJNIReload;
  private static boolean _hasAnnotations;
  //private static final JniTroubleshoot _jniTroubleshoot;

  private DynamicClassLoader _loader;
  private String _name;
    
  private Source _classPath;

  private Dependency _depend;

  private boolean _classIsModified;
    
  private Source _sourcePath;
  private long _sourceLastModified;
  private long _sourceLength;

  private ClassPackage _classPackage;

  private CodeSource _codeSource;

  private WeakReference<Class<?>> _clRef;

  /**
   * Create a loaded class entry
   *
   * @param name the classname
   * @param sourcePath path to the source Java file
   * @param classPath path to the compiled class file
   */
  public ClassEntry(DynamicClassLoader loader,
                    String name, 
                    Path sourcePath,
                    Path classPath,
                    CodeSource codeSource)
  {
    _loader = loader;
    _name = name;
    
    _classPath = classPath;

    setDependPath(classPath);

    if (sourcePath != null && ! sourcePath.equals(classPath)) {
      _sourcePath = sourcePath;
      
      _sourceLastModified = sourcePath.getLastModified();
      _sourceLength = sourcePath.length();
    }

    _codeSource = codeSource;
  }

  /**
   * Create a loaded class entry
   *
   * @param name the classname
   * @param sourcePath path to the source Java file
   * @param classPath path to the compiled class file
   */
  public ClassEntry(Loader loader,
                    String name, Path sourcePath,
                    Path classPath)
  {
    this(loader.getClassLoader(), name, sourcePath, classPath,
         loader.getCodeSource(classPath));
  }

  public String getName()
  {
    return _name;
  }

  /**
   * returns the class loader.
   */
  public DynamicClassLoader getClassLoader()
  {
    return _loader;
  }

  public CodeSource getCodeSource()
  {
    return _codeSource;
  }

  public Source getSourcePath()
  {
    return _sourcePath;
  }

  /**
   * Sets the depend path.
   */
  protected void setDependPath(Path dependPath)
  {
    /*
    if (dependPath instanceof JarPath)
      _depend = ((JarPath) dependPath).getDepend();
    else
      _depend = new Depend(dependPath);
      */
  }

  /**
   * Adds the dependencies, returning true if it's adding itself.
   */
  protected boolean addDependencies(DependencyContainer container)
  {
    /* XXX:
    if (_classPath instanceof JarPath) {
      container.add(_depend);
      return false;
    }
    else
    */ 
    if (_hasJNIReload) {
      container.add(this);
      return true;
    }
    else if (_sourcePath == null) {
      container.add(_depend);
      return false;
    }
    else {
      container.add(this);
      return true;
    }
  }
  
  public void setSourceLength(long length)
  {
    _sourceLength = length;
  }
  
  public void setSourceLastModified(long lastModified)
  {
    _sourceLastModified = lastModified;
  }
  
  public ClassPackage getClassPackage()
  {
    return _classPackage;
  }

  public void setClassPackage(ClassPackage pkg)
  {
    _classPackage = pkg;
  }

  /**
   * Returns true if the source file has been modified.
   */
  @Override
  public boolean isModified()
  {
    if (_depend == null) {
      return false;
    }
    
    if (_depend.isModified()) {
      if (log.isLoggable(Level.FINE))
        log.fine("class modified: " + _depend);

      return reloadIsModified();
    }
    else if (_sourcePath == null) {
      return false;
    }  
    else if (_sourcePath.getLastModified() != _sourceLastModified) {
      if (log.isLoggable(Level.FINE))
        log.fine("source modified time: " + _sourcePath +
                 " old:" + new Date(_sourceLastModified) +
                 " new:" + new Date(_sourcePath.getLastModified()));

      if (! compileIsModified()) {
        return false;
      }
      
      boolean isModified = reloadIsModified();

      return isModified;
    }
    else if (_sourcePath.length() != _sourceLength) {
      if (log.isLoggable(Level.FINE))
        log.fine("source modified length: " + _sourcePath +
                 " old:" + _sourceLength +
                 " new:" + _sourcePath.length());

      if (! compileIsModified()) {
        return false;
      }

      return reloadIsModified();
    }
    else {
      return false;
    }
  }

  /**
   * Returns true if the source file has been modified.
   */
  @Override
  public boolean logModified(Logger log)
  {
    if (_depend.logModified(log)) {
      return true;
    }
    else if (_sourcePath == null)
      return false;
      
    else if (_sourcePath.getLastModified() != _sourceLastModified) {
      log.info("source modified time: " + _sourcePath +
               " old:" + new Date(_sourceLastModified) +
               " new:" + new Date(_sourcePath.getLastModified()));

      return true;
    }
    else if (_sourcePath.length() != _sourceLength) {
      log.info("source modified length: " + _sourcePath +
               " old:" + _sourceLength +
               " new:" + _sourcePath.length());

      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Returns true if the compile doesn't avoid the dependency.
   */
  public boolean compileIsModified()
  {
    return true;
  }

  /**
   * Returns true if the reload doesn't avoid the dependency.
   */
  public boolean reloadIsModified()
  {
    if (_classIsModified) {
      return true;
    }

    if (! _hasJNIReload || ! _classPath.canRead()) {
      return true;
    }
    
    try {
      long length = _classPath.length();

      Class<?> cl = _clRef != null ? _clRef.get() : null;

      if (cl == null) {
        return false;
      }

      /*
      if (cl.isAnnotationPresent(RequireReload.class))
        return true;
        */

      byte []bytecode = new byte[(int) length];
      try (InputStream is = _classPath.inputStream()) {
        IoUtil.readAll(is, bytecode, 0, bytecode.length);
      }

      int result = reloadNative(cl, bytecode, 0, bytecode.length);

      if (result != 0) {
        _classIsModified = true;
        return true;
      }

      // XXX: setDependPath(_classPath);

      if (_sourcePath != null) {
        _sourceLastModified = _sourcePath.getLastModified();
        _sourceLength = _sourcePath.length();
      }

      log.info("Reloading " + cl.getName());

      return false;
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
      _classIsModified = true;

      return true;
    }
  }

  /**
   * Returns the path to the class file.
   */
  public Source getClassPath()
  {
    return _classPath;
  }

  public Class<?> getEntryClass()
  {
    return _clRef != null ? _clRef.get() : null;
  }

  public void setEntryClass(Class<?> cl)
  {
    _clRef = new WeakReference<Class<?>>(cl);
  }

  /**
   * preload actions.
   */
  public void preLoad()
    throws ClassNotFoundException
  {
  }

  /**
   * Loads the contents of the class file into the buffer.
   */
  public void load(ByteArrayBuffer buffer)
    throws IOException
  {
    synchronized (this) {
      Source classPath = getClassPath();
    
      buffer.clear();

      int retry = 3;
      for (int i = 0; i < retry; i++) {
        long length = -1;
        
        try (InputStream is = classPath.inputStream()) {
          length = classPath.length();
          long lastModified = classPath.getLastModified();

          if (length < 0)
            throw new IOException("class loading failed because class file '" + classPath + "' does not have a positive length.  Possibly the file has been overwritten");

          buffer.setLength((int) length);

          int results = IoUtil.readAll(is, buffer.getBuffer(), 0, (int) length);

          if (results == length
              && length == classPath.length()
              && lastModified == classPath.getLastModified()) {
            return;
          }

          log.warning(L.l("{0}: class file length mismatch expected={1} received={2}.  The class file may have been modified concurrently.",
                this, length, results));
        }
      }
    }
  }

  /**
   * post-load actions.
   */
  public boolean postLoad()
  {
    return false;
  }

  public static boolean isReloadEnabled()
  {
    return _hasJNIReload;
  }

  public String toString()
  {
    if (_sourcePath == null)
      return getClass().getSimpleName() + "[" + _classPath + "]";
    else
      return getClass().getSimpleName() +  "[" + _classPath + ", src=" + _sourcePath + "]";
  }

  /*
  public static native boolean canReloadNative();

  public static native int reloadNative(Class<?> cl,
                                        byte []bytes, int offset, int length);
                                        */
  
  public static boolean canReloadNative() { return false; }
  public static int reloadNative(Class<?> cl,
                                 byte []bytes, int offset, int length) { return -1; }

  class ReloadThread implements Runnable {
    private volatile boolean _isDone;

    public boolean isDone()
    {
      return _isDone;
    }
    
    public void run()
    {
    }
  }
  
  static {
    /* XXX:
    _jniTroubleshoot
      = JniUtil.load(SelectManagerJni.class,
                     new JniLoad() { 
                       public void load(String path) { System.load(path); }},
                     "baratine");
                     */

    try {
      boolean hasJNIReload = canReloadNative();
      
      // XXX:
      hasJNIReload = false;
      
      _hasJNIReload = hasJNIReload;

      if (CurrentTime.isTest()) {
        // skip logging for test
      }
      else if (_hasJNIReload)
        log.config("In-place class redefinition (HotSwap) is available.");
      else
        log.config("In-place class redefinition (HotSwap) is not available.  In-place class reloading during development requires a compatible JDK and -Xdebug.");
    } 
    catch (Throwable e) {
      log.log(Level.FINEST, e.toString(), e);
    }
  }
}
