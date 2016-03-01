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

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stream encapsulating FileOutputStream
 */
public class FileWriteStream extends VfsStreamOld
    implements LockableStream
{
  private FileOutputStream _os;

  private FileLock _fileLock;
  private FileChannel _fileChannel;

  private static final Logger log
    = Logger.getLogger(FileWriteStream.class.getName());

  /**
   * Create a new FileWriteStream based on the java.io.* stream.
   *
   * @param fos the underlying file output stream.
   */
  public FileWriteStream(FileOutputStream fos)
  {
    super(null, fos);
    _os = fos;
  }

  /**
   * Create a new FileWriteStream based on the java.io.* stream.
   *
   * @param fos the underlying file output stream.
   * @param path the associated Path.
   */
  public FileWriteStream(FileOutputStream fos, PathImpl path)
  {
    super(null, fos); // , path);
    _os = fos;
  }

  /**
   * Closes the underlying stream.
   */
  public void close() throws IOException
  {
    unlock();

    _fileChannel = null;

    super.close();
  }

  public boolean lock(boolean shared, boolean block)
  {
    unlock();

    if (shared) {
      // Invalid request for a shared "read" lock on a write only stream.

      return false;
    }

    try {
      if (_fileChannel == null) {
        _fileChannel = _os.getChannel();
      }

      if (block)
        _fileLock = _fileChannel.lock(0, Long.MAX_VALUE, false);
      else
        _fileLock = _fileChannel.tryLock(0, Long.MAX_VALUE, false);

      return _fileLock != null;
    } catch (OverlappingFileLockException e) {
      log.log(Level.FINE, e.toString(), e);
      return false;
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

  /**
   * Seeks based on the start.
   */
  public void seekStart(long pos)
    throws IOException
  {
    if (_fileChannel == null) {
      _fileChannel = _os.getChannel();
    }

    _fileChannel.position(pos);
  }

}
