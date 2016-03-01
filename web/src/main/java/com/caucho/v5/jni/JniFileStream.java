/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
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

package com.caucho.v5.jni;

import java.io.IOException;

import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.jni.JniUtil.JniLoad;

/**
 * Stream using with JNI.
 */
public class JniFileStream extends StreamImpl
    //implements LockableStream
{
  private static final JniTroubleshoot _jniTroubleshoot;
  
  private int _fd;
  private long _pos;

  private boolean _canRead;
  private boolean _canWrite;

  /**
   * Create a new JniStream based on the java.io.* stream.
   */
  public JniFileStream(int fd, boolean canRead, boolean canWrite)
  {
    _jniTroubleshoot.checkIsValid();

    init(fd, canRead, canWrite);
  }

  void init(int fd, boolean canRead, boolean canWrite)
  {
    _fd = fd;
    _pos = 0;

    _canRead = canRead;
    _canWrite = canWrite;
  }

  public static boolean isEnabled()
  {
    return _jniTroubleshoot.isEnabled();
  }
  
  public static Throwable getDisableCause()
  {
    return _jniTroubleshoot.getCause();
  }
  
  static int openFileDescriptorRead(byte []name, int length)
  {
    return nativeOpenRead(name, length);
  }
  
  static void closeFileDescriptor(int fd)
    throws IOException
  {
    nativeClose(fd);
  }

  public static JniFileStream openRead(byte []name, int length)
  {
    int fd = nativeOpenRead(name, length);

    if (fd >= 0)
      return new JniFileStream(fd, true, false);
    else
      return null;
  }

  public static JniFileStream openWrite(byte []name,
                                        int length,
                                        boolean isAppend)
  {
    int fd = nativeOpenWrite(name, length, isAppend);
    
    if (fd >= 0)
      return new JniFileStream(fd, false, true);
    else
      return null;
  }

  @Override
  public boolean canRead()
  {
    return _canRead && _fd >= 0;
  }

  @Override
  public boolean hasSkip()
  {
    return _fd >= 0;
  }

  @Override
  public long skip(long length)
    throws IOException
  {
    long pos = nativeSkip(_fd, length);

    if (pos < 0)
      return -1;
    else {
      _pos = pos;
      return length;
    }
  }

  /**
   * Reads data from the file.
   */
  @Override
  public int read(byte []buf, int offset, int length)
    throws IOException
  {
    if (buf == null)
      throw new NullPointerException();
    else if (offset < 0 || buf.length < offset + length)
      throw new ArrayIndexOutOfBoundsException();

    int result = nativeRead(_fd, buf, offset, length);

    if (result > 0)
      _pos += result;

    return result;
  }

  // XXX: needs update
  @Override
  public int getAvailable() throws IOException
  {
    throw new UnsupportedOperationException();
    /*
    if (_fd < 0) {
      return -1;
    }
    else if (getPath() instanceof FilesystemPath) {
      long length = getPath().length();
      
      return (int) (length - _pos);
    }
    else
      return nativeAvailable(_fd);
      */
  }

  /**
   * Returns true if this is a writeable stream.
   */
  @Override
  public boolean canWrite()
  {
    return _canWrite && _fd >= 0;
  }

  /**
   * Writes data to the file.
   */
  @Override
  public void write(byte []buf, int offset, int length, boolean isEnd)
    throws IOException
  {
    if (buf == null)
      throw new NullPointerException();
    else if (offset < 0 || buf.length < offset + length)
      throw new ArrayIndexOutOfBoundsException();

    nativeWrite(_fd, buf, offset, length);
  }

  @Override
  public void seekStart(long offset)
    throws IOException
  {
    nativeSeekStart(_fd, offset);
  }

  @Override
  public void seekEnd(long offset)
    throws IOException
  {
    nativeSeekEnd(_fd, offset);
  }

  @Override
  public void flush()
    throws IOException
  {
  }

  @Override
  public void flushToDisk()
    throws IOException
  {
    nativeFlushToDisk(_fd);
  }

  @Override
  public void close()
    throws IOException
  {
    int fd;

    synchronized (this) {
      fd = _fd;
      _fd = -1;
    }
    
    nativeClose(fd);
  }

  @Override
  protected void finalize()
    throws IOException
  {
    close();
  }

  /**
   * Native interface to read bytes from the input.
   */
  native int nativeRead(int fd, byte []buf, int offset, int length)
    throws IOException;

  /**
   * Native interface to read bytes from the input.
   */
  native int nativeAvailable(int fd)
    throws IOException;

  /**
   * Native to open a file for reading
   */
  private static native int nativeOpenRead(byte []name, int length);
  
  /**
   * Native to open a file for writing
   */
  private static native int nativeOpenWrite(byte []name, int length,
                                            boolean isAppend);

  /*
  private static native void nativeChown(byte []path, int length,
                                         String user, String group);
  */

  /**
   * Native interface to write bytes to the file
   */
  native int nativeWrite(int fd, byte []buf, int offset, int length)
    throws IOException;

  /**
   * Native interface to skip bytes from the input.
   */
  native long nativeSkip(int fd, long skip)
    throws IOException;

  /**
   * Native interface to force data to the disk
   */
  native int nativeFlushToDisk(int fd)
    throws IOException;

  /**
   * Native interface to read bytes from the input.
   */
  static native int nativeClose(int fd)
    throws IOException;

  /**
   * Native interface to seek from the beginning
   */
  native int nativeSeekStart(int fd, long offset)
    throws IOException;

  /**
   * Native interface to seek from the beginning
   */
  native int nativeSeekEnd(int fd, long offset)
    throws IOException;

  /**
   * Implement LockableStream as a no-op, but maintain
   * compatibility with FileWriteStream and FileReadStream
   * wrt returning false to indicate error case.
   */

  public boolean lock(boolean shared, boolean block)
  {
    if (shared && !_canRead) {
      // Invalid request for a shared "read" lock on a write only stream.

      return false;
    }

    if (!shared && !_canWrite) {
      // Invalid request for an exclusive "write" lock on a read only stream.

      return false;
    }

    return true;
  }

  public boolean unlock()
  {
    return true;
  }

  /**
   * Returns the debug name for the stream.
   */
  @Override
  public String toString()
  {
    //return "JniFileStream[" + getPath().getNativePath() + "]";
    return "JniFileStream[]"; //  + getPath().getNativePath() + "]";
  }

  static {
    _jniTroubleshoot
    = JniUtil.load(JniFileStream.class,
                   new JniLoad() { 
                     public void load(String path) { System.load(path); }},
                   "baratine");
  }
}

