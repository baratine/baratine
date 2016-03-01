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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads from a file in a random-access fashion.
 */
public class FileRandomAccessStream extends RandomAccessStream
    implements LockableStream
{
  private static final Logger log
    = Logger.getLogger(FileRandomAccessStream.class.getName());

  private RandomAccessFile _file;
  private OutputStream _os;
  private InputStream _is;

  private FileLock _fileLock;
  private FileChannel _fileChannel;

  public FileRandomAccessStream(RandomAccessFile file)
  {
    _file = file;
  }

  public RandomAccessFile getRandomAccessFile()
  {
    return _file;
  }

  /**
   * Returns the length.
   */
  @Override
  public long getLength()
    throws IOException
  {
    return _file.length();
  }
  
  /**
   * Reads a block starting from the current file pointer.
   */
  public int read(byte []buffer, int offset, int length)
    throws IOException
  {
    return _file.read(buffer, offset, length);
  }

  /**
   * Reads a block starting from the current file pointer.
   */
  public int read(char []buffer, int offset, int length)
    throws IOException
  {
    byte[] bytes = new byte[length];

    int count = _file.read(bytes, 0, length);

    for (int i = 0; i < count; i++) {
      buffer[offset + i] = (char) bytes[i];

    }

    return count;
  }

  /**
   * Reads a block from a given location.
   */
  public int read(long fileOffset, byte []buffer, int offset, int length)
    throws IOException
  {
    _file.seek(fileOffset);
    
    return _file.read(buffer, offset, length);
  }

  /**
   * Writes a block starting from the current file pointer.
   */
  public void write(byte []buffer, int offset, int length)
    throws IOException
  {
    _file.write(buffer, offset, length);
  }

  /**
   * Writes a block from a given location.
   */
  public void write(long fileOffset, byte []buffer, int offset, int length)
    throws IOException
  {
    _file.seek(fileOffset);
    
    _file.write(buffer, offset, length);
  }

  /**
   * Seeks to the given position in the file.
   */
  public boolean seek(long position)
  {
    try {
      _file.seek(position);

      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Returns an OutputStream for this stream.
   */
  public OutputStream getOutputStream()
    throws IOException
  {
    if (_os == null)
      _os = new FileOutputStream(_file.getFD());

    return _os;
  }

  /**
   * Returns an InputStream for this stream.
   */
  public InputStream getInputStream()
    throws IOException
  {
    if (_is == null)
      _is = new FileInputStream(_file.getFD());

    return _is;
  }

  /**
   * Read a byte from the file, advancing the pointer.
   */
  public int read()
    throws IOException
  {
    return _file.read();
  }

  /**
   * Write a byte to the file, advancing the pointer.
   */
  public void write(int b)
    throws IOException
  {
    _file.write(b);
  }

  /**
   * Returns the current position of the file pointer.
   */
  public long getFilePointer()
    throws IOException
  {
    return _file.getFilePointer();
  }

  /**
   * Closes the stream.
   */
  @Override
  public void closeImpl() throws IOException
  {
    unlock();

    _fileChannel = null;

    RandomAccessFile file = _file;
    _file = null;

    if (file != null)
      file.close();
  }

  @Override
  public boolean lock(boolean shared, boolean block)
  {
    unlock();

    try {
      if (_fileChannel == null) {
        _fileChannel = _file.getChannel();
      }

      if (block)
        _fileLock = _fileChannel.lock(0, Long.MAX_VALUE, shared);
      else
        _fileLock = _fileChannel.tryLock(0, Long.MAX_VALUE, shared);

      return _fileLock != null;
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
      return false;
    }
  }

  @Override
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
 
  @Override
  public String toString()
  {
    return (getClass().getSimpleName() + "[" + _file
            + "," +System.identityHashCode(this) + "]");
  }
}
