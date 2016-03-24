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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.io.WriteBuffer;
import com.caucho.v5.loader.EnvironmentLocal;
import com.caucho.v5.util.CurrentTime;

/**
 * A stream that varies depending on the environment class loader.
 */
public class EnvironmentStream extends StreamImpl {
  // original stdout stream
  private final static PrintStream _origSystemOutWriter;
  // original stderr stream
  private final static PrintStream _origSystemErrWriter;
  
  // original stdout stream
  private final static WriteBuffer _origSystemOut;
  // original stderr stream
  private final static WriteBuffer _origSystemErr;
  
  // static stdout stream
  private static PrintStream _systemOut;
  // static stderr stream
  private static PrintStream _systemErr;
  
  // static stdout stream
  private static EnvironmentStream _stdoutStream;
  // static stderr stream
  private static EnvironmentStream _stderrStream;
  
  // context variable storing the per-environment stream.
  private EnvironmentLocal<OutputStream> _environmentStream;

  /**
   * Create the environment stream.
   *
   * @param envVariable the variable for the underlying stream
   * @param defaultStream the stream used if outside an environment
   */
  public EnvironmentStream(String envVariable, OutputStream defaultStream)
  {
    _environmentStream = new EnvironmentLocal<>(envVariable);
    _environmentStream.setGlobal(defaultStream);
  }

  /**
   * Create the environment stream.
   *
   * @param defaultStream the stream used if outside an environment
   */
  public EnvironmentStream(OutputStream defaultStream)
  {
    _environmentStream = new EnvironmentLocal<>();
    _environmentStream.setGlobal(defaultStream);
  }

  /**
   * Returns the context stream's variable.
   */
  public String getVariable()
  {
    return _environmentStream.getVariable();
  }

  /**
   * Returns the global stream
   */
  public OutputStream getGlobalStream()
  {
    return (OutputStream) _environmentStream.getGlobal();
  }

  /**
   * Returns the context stream's variable.
   */
  public Object setGlobalStream(OutputStream defaultStream)
  {
    return _environmentStream.setGlobal(defaultStream);
  }

  /**
   * Returns the global stream
   */
  public OutputStream getStream()
  {
    return (OutputStream) _environmentStream.get();
  }

  /**
   * Returns the context stream's variable.
   */
  public Object setStream(OutputStream os)
  {
    return _environmentStream.set(os);
  }

  /**
   * True if the stream can write
   */
  @Override
  public boolean canWrite()
  {
    OutputStream stream = getStream();

    return stream != null;
  }

  /**
   * Write data to the stream.
   */
  @Override
  public void write(byte []buf, int offset, int length, boolean isEnd)
    throws IOException
  {
    OutputStream stream = getStream();

    if (stream == null) {
      return;
    }

    synchronized (stream) {
      stream.write(buf, offset, length);
      
      if (isEnd) {
        stream.flush();
      }
    }
  }

  /**
   * Flush data to the stream.
   */
  @Override
  public void flush()
    throws IOException
  {
    OutputStream stream = getStream();

    if (stream == null) {
      return;
    }

    synchronized (stream) {
      stream.flush();
    }
  }

  /**
   * Flush data to the stream.
   */
  @Override
  public void close()
    throws IOException
  {
    OutputStream stream = getStream();

    if (stream == null) {
      return;
    }

    synchronized (stream) {
      stream.flush();
    }
  }

  /**
   * Sets the backing stream for System.out
   */
  public synchronized static void setStdout(OutputStream os)
  {
    if (_stdoutStream == null) {
      initStdout();
    }

    if (os == _systemErr || os == _systemOut) {
      return;
    }

    if (os instanceof WriteBuffer) {
      WriteBuffer out = (WriteBuffer) os;

      /*
      if (out.getSource() == StdoutStream.create()
          || out.getSource() == StderrStream.create()) {
        return;
      }
      */
    }
    
    _stdoutStream.setStream(os);
  }

