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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.i18n.Encoding;
import com.caucho.v5.util.Alarm;
import com.caucho.v5.util.CauchoUtil;
import com.caucho.v5.util.CharBuffer;
import com.caucho.v5.vfs.MemoryStream;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.ReadStreamOld;
import com.caucho.v5.vfs.VfsOld;
import com.caucho.v5.vfs.WriteStreamOld;

/**
 * Compiles Java source, returning the loaded class.
 */
public class ExternalCompiler extends AbstractJavaCompiler
{
  protected static final Logger log
    = Logger.getLogger(ExternalCompiler.class.getName());
  
  Process _process;
  String _userPrefix;
  InputStream _errorStream;
  InputStream _inputStream;
  
  boolean _isDead;
  
  public ExternalCompiler(JavaCompilerUtil compiler)
  {
    super(compiler);
  }

  /**
   * Compile the configured file.
   *
   * @param path the path to the java source.
   * @param lineMap mapping from the generated source to the original files.
   */
  protected void compileInt(String []paths, LineMap lineMap)
    throws IOException
  {
    MemoryStream tempStream = new MemoryStream();
    WriteStreamOld error = new WriteStreamOld(tempStream);
    _inputStream = null;
    _errorStream = null;
    boolean chdir = CauchoUtil.isUnix();

    _process = null;

    try {
      String javac = _compiler.getCompiler();
      String sourceExt = _compiler.getSourceExtension();

      String path = paths[0];
      int tail = path.length() - sourceExt.length();
      String className = path.substring(0, tail);
      PathImpl classFile = _compiler.getClassDir().lookup(className + ".class");

      ArrayList<String> argList = new ArrayList<String>();
      argList.add(javac);

      ArrayList<String> args = _compiler.getArgs();
      if (args != null)
        argList.addAll(args);

      ArrayList<String> envList = new ArrayList();

      if (javac.endsWith("jikes") || javac.endsWith("jikes.exe")) {
        // make jikes look in the source path allowing for class dependencies
        // argList.add("+CSO");
        // "emacs" style error messages, understood by JavacErrorParser
        argList.add("+E");

        chdir = false;
        
        if (_compiler.getEncoding() != null) {
          argList.add("-encoding");
          argList.add(_compiler.getEncoding());
        }
        /*
         * XXX: this encoding is needed for jikes to handle ISO-8859-1,
         * but won't work on several jikes installations.
        else {
          argList.add("-encoding");
          argList.add("LATIN1");
        }
        */
      }
      else if (_compiler.getEncoding() != null) {
        String encoding = Encoding.getJavaName(_compiler.getEncoding());
        argList.add("-encoding");
        argList.add(encoding);
      }

      String classPath = normalizeClassPath(_compiler.getClassPath(), ! chdir);

      envList.add("CLASSPATH=" + classPath);

      if (_compiler.getCompiler().endsWith("groovyc")) {
        argList.add("--classpath");
        argList.add(classPath);
      }
      else {
        argList.add("-classpath");
        argList.add(classPath);
      }

      argList.add("-d");
      argList.add(normalizePath(_compiler.getClassDirName(), ! chdir));

      for (int i = 0; i < paths.length; i++) {
        if (chdir)
          argList.add(paths[i]);
        else {
          PathImpl javaPath = _compiler.getSourceDir().lookup(paths[i]);
          argList.add(javaPath.getNativePath());
        }
      }

      if (log.isLoggable(Level.FINE))
        log.fine(String.valueOf(argList));

      _process = executeCompiler(argList, envList, chdir);
      if (_process != null) {
        _inputStream = _process.getInputStream();
        _errorStream = _process.getErrorStream();
      }

      /*
      Alarm alarm = null;
      
      if (_compiler.getMaxCompileTime() > 1000)
        alarm = new Alarm(this, _maxCompileTime);
      */

      int status = 666;

      try {
        waitForErrors(error, _inputStream, _errorStream);
        
        if (_process != null) {
          status = _process.waitFor();
          _process = null;
        }
      } catch (Throwable e) {
        if (_isDead)
          throw new JavaCompileException(L.l("The compilation has timed out.  You can increase the timeout value by changing the max-compile-time."));

        throw new IOException(e);
      }
      /*
      finally {

        if (alarm != null)
          alarm.dequeue();
      }
      */

      if (_process != null) {
        status = 666;
      }

      error.close();
      tempStream.close();

      if (log.isLoggable(Level.FINE)) {
        ReadStreamOld read = tempStream.openReadAndSaveBuffer();
        CharBuffer cb = new CharBuffer();
        int ch;

        while ((ch = read.read()) >= 0)
          cb.append((char) ch);
        read.close();
        final String msg = cb.toString();

        new com.caucho.v5.loader.ClassLoaderContext(_compiler.getClassLoader()) {
          public void run()
          {
            log.fine(msg);
          }
        };
      }

      ReadStreamOld read = tempStream.openReadAndSaveBuffer();
      ErrorParser parser;
        
      // the javac error parser will work with jikes in "emacs" mode
      parser = new JavacErrorParser();

      String errors = parser.parseErrors(read, lineMap);
      read.close();

      if (errors != null)
        errors = errors.trim();

      if (status == 0 && classFile.length() > 0) {
        if (errors != null && ! errors.equals("")) {
          final String msg = errors;

          new com.caucho.v5.loader.ClassLoaderContext(_compiler.getClassLoader()) {
            public void run()
            {
              log.warning(msg);
            }
          };
        }

        return;
      }

      if (errors == null || errors.equals("")) {
        CharBuffer cb = new CharBuffer();

        if (status == 0) {
          cb.append("Compilation for '" + className + "' did not generate a .class file.\n");
          cb.append("Make sure the `package' matches the directory.\n");
        }
        else
          cb.append("Unknown compiler error executing:\n");

        for (int i = 0; i < argList.size(); i++)
          cb.append(" " + argList.get(i) + "\n");

        read = tempStream.openReadAndSaveBuffer();
        int ch;
        while ((ch = read.read()) >= 0)
          cb.append((char) ch);
        read.close();
        errors = cb.toString();
      }
      else if (errors.indexOf("command not found") >= 0) {
        throw new JavaCompileException(L.l("Can't execute the compiler `{0}'.  This usually means that the compiler is not in the operating system's PATH or the compiler is incorrectly specified in the configuration.  You may need to add the full path to <java compiler='{0}'/>.\n\n{1}", argList.get(0), errors));
      }

      throw new JavaCompileException(errors);
    } finally {
      if (_inputStream != null) {
        try {
          _inputStream.close();
        } catch (Throwable e) {
        }
      }

      if (_errorStream != null) {
        try {
          _errorStream.close();
        } catch (Throwable e) {
        }
      }

      if (_process != null) {
        try {
          _process.destroy();
        } catch (Throwable e) {
          log.log(Level.FINE, e.toString(), e);
        }
      }
      
      tempStream.destroy();
    }
  }

