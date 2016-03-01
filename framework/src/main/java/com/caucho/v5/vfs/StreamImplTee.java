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
import java.util.Iterator;
import java.util.Objects;

import com.caucho.v5.io.StreamImpl;

/**
 * This is the service provider's interface for a stream supported by
 * the VFS.
 */
public class StreamImplTee extends StreamImpl
{
  private StreamImpl _delegate;
  private StreamImpl _logStream;
  
  public StreamImplTee(StreamImpl delegate,
                       StreamImpl logStream)
  {
    Objects.requireNonNull(delegate);
    Objects.requireNonNull(logStream);
    
    _delegate = delegate;
    _logStream = logStream;
  }
  
  public StreamImpl getDelegate()
  {
    return _delegate;
  }
  
  public StreamImpl getLogStream()
  {
    return _logStream;
  }

  /**
   * Returns the stream's natural newline character.
   */
  @Override
  public byte []getNewline()
  {
    return getDelegate().getNewline();
  }

  /**
   * Returns true if the stream implements skip.
   */
  @Override
  public boolean hasSkip()
  {
    return getDelegate().hasSkip();
  }

  /**
   * Skips a number of bytes, returning the bytes skipped.
   *
   * @param n the number of types to skip.
   *
   * @return the actual bytes skipped.
   */
  @Override
  public long skip(long n)
    throws IOException
  {
    return getDelegate().skip(n);
  }

  /**
   * Returns true if this is a read stream.
   */
  @Override
  public boolean canRead()
  {
    return getDelegate().canRead();
  }

  /**
   * Returns the read buffer.
   */
  @Override
  public byte []getReadBuffer()
  {
    return getDelegate().getReadBuffer();
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
  @Override
  public int read(byte []buffer, int offset, int length) throws IOException
  {
    int sublen = getDelegate().read(buffer, offset, length);
    
    if (sublen > 0) {
      getLogStream().write(buffer, offset, sublen, false);
    }
    
    return sublen;
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
  @Override
  public int readNonBlock(byte []buffer, int offset, int length)
    throws IOException
  {
    int sublen = getDelegate().readTimeout(buffer, offset, length, 0);
    
    if (sublen > 0) {
      getLogStream().write(buffer, offset, sublen, false);
    }
    
    return sublen;
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
  @Override
  public int readTimeout(byte []buffer, int offset, int length,
                         long timeout)
    throws IOException
  {
    int sublen = getDelegate().readTimeout(buffer, offset, length, timeout);
    
    if (sublen > 0) {
      getLogStream().write(buffer, offset, sublen, false);
    }
    return sublen;
  }

  /**
   * Returns the number of bytes available without blocking.  Depending on
   * the stream, this may return less than the actual bytes, but will always
   * return a number > 0 if there is any data available.
   */
  @Override
  public int getAvailable() throws IOException
  {
    return getDelegate().getAvailable();
  }

  @Override
  public boolean isEof() throws IOException
  {
    return getDelegate().isEof();
  }

  /**
   * Returns the current read position of the underlying file.
   */
  @Override
  public long getReadPosition()
  {
    return getDelegate().getReadPosition();
  }

  /**
   * Returns true if this is a writable stream.
   */
  @Override
  public boolean canWrite()
  {
    return getDelegate().canWrite();
  }

  /**
   * Returns true if the buffer should be flushed on every newline.  This is
   * typically only true for error streams like stderr:.
   */
  @Override
  public boolean getFlushOnNewline()
  {
    return getDelegate().getFlushOnNewline();
  }

  /**
   * Sets the write encoding.
   */
  @Override
  public void setWriteEncoding(String encoding)
  {
    getDelegate().setWriteEncoding(encoding);
  }

  /**
   * Writes a buffer to the underlying stream.
   *
   * @param buffer the byte array to write.
   * @param offset the offset into the byte array.
   * @param length the number of bytes to write.
   * @param isEnd true when the write is flushing a close.
   */
  @Override
  public void write(byte []buffer, int offset, int length, boolean isEnd)
    throws IOException
  {
      getDelegate().write(buffer, offset, length, isEnd);
      
      getLogStream().write(buffer, offset, length, false);
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
  @Override
  public void seekStart(long offset)
    throws IOException
  {
    getDelegate().seekStart(offset);
  }

  /**
   * Seeks based on the end.
   */
  @Override
  public void seekEnd(long offset)
    throws IOException
  {
    getDelegate().seekEnd(offset);
  }

  /**
   * Flushes buffered writes.
   */
  @Override
  public void flushBuffer() throws IOException
  {
    getDelegate().flushBuffer();
    
    getLogStream().flushBuffer();
  }

  /**
   * Flushes the write output.
   */
  @Override
  public void flush() throws IOException
  {
    getDelegate().flush();
    
    getLogStream().flush();
  }

  /**
   * Flushes the write output, forcing to disk.
   */
  @Override
  public void flushToDisk() throws IOException
  {
    getDelegate().flushToDisk();
    
    getLogStream().flush();
  }

  /**
   * Returns the Path associated with the stream.
   */
  /*
  @Override
  public PathImpl getPath()
  {
    return getDelegate().getPath();
  }
  */

  /**
   * Sets the Path associated with the stream.
   */
  /*
  @Override
  public void setPath(PathImpl path)
  {
    getDelegate().setPath(path);
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
  @Override
  public Object getAttribute(String name)
    throws IOException
  {
    return getDelegate().getAttribute(name);
  }
  */

  /**
   * Sets a stream attribute.
   *
   * @param name the attribute name.
   * @param value the attribute value.
   */
  /*
  @Override
  public void setAttribute(String name, Object value)
    throws IOException
  {
    getDelegate().setAttribute(name, value);
  }
  */

  /**
   * Removes a stream attribute.
   *
   * @param name the attribute name.
   */
  /*
  @Override
  public void removeAttribute(String name)
    throws IOException
  {
    getDelegate().removeAttribute(name);
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
    return getDelegate().getAttributeNames();
  }
  */
  
  //
  // mmap/sendfile
  //

  /**
   * Returns true if the stream supports mmap.
   */
  @Override
  public boolean isMmapEnabled()
  {
    return getDelegate().isMmapEnabled();
  }

  /**
   * Returns true if the stream supports mmap.
   */
  @Override
  public boolean isSendfileEnabled()
  {
    return getDelegate().isSendfileEnabled();
  }

  @Override
  public void writeMmap(long mmapAddress,
                        long []mmapBlocks,
                        long mmapOffset, 
                        long mmapLength)
    throws IOException
  {
      getDelegate().writeMmap(mmapAddress, mmapBlocks, mmapOffset, mmapLength);
  }

  @Override
  public void writeSendfile(byte []buffer, int offset, int length,
                            byte []fileName, int nameLength,
                            long fileLength)
    throws IOException
  {
    getDelegate().writeSendfile(buffer, offset, length, 
                                fileName, nameLength, fileLength);
  }

  /**
   * Closes the write half of the stream.
   */
  @Override
  public void closeWrite() throws IOException
  {
    getDelegate().closeWrite();
    
    getLogStream().close();
  }
  
  /**
   * Returns true if the stream is closed.
   */
  @Override
  public boolean isClosed()
  {
    return getDelegate().isClosed();
  }

  /**
   * Closes the stream.
   */
  @Override
  public void close() throws IOException
  {
    getDelegate().close();
    
    getLogStream().close();
  }
}