  /**
   * Returns the environment stream for System.out
   */
  public static EnvironmentStream getStdout()
  {
    return _stdoutStream;
  }

  /**
   * Returns the original System.out writer
   */
  public static WriteBuffer getOriginalSystemOutStream()
  {
    return _origSystemOut;
  }

  /**
   * Returns the original System.out writer
   */
  public static PrintStream getOriginalSystemOut()
  {
    return _origSystemOutWriter;
  }

  /**
   * Sets path as the backing stream for System.err
   */
  public static synchronized void setStderr(OutputStream os)
  {
    if (_stderrStream == null) {
      initStderr();
    }

    if (os == _systemErr || os == _systemOut) {
      return;
    }

    if (os instanceof WriteBuffer) {
      WriteBuffer out = (WriteBuffer) os;

      /*
      if (out.getSource() == StdoutStream.create()
          || out.getSource() == StderrStream.create()) {
        return;
      }
      */
    }

    _stderrStream.setStream(os);
  }
  
  public static synchronized void initStdout()
  {
    if (_stdoutStream != null) {
      return;
    }
    
    /*
    StreamImpl stdoutRotate = RotateStream.getStdoutStream();
    OutputStream systemOut = new StreamImplOutputStream(stdoutRotate);
      
    _stdoutStream = new EnvironmentStream("caucho.stdout.stream",
                                          systemOut);
    WriteBuffer out = new WriteBuffer(_stdoutStream);
    out.setDisableClose(true);
    _systemOut = new PrintStream(out, true);
    System.setOut(_systemOut);
    */
  }
  
  public static synchronized void initStderr()
  {
    if (_stderrStream != null) {
      return;
    }
    
    /*
    StreamImpl stderrRotate = RotateStream.getStderrStream();
    OutputStream systemErr = new StreamImplOutputStream(stderrRotate);
    
    _stderrStream = new EnvironmentStream("caucho.stderr.stream",
                                          systemErr);
      
    WriteBuffer err = new WriteBuffer(_stderrStream);
    err.setDisableClose(true);
    _systemErr = new PrintStream(err, true);
    System.setErr(_systemErr);
    */
  }

  /**
   * Returns the environment stream for System.err
   */
  public static EnvironmentStream getStderr()
  {
    return _stderrStream;
  }

  /**
   * Returns the original System.out writer
   */
  public static WriteBuffer getOriginalSystemErrStream()
  {
    return _origSystemErr;
  }

  /**
   * Returns the original System.out writer
   */
  public static PrintStream getOriginalSystemErr()
  {
    return _origSystemErrWriter;
  }

  /**
   * Logs a message to the original stderr in cases where java.util.logging
   * is dangerous, e.g. in the logging code itself.
   */
  public static void logStderr(String msg, Throwable e)
  {
    try {
      long now = CurrentTime.currentTime();
    
      //msg = QDate.formatLocal(now, "[%Y-%m-%d %H:%M:%S] ") + msg;

      _origSystemErr.println(msg);

      //e.printStackTrace(_origSystemErr.getPrintWriter());

      _origSystemErr.flush();
    } catch (Throwable e1) {
    }
  }

  /**
   * Logs a message to the original stderr in cases where java.util.logging
   * is dangerous, e.g. in the logging code itself.
   */
  public static void logStderr(String msg)
  {
    try {
      long now = CurrentTime.currentTime();
    
      //msg = QDate.formatLocal(now, "[%Y-%m-%d %H:%M:%S] ") + msg;

      _origSystemErr.println(msg);
    } catch (Throwable e1) {
    }
  }

  static {
    _origSystemOutWriter = System.out;
    
    _origSystemOut = new WriteBuffer(System.out);
    _origSystemOut.setFlushOnNewline(true);
    
    _origSystemErrWriter = System.err;
    _origSystemErr = new WriteBuffer(System.err);
    _origSystemErr.setFlushOnNewline(true);
  }
}
