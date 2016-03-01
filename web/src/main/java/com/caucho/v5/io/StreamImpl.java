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

package com.caucho.v5.io;

import java.io.IOException;

/**
 * This is the service provider's interface for a stream supported by
 * the VFS.
 */
public class StreamImpl
{
  //protected static NullPath _nullPath;
  
  private static final byte _newline[] = new byte[] {(byte) '\n'};
  
  //protected PathImpl _path;

  /**
   * Returns the stream's natural newline character.
   */
  public byte []getNewline()
  {
    return _newline;
  }

  /**
   * Returns true if the stream implements skip.
   */
  public boolean hasSkip()
  {
    return false;
  }

  /**
   * Skips a number of bytes, returning the bytes skipped.
   *
   * @param n the number of types to skip.
   *
   * @return the actual bytes skipped.
   */
  public long skip(long n)
    throws IOException
  {
    return 0;
  }

  /**
   * Returns true if this is a read stream.
   */
  public boolean canRead()
  {
    return false;
  }

  /**
   * Returns the read buffer.
   */
  public byte []getReadBuffer()
  {
    return null;
  }

  /**
   * Reads the next chunk from the stream.
   *
   * @param buffer byte array receiving the data.
   * @param offset starting offset into the array.
   * @param length number of bytes to read.
   *
   * @return the number of bytes read or -1 on end of file.
   */
  public int read(byte []buffer, int offset, int length) throws IOException
  {
    throw new UnsupportedOperationException(String.valueOf(this));
  }

  /**
   * Reads the next chunk from the stream in non-blocking mode.
   *
   * @param buffer byte array receiving the data.
   * @param offset starting offset into the array.
   * @param length number of bytes to read.
   *
   * @return the number of bytes read, -1 on end of file, or 0 on timeout.
   */
  public int readNonBlock(byte []buffer, int offset, int length)
    throws IOException
  {
    return readTimeout(buffer, offset, length, 0);
  }

  /**
   * Reads the next chunk from the stream in non-blocking mode.
   *
   * @param buffer byte array receiving the data.
   * @param offset starting offset into the array.
   * @param length number of bytes to read.
   *
   * @return the number of bytes read, -1 on end of file, or 0 on timeout.
   */
  public int readTimeout(byte []buffer, int offset, int length,
                         long timeout)
    throws IOException
  {
    return 0;
  }

  /**
   * Returns the number of bytes available without blocking.  Depending on
   * the stream, this may return less than the actual bytes, but will always
   * return a number > 0 if there is any data available.
   */
  public int getAvailable() throws IOException
  {
    return 0;
  }
  
  public boolean isReady()
  {
    try {
      return getAvailable() > 0;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  public boolean isEof() throws IOException
  {
    return false;
  }

  /**
   * Returns the current read position of the underlying file.
   */
  public long getReadPosition()
  {
    return -1;
  }

  /**
   * Returns true if this is a writable stream.
   */
  public boolean canWrite()
  {
    return false;
  }

  /**
   * Returns true if the buffer should be flushed on every newline.  This is
   * typically only true for error streams like stderr:.
   */
  public boolean getFlushOnNewline()
  {
    return false;
  }

  /**
   * Sets the write encoding.
   */
  public void setWriteEncoding(String encoding)
  {
  }

  /**
   * Writes a buffer to the underlying stream.
   *
   * @param buffer the byte array to write.
   * @param offset the offset into the byte array.
   * @param length the number of bytes to write.
   * @param isEnd true when the write is flushing a close.
   */
  public void write(byte []buffer, int offset, int length, boolean isEnd)
    throws IOException
  {
    throw new UnsupportedOperationException(String.valueOf(this));
  }

  /**
   * Writes a pair of buffer to the underlying stream.
   *
   * @param buf1 the byte array to write.
   * @param off1 the offset into the byte array.
   * @param len1 the number of bytes to write.
   * @param buf2 the byte array to write.
   * @param off2 the offset into the byte array.
   * @param len2 the number of bytes to write.
   * @param isEnd true when the write is flushing a close.
   */
  public boolean write(byte []buf1, int off1, int len1,
                       byte []buf2, int off2, int len2,
                       boolean isEnd)
    throws IOException
  {
    if (len1 == 0) {
      write(buf2, off2, len2, isEnd);

      return true;
    }
    else
      return false;
  }

  /**
   * Clears any buffered values in the write.
   */
  public void clearWrite()
  {
  }

  /**
   * Seeks based on the start.
   */
  public void seekStart(long offset)
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Seeks based on the end.
   */
  public void seekEnd(long offset)
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Flushes buffered writes.
   */
  public void flushBuffer() throws IOException
  {
  }

  /**
   * Flushes the write output.
   */
  public void flush() throws IOException
  {
  }

  /**
   * Flushes the write output, forcing to disk.
   */
  public void flushToDisk() throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Returns the Path associated with the stream.
   */
  /*
  public PathImpl getPath()
  {
    if (_path != null)
      return _path;

    if (_nullPath == null)
      _nullPath = new NullPath("stream");

    return _nullPath;
  }
  */

  /**
   * Sets the Path associated with the stream.
   */
  /*
  public void setPath(PathImpl path)
  {
    _path = path;
  }
  */

  /**
   * Returns a stream attribute.
   *
   * @param name the attribute name.
   *
   * @return the attribute value.
   */
  /*
  public Object getAttribute(String name)
    throws IOException
  {
    return null;
  }
  */

  /**
   * Sets a stream attribute.
   *
   * @param name the attribute name.
   * @param value the attribute value.
   */
  /*
  public void setAttribute(String name, Object value)
    throws IOException
  {
  }
  */

  /**
   * Removes a stream attribute.
   *
   * @param name the attribute name.
   */
  /*
  public void removeAttribute(String name)
    throws IOException
  {
  }
  */

  /**
   * Returns an iterator of the attribute names.
   */
  /*
  @SuppressWarnings("unchecked")
  public Iterator<String> getAttributeNames()
    throws IOException
  {
    return NullIterator.create();
  }
  */
  
  //
  // mmap/sendfile
  //

  /**
   * Returns true if the stream supports mmap.
   */
  public boolean isMmapEnabled()
  {
    return false;
  }

  /**
   * Returns true if the stream supports mmap.
   */
  public boolean isSendfileEnabled()
  {
    return false;
  }
  

  /*
  public void writeMmap(long mmapAddress, long mmapOffset, int mmapLength)
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */
  
  public void writeMmap(long mmapAddress,
                        long []mmapBlocks,
                        long mmapOffset, 
                        long mmapLength)
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void writeSendfile(byte []buffer, int offset, int length,
                            byte []fileName, int nameLength,
                            long fileLength)
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Closes the write half of the stream.
   */
  public void closeWrite() throws IOException
  {
    close();
  }
  
  /**
   * Returns true if the stream is closed.
   */
  public boolean isClosed()
  {
    return false;
  }

  /**
   * Closes the stream.
   */
  public void close() throws IOException
  {
  }
}
