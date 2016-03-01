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

package com.caucho.v5.javac;

import com.caucho.v5.config.DisplayableException;
import com.caucho.v5.util.Alarm;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * Compiles Java source, returning the loaded class.
 */
abstract public class AbstractJavaCompiler implements Runnable {
  protected static final L10N L = new L10N(AbstractJavaCompiler.class);
  protected static final Logger log
    = Logger.getLogger(AbstractJavaCompiler.class.getName());

  private ClassLoader _loader;
  
  protected JavaCompilerUtil _compiler;
  private final AtomicBoolean _isDone = new AtomicBoolean();

  // path of source files
  private String []_path;
  private LineMap _lineMap;

  private Throwable _exception;
  
  public AbstractJavaCompiler(JavaCompilerUtil compiler)
  {
    _loader = Thread.currentThread().getContextClassLoader();
    
    _compiler = compiler;
  }

  /**
   * Sets the path of files to compile.
   */
  public void setPath(String []path)
  {
    _path = path;
  }

  /**
   * Sets the LineMap for the file
   */
  public void setLineMap(LineMap lineMap)
  {
    _lineMap = lineMap;
  }

  /**
   * Returns any compile exception.
   */
  public Throwable getException()
  {
    return _exception;
  }

  /**
   * Returns true when the compilation is done.
   */
  public boolean isDone()
  {
    return _isDone.get();
  }

  /**
   * runs the compiler.
   */
  @Override
  public void run()
  {
    try {
      runImpl();
    } catch (final Throwable e) {
      // env/0203 vs env/0206
      if (e instanceof DisplayableException)
        log.warning(e.getMessage());
      else
        log.warning(e.toString());
      
      _exception = e;
    } finally {
      notifyComplete();
    }
  }

  public void runImpl() throws IOException
  {
    Thread thread = Thread.currentThread();
    
    try {
      thread.setContextClassLoader(_loader);

      compileInt(_path, _lineMap);
    } finally {
      thread.setContextClassLoader(null);
    }
  }

  protected final void waitForComplete(long timeout)
  {
    long endTime = CurrentTime.getCurrentTimeActual() + timeout;

    synchronized (_isDone) {
      Thread.interrupted();
      while (! isDone()
             && CurrentTime.getCurrentTimeActual() <= endTime) {
        
        try {
          _isDone.wait(endTime - CurrentTime.getCurrentTimeActual());
        } catch (Exception e) {
          Thread.interrupted();
        }
      }
    }
  }

  protected void notifyComplete()
  {
    synchronized (_isDone) {
      _isDone.set(true);
      _isDone.notifyAll();
    }
  }

  /**
   * Quit the compilation.
   */
  public void abort()
  {
  }

  /**
   * Compile the configured file.
   *
   * @param path the path to the java source.
   * @param lineMap mapping from the generated source to the original files.
   */
  abstract protected void compileInt(String []path, LineMap lineMap)
    throws IOException;
}
