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
import java.security.CodeSource;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.thread.ThreadPool;
import com.caucho.v5.io.Path;
import com.caucho.v5.io.Source;
import com.caucho.v5.javac.CompileClassNotFound;
import com.caucho.v5.util.CauchoUtil;
import com.caucho.v5.util.L10N;
import com.caucho.v5.vfs.PathImpl;

class CompilingClassEntry extends ClassEntry {
  private static final L10N L = new L10N(CompilingClassEntry.class);
  private static final Logger log
    = Logger.getLogger(CompilingClassEntry.class.getName());
  
  private CompilingLoader _loader;
  private boolean _compileIsModified;
  private AtomicBoolean _isCompiling = new AtomicBoolean();
    
  public CompilingClassEntry(CompilingLoader compilingLoader,
                             DynamicClassLoader loader,
                             String name, PathImpl sourcePath,
                             PathImpl classPath,
                             CodeSource codeSource)
  {
    super(loader, name, sourcePath, classPath, codeSource);

    _loader = compilingLoader;
  }

  @Override
  public void preLoad()
    throws ClassNotFoundException
  {
    String javaName = getName().replace('.', '/') + ".java";
    
    Source javaFile = getSourcePath();
    Source classFile = getClassPath();
    
    if (javaFile.getLastModified() <= classFile.getLastModified()) {
      log.finest(L.l("loading pre-compiled class {0} from {1}",
                     getName(), classFile));
      return;
    }

    if (! javaFile.canRead())
      return;
    
    try {
      ((Path) classFile).remove();
    } catch (IOException e) {
    }
      
    String sourcePath = _loader.prefixClassPath(_loader.getClassLoader().getSourcePath());

    // deal with windows case nuttiness
    if (CauchoUtil.isWindows()
        && ! _loader.checkSource(_loader.getSource(), javaName))
      return;

    _loader.compileClass(javaFile, classFile, sourcePath, false);

    if (classFile.canRead()) {
      log.fine(L.l("loading compiled class {0} from {1}",
                   getName(), classFile));
    }
    else if (javaFile.exists())
      throw new CompileClassNotFound(L.l("{1} does not have a class file because compiling {0} didn't produce a {1} class",
                                         javaFile, getName()));

    //setDependPath(classFile);
  }

  /**
   * Returns true if the compile doesn't avoid the dependency.
   */
  public boolean compileIsModified()
  {
    if (_compileIsModified)
      return true;

    CompileThread compileThread = new CompileThread();
    ThreadPool.current().start(compileThread);

    try {
      synchronized (compileThread) {
        if (! compileThread.isDone())
          compileThread.wait(5000);
      }

      if (_compileIsModified)
        return true;
      else if (compileThread.isDone()) {
        //setDependPath(getClassPath());

        return reloadIsModified();
      }
      else
        return true;
    } catch (Throwable e) {
    }

    return false;
  }

  class CompileThread implements Runnable {
    private volatile boolean _isDone;

    public boolean isDone()
    {
      return _isDone;
    }
    
    public void run()
    {
      Source sourcePath = getSourcePath();
      
      long length = sourcePath.length();
      long lastModified = sourcePath.getLastModified();

      try {
        _loader.compileClass(getSourcePath(), getClassPath(),
                             getSourcePath().getPath(), false);

        setSourceLength(length);
        setSourceLastModified(lastModified);
      } catch (Throwable e) {
        log.log(Level.FINE, e.toString(), e);

        _compileIsModified = true;
      }

      _isDone = true;

      synchronized (this) {
        notifyAll();
      }
    }
  }

  public String toString()
  {
    return getClass().getSimpleName() + "[" + getClassPath() + ", src=" + getSourcePath() + "]";
  }
}