  /**
   * Read any errors from the process.
   */
  private void waitForErrors(WriteStreamOld error,
                             InputStream inputStream,
                             InputStream errorStream)
    throws IOException
  {
    byte []buffer = new byte[256];
    int stderrLen = 0;
    int stdoutLen = 0;

    if (inputStream == null || errorStream == null)
      return;
    
    do {
      if ((stderrLen = errorStream.available()) > 0) {
        stderrLen = errorStream.read(buffer, 0, buffer.length);
        
        if (stderrLen > 0) {
          error.write(buffer, 0, stderrLen);
          continue;
        }
      }
      
      if ((stdoutLen = inputStream.available()) > 0) {
        stdoutLen = inputStream.read(buffer, 0, buffer.length);
        
        if (stdoutLen > 0) {
          error.write(buffer, 0, stdoutLen);
          continue;
        }
      }

      if (stderrLen < 0 && stdoutLen < 0)
        return;

      stderrLen = errorStream.read(buffer, 0, buffer.length);
      if (stderrLen > 0) {
        error.write(buffer, 0, stderrLen);
        continue;
      }

      stdoutLen = inputStream.read(buffer, 0, buffer.length);
      if (stdoutLen > 0) {
        error.write(buffer, 0, stdoutLen);
        continue;
      }
    } while (! _isDead && (stderrLen >= 0 || stdoutLen >= 0));
  }

