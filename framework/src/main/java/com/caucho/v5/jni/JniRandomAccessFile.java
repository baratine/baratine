/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.jni;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.caucho.v5.jni.JniUtil.JniLoad;
import com.caucho.v5.vfs.PathImpl;
import com.caucho.v5.vfs.RandomAccessStream;

/**
 * Stream using with JNI.
 */
public class JniRandomAccessFile extends RandomAccessStream
{
  private static final JniTroubleshoot _jniTroubleshoot;

  private int _fd = -1;
  private PathImpl _path;

  /**
   * Create a new JniStream based on the java.io.* stream.
   */
  private JniRandomAccessFile(int fd, PathImpl path)
  {
    _jniTroubleshoot.checkIsValid();

    _fd = fd;
    _path = path;
  }

  public static boolean isEnabled()
  {
    return _jniTroubleshoot.isEnabled();
  }

  public static JniRandomAccessFile open(PathImpl path,
                                         byte []name,
                                         int length)
  {
    if (! isEnabled())
      return null;

    int fd = nativeOpen(name, length);

    if (fd >= 0)
      return new JniRandomAccessFile(fd, path);
    else
      return null;
  }
  
  /**
   * Returns the length.
   */
  @Override
  public long getLength()
    throws IOException
  {
    return nativeGetLength(_fd);
  }

  /**
   * Reads data from the file.
   */
  public int read(long pos, byte []buf, int offset, int length)
    throws IOException
  {
    if (buf == null)
      throw new NullPointerException();
    else if (offset < 0 || buf.length < offset + length)
      throw new ArrayIndexOutOfBoundsException();

    return nativeRead(_fd, pos, buf, offset, length);
  }

  /**
   * Writes data to the file.
   */
  public void write(long pos, byte []buf, int offset, int length)
    throws IOException
  {
    if (buf == null)
      throw new NullPointerException();
    else if (offset < 0 || buf.length < offset + length)
      throw new ArrayIndexOutOfBoundsException();

    nativeWrite(_fd, pos, buf, offset, length);
  }

  public void flushToDisk()
    throws IOException
  {
    nativeFlushToDisk(_fd);
  }

  /**
   * Reads data from the file.
   */
  public int read(byte []buf, int offset, int length)
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Reads data from the file.
   */
  public int read(char []buf, int offset, int length)
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Writes data to the file.
   */
  public void write(byte []buf, int offset, int length)
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Seeks to the given position in the file.
   */
  public boolean seek(long position)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Returns an OutputStream for this stream.
   */
  public OutputStream getOutputStream()
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Returns an InputStream for this stream.
   */
  public InputStream getInputStream()
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Read a byte from the file, advancing the pointer.
   */
  public int read()
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Write a byte to the file, advancing the pointer.
   */
  public void write(int b)
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }


  /**
   * Returns the current position of the file pointer.
   */
  public long getFilePointer()
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public void closeImpl()
    throws IOException
  {
    int fd;

    synchronized (this) {
      fd = _fd;
      _fd = -1;
    }

    if (fd >= 0)
      nativeClose(fd);
  }

  protected void finalize()
    throws IOException
  {
    close();
  }

  /**
   * Native to open a random access file
   */
  private static native int nativeOpen(byte []name, int length);
  
  /**
   * Returns the file length.
   */
  native long nativeGetLength(int fd);

  /**
   * Native interface to read bytes from the input.
   */
  native int nativeRead(int fd, long pos, byte []buf, int offset, int length)
    throws IOException;

  /**
   * Native interface to write bytes to the file
   */
  native int nativeWrite(int fd, long pos, byte []buf, int offset, int length)
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
   * Returns the debug name for the stream.
   */
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _path + "]";
  }

  static {
    _jniTroubleshoot
    = JniUtil.load(JniRandomAccessFile.class,
                   new JniLoad() { 
                     public void load(String path) { System.load(path); }},
                   "baratine");
  }
}

