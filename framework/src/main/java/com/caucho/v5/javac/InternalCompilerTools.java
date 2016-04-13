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

package com.caucho.v5.javac;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import com.caucho.v5.config.ConfigException;
import com.caucho.v5.io.i18n.Encoding;
import com.caucho.v5.loader.DynamicClassLoader;
import com.caucho.v5.loader.NonScanDynamicClassLoader;
import com.caucho.v5.util.CharBuffer;
import com.caucho.v5.vfs.MemoryStream;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.ReadStreamOld;
import com.caucho.v5.vfs.WriteStreamOld;

/**
 * Compiles Java source, returning the loaded class.
 */
public class InternalCompilerTools extends AbstractJavaCompiler {
  private static final Logger log
    = Logger.getLogger(InternalCompilerTools.class.getName());

  private static boolean _hasCompiler; // already tested for compiler
  private static DynamicClassLoader _internalLoader;

  private static DynamicClassLoader _envClassLoader;

  private static JavaCompiler _javac;
  
  Process _process;
  String _userPrefix;
  
  boolean _isDead;
  
  public InternalCompilerTools(JavaCompilerUtil compiler)
  {
    super(compiler);
  }

  protected void compileInt(String []path, LineMap lineMap)
    throws IOException, JavaCompileException
  {
    executeInt(path, lineMap);
  }

  /**
   * Compiles the names files.
   */
  private void executeInt(String []path, LineMap lineMap)
    throws JavaCompileException, IOException
  {
    MemoryStream tempStream = new MemoryStream();
    WriteStreamOld error = new WriteStreamOld(tempStream);

    try {
      // String parent = javaPath.getParent().getNativePath();

      ArrayList<String> argList = new ArrayList<String>();
      argList.add("-d");
      argList.add(_compiler.getClassDirName());
      if (_compiler.getEncoding() != null) {
        String encoding = Encoding.getJavaName(_compiler.getEncoding());
        if (encoding != null && ! encoding.equals("ISO8859_1")) {
          argList.add("-encoding");
          argList.add(_compiler.getEncoding());
        }
      }

      argList.add("-classpath");
      argList.add(_compiler.getClassPath());

      ArrayList<String> args = _compiler.getArgs();
      if (args != null)
        argList.addAll(args);

      for (int i = 0; i < path.length; i++) {
        PathImpl javaPath = _compiler.getSourceDir().lookup(path[i]);
        argList.add(javaPath.getNativePath());
      }

      if (log.isLoggable(Level.FINER)) {
        CharBuffer msg = new CharBuffer();
        msg.append("javac(int)");
        for (int i = 0; i < argList.size(); i++) {
          if (argList.get(i).equals("-classpath")
              && ! log.isLoggable(Level.FINEST)) {
            i++;
            continue;
          }

          msg.append(" ");
          msg.append(argList.get(i));
        }

        log.finer(msg.toString());
      }

      String []argArray = argList.toArray(new String[argList.size()]);

      int status = -1;
      
      Thread thread = Thread.currentThread();
      ClassLoader oldLoader = thread.getContextClassLoader();

      // env = _internalLoader;


      try {
        thread.setContextClassLoader(_envClassLoader);
        
        if (_javac == null)
          throw new ConfigException(L.l("javac compiler is not available in {0}. Check that you are using the JDK, not the JRE.",
                                        System.getProperty("java.runtime.name")
                                        + " " + System.getProperty("java.runtime.version")));
        
        status = _javac.run(null, error, error, argArray);
      
        error.close();
        tempStream.close();
      } finally {
        thread.setContextClassLoader(oldLoader);
      }

      ReadStreamOld read = tempStream.openReadAndSaveBuffer();
      JavacErrorParser parser = new JavacErrorParser(this, path[0], _compiler.getEncoding());

      String errors = parser.parseErrors((InputStream) read, lineMap);
      read.close();

      if (errors != null)
        errors = errors.trim();

      if (log.isLoggable(Level.FINE)) {
        read = tempStream.openReadAndSaveBuffer();
        CharBuffer cb = new CharBuffer();
        int ch;
        while ((ch = read.read()) >= 0) {
          cb.append((char) ch);
        }
        read.close();

        log.fine(cb.toString());
      }
      else if (status == 0 && errors != null && ! errors.equals("")) {
        final String msg = errors;

        new com.caucho.v5.loader.ClassLoaderContext(_compiler.getClassLoader()) {
          public void run()
          {
            log.warning(msg);
          }
        };
      }

      if (status != 0)
        throw new JavaCompileException(errors);
    } finally {
      tempStream.destroy();
    }
  }
  
  static {
    _envClassLoader = new NonScanDynamicClassLoader(ClassLoader.getSystemClassLoader());
    
    /*
    Path javaHome = Vfs.lookup(System.getProperty("java.home"));
    Path jar = javaHome.lookup("./lib/tools.jar");
    if (jar.canRead())
      env.addJar(jar);
    jar = javaHome.lookup("../lib/tools.jar");
    if (jar.canRead())
      env.addJar(jar);
      */

    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    
    if (javac == null) {
      Iterator<JavaCompiler> iter
        = ServiceLoader.load(JavaCompiler.class).iterator();
      
      if (iter.hasNext())
        javac = iter.next();
    }
    
    _javac = javac;
  }
}