  /**
   * This callback should only occur if the compiler freezes.  In that
   * case we immediately kill the process.
   *
   * @param alarm the alarm we've been waiting for.
   */
  public void handleAlarm(Alarm alarm)
  {
    _isDead = true;
    abort();
  }

  /**
   * Aborts the compilation.
   */
  public void abort()
  {
    if (_inputStream != null) {
      try {
        _inputStream.close();
      } catch (Throwable e) {
      }
    }
      
    if (_errorStream != null) {
      try {
        _errorStream.close();
      } catch (Throwable e) {
      }
    }

    if (_process != null) {
      try {
        _process.destroy();
      } catch (Throwable e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }
  }

  /**
   * Spawn the process to compile the file.
   *
   * @param argList compiler arguments.
   * @param chdir if true, change to the compilation directory .
   * @return a Java Process representing the compiler.
   */
  private Process executeCompiler(ArrayList<String> argList,
                                  ArrayList<String> envList,
                                  boolean chdir)
  throws IOException
  {
    String []args;

    // For unix, we can use sh to change the directory to get
    // automatic compilation of aux files.
    // Disabled because it's not needed with the source path?
    if (chdir) {
      CharBuffer cb = new CharBuffer();
      cb.append("cd ");
      cb.append(_compiler.getSourceDirName());
      cb.append(";");
      for (int i = 0; i < argList.size(); i++) {
        cb.append(" ");
        cb.append(argList.get(i));
      }
      args = new String[3];
      args[0] = "/bin/sh";
      args[1] = "-c";
      args[2] = cb.toString();
    }
    else {
      args = new String[argList.size()];
      argList.toArray(args);
    }

    String []envp = new String[envList.size()];
    envList.toArray(envp);

    if (log.isLoggable(Level.FINE)) {
      CharBuffer cb = CharBuffer.allocate();
      
      for (int i = 0; i < args.length; i++) {
        if (i != 0)
          cb.append(" ");
        cb.append(args[i]);
      }
      log.fine(cb.close());
    }
    
    Runtime runtime = Runtime.getRuntime();

    try {
      return runtime.exec(args);
    } catch (Exception e) {
      throw new JavaCompileException(L.l("Can't execute the compiler `{0}'.  This usually means that the compiler is not in the operating system's PATH or the compiler is incorrectly specified in the configuration.  You may need to add the full path to <java compiler='{0}'/>.\n\n{1}", args[0], String.valueOf(e)));
    }
  }

  /**
   * Converts any relative classpath references to the full path.
   */
  String normalizeClassPath(String classPath, boolean generateRelative)
  {
    char sep = CauchoUtil.getPathSeparatorChar();
    int head = 0;
    int tail = 0;

    CharBuffer cb = CharBuffer.allocate();

    while (head < classPath.length()) {
      tail = classPath.indexOf(sep, head);
      if (tail < 0)
        tail = classPath.length();

      if (tail > head) {
        String segment = classPath.substring(head, tail);

        segment = normalizePath(segment, generateRelative);

        if (segment != null) {
          if (cb.length() != 0)
            cb.append(sep);

          cb.append(segment);
        }
      }

      head = tail + 1;
    }

    return cb.close();
  }
  /**
   * Normalizes a path.
   */
  String normalizePath(String segment, boolean generateRelative)
  {
    if (_userPrefix == null) {
      PathImpl userPath = VfsOld.lookup(CauchoUtil.getUserDir());
      char sep = CauchoUtil.getFileSeparatorChar();
      _userPrefix = userPath.getNativePath();
      
      if (_userPrefix.length() == 0 ||
          _userPrefix.charAt(_userPrefix.length() - 1) != sep) {
        _userPrefix = _userPrefix + sep;
      }
    }
    
    PathImpl path = VfsOld.lookup(segment);
    
    String nativePath = path.getNativePath();

    if (! generateRelative)
      return nativePath;

    if (nativePath.startsWith(_userPrefix))
      nativePath = nativePath.substring(_userPrefix.length());

    if (nativePath.equals(""))
      return ".";
    else
      return nativePath;
  }

  public static class CompilerThread implements Runnable {
    private volatile boolean _isDone;
    
    public void run()
    {
      try {
      } finally {
        _isDone = true;

        synchronized (this) {
          notifyAll();
        }
      }
    }
  }
}
