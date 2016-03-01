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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.SendfileOutputStream;

/**
 * Reads from a file in a random-access fashion.
 */
abstract public class RandomAccessStream
  implements LockableStream
{
  private static final Logger log
    = Logger.getLogger(RandomAccessStream.class.getName());
  
  private final AtomicLong _useCount = new AtomicLong(1);
  private final AtomicBoolean _isClosed = new AtomicBoolean();
  
  /**
   * Returns the length.
   */
  abstract public long getLength()
    throws IOException;
  
  /**
   * Reads a block starting from the current file pointer.
   */
  abstract public int read(byte []buffer, int offset, int length)
    throws IOException;

  /**
   * Reads a block starting from the current file pointer.
   */
  abstract public int read(char []buffer, int offset, int length)
    throws IOException;

  /**
   * Reads a block from a given location.
   */
  abstract public int read(long fileOffset,
                           byte []buffer, int offset, int length)
    throws IOException;

  /**
   * Writes a block starting from the current file pointer.
   */
  abstract public void write(byte []buffer, int offset, int length)
    throws IOException;

  /**
   * Writes a block to a given location.
   */
  abstract public void write(long fileOffset,
                             byte []buffer, int offset, int length)
    throws IOException;

  /**
   * Writes data to the file.
   */
  public boolean writeToStream(SendfileOutputStream os, 
                               long offset, long length,
                               long []blockAddresses, long blockLength)
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Seeks to the given position in the file.
   */
  abstract public boolean seek(long position);

  /**
   * Returns an OutputStream for this stream.
   */
  abstract public OutputStream getOutputStream()
    throws IOException;

  /**
   * Returns an InputStream for this stream.
   */
  abstract public InputStream getInputStream()
    throws IOException;

  /**
   * Read a byte from the file, advancing the pointer.
   */
  abstract public int read()
    throws IOException;

  /**
   * Write a byte to the file, advancing the pointer.
   */
  abstract public void write(int b)
    throws IOException;

  /**
   * Returns the current position of the file pointer.
   */
  abstract public long getFilePointer()
    throws IOException;
  
  public boolean isMmap()
  {
    return false;
  }
  
  public long getMmapAddress() throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  public void fsync() throws IOException
  {
  }
  
  public final boolean isOpen()
  {
    return _useCount.get() > 0;
  }
  
  public final long getUseCount()
  {
    return _useCount.get();
  }

  public boolean allocate()
  {
    long count;
    
    do {
      count = _useCount.get();
      
      if (count <= 0) {
        return false;
      }
    } while (! _useCount.compareAndSet(count, count + 1));

    return true;
  }
  
  
  public void free()
  {
    long value = _useCount.decrementAndGet();

    if (value == 0) {
      try {
        closeImpl();
      } catch (IOException e) {
        log.log(Level.WARNING, e.toString(), e);
      }
    }
  }
  
  /**
   * Closes the stream.
   */
  public void close()
  {
    if (! _isClosed.getAndSet(true)) {
      free();
    }
  }
  
  /**
   * Closes the stream.
   */
  protected void closeImpl() throws IOException
  {
  }

  // Placeholder for LockableStream implementation

  public boolean lock(boolean shared, boolean block)
  {
    return true;
  }

  public boolean unlock()
  {
    return true;
  }
}
