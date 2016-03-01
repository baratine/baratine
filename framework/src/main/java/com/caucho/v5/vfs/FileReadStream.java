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

package com.caucho.v5.vfs;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.StreamImpl;

/**
 * Stream encapsulating FileInputStream
 */
public class FileReadStream extends StreamImpl
    implements LockableStream
{
  private static final Logger log
    = Logger.getLogger(FileRandomAccessStream.class.getName());

  private FileInputStream _is;

  private FileLock _fileLock;
  private FileChannel _fileChannel;

  /**
   * Create a new FileReadStream.
   */
  public FileReadStream()
  {
  }
  
  /**
   * Create a new FileReadStream based on the java.io.* stream.
   *
   * @param is the underlying input stream.
   */
  public FileReadStream(FileInputStream is)
  {
    init(is);
  }
  
  /**
   * Create a new FileReadStream based on the java.io.* stream.
   *
   * @param is the underlying input stream.
   * @param path the associated Path.
   */
  public FileReadStream(FileInputStream is, PathImpl path)
  {
    init(is);
    //setPath(path);
  }

  /**
   * Initializes a VfsStream with an input/output stream pair.  Before a
   * read, the output will be flushed to avoid deadlocks.
   *
   * @param is the underlying InputStream.
   * @param os the underlying OutputStream.
   */
  public void init(FileInputStream is)
  {
    _is = is;
    //setPath(null);
  }

  /**
   * Returns true if there's an associated file.
   */
  public boolean hasSkip()
  {
    return _is != null;
  }

  /**
   * Skips bytes in the file.
   *
   * @param n the number of bytes to skip
   *
   * @return the actual bytes skipped.
   */
  public long skip(long n)
    throws IOException
  {
    if (_is != null)
      return _is.skip(n);
    else
      return -1;
  }

  /**
   * Seeks based on the start.
   */
  public void seekStart(long offset)
    throws IOException
  {
    if (_is != null)
      _is.getChannel().position(offset);
  }

  /**
   * Returns true if there's an associated file.
   */
  public boolean canRead()
  {
    return _is != null;
  }

  /**
   * Reads bytes from the file.
   *
   * @param buf a byte array receiving the data.
   * @param offset starting index to receive data.
   * @param length number of bytes to read.
   *
   * @return the number of bytes read or -1 on end of file.
   */
  public int read(byte []buf, int offset, int length) throws IOException
  {
    if (_is == null)
      return -1;

    int len = _is.read(buf, offset, length);

    return len;
  }

  /**
   * Returns the number of bytes available for reading.
   */
  public int getAvailable() throws IOException
  {
    if (_is == null)
      return -1;
    else {
      return _is.available();
    }
  }

  /**
   * Closes the underlying stream.
   */
  public void close() throws IOException
  {
    unlock();

    _fileChannel = null;

    InputStream is = _is;
    _is = null;
    
    if (is != null)
      is.close();
  }

  public boolean lock(boolean shared, boolean block)
  {
    unlock();

    if (!shared) {
      // Invalid request for an exclusive "write" lock on a read only stream.

      return false;
    }

    try {
      if (_fileChannel == null) {
        _fileChannel = _is.getChannel();
      }

      if (block)
        _fileLock = _fileChannel.lock(0, Long.MAX_VALUE, true);
      else
        _fileLock = _fileChannel.tryLock(0, Long.MAX_VALUE, true);

      return _fileLock != null;
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
      return false;
    }
  }

  public boolean unlock()
  {
    try {
      FileLock lock = _fileLock;
      _fileLock = null;

      if (lock != null) {
        lock.release();

        return true;
      }

      return false;
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
      return false;
    }
  }

}
