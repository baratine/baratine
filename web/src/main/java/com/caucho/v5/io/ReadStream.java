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
import java.io.InputStream;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.util.CurrentTime;

/**
 * A fast bufferered input stream supporting both character
 * and byte data.  The underlying stream sources are provided by StreamImpl
 * classes, so all streams have the same API regardless of the underlying
 * implementation.
 *
 * <p>Dynamic streams, like tcp and http
 * will properly flush writes before reading input.  And random access
 * streams, like RandomAccessFile, can use the same API as normal streams.
 *
 * <p>Most applications will use the Path routines to create their own streams.
 * Specialized applications, like servers, need the capability of recycling
 * streams.
 */
public final class ReadStream extends InputStream
{
  private static final Logger log
    = Logger.getLogger(ReadStream.class.getName());
  
  public static int ZERO_COPY_SIZE = 1024;
  public static final int READ_TIMEOUT = -4;

  private TempBufferData _tempRead;
  private byte []_readBuffer;
  private int _readOffset;
  private int _readLength;

  private StreamImpl _source;
  private long _position;
  
  private boolean _isReuseBuffer;

  private boolean _isEnableReadTime;
  private long _readTime;

  /**
   * Creates a stream and initializes with a specified source.
   *
   * @param source Underlying source for the stream.
   * @param sibling Sibling write stream.
   */
  public ReadStream()
  {
    _tempRead = TempBuffers.allocate();
    _readBuffer = _tempRead.buffer();
  }

  /**
   * Creates a stream and initializes with a specified source.
   *
   * @param source Underlying source for the stream.
   * @param sibling Sibling write stream.
   */
  public ReadStream(InputStream is)
  {
    this();
    
    init(new VfsStream(is));
  }

  /**
   * Creates a stream and initializes with a specified source.
   *
   * @param source Underlying source for the stream.
   * @param sibling Sibling write stream.
   */
  public ReadStream(StreamImpl source)
  {
    this();
    
    _readTime = 0;
    _source = source;

    init(source);
  }
  
  public void init(StreamImpl source)
  {
    Objects.requireNonNull(source);
    Objects.requireNonNull(_tempRead);
    
    if (! source.canRead()) {
      throw new IllegalArgumentException();
    }
    
    _source = source;
    _readOffset = 0;
    _readLength = 0;
  }
  
  public void reuseBuffer(boolean isReuse)
  {
    _isReuseBuffer = isReuse;
  }

  /**
   * Returns the underlying source for the stream.
   *
   * @return the source
   */
  public StreamImpl getSource()
  {
    return _source;
  }

  public byte []buffer()
  {
    return _readBuffer;
  }

  public int offset()
  {
    return _readOffset;
  }

  public void offset(int offset)
  {
    if (offset < 0) {
      throw new IllegalStateException("illegal offset=" + offset);
    }

    _readOffset = offset;
  }

  public int length()
  {
    return _readLength;
  }

  public void length(int length)
  {
    if (length< 0 || _readBuffer.length < length)
      throw new IllegalStateException("illegal length=" + length);

    _readLength = length;
  }

  /**
   * Returns the read position.
   */
  public long position()
  {
    return _position - (_readLength - _readOffset);
  }

  /**
   * Returns the last read-time.
   */
  public long getReadTime()
  {
    if (! _isEnableReadTime)
      throw new UnsupportedOperationException("last read-time is disabled");

    return _readTime;
  }

  /**
   * Returns the last read-time.
   */
  public void clearReadTime()
  {
    _readTime = 0;
  }

  /**
   * Enables setting the read time on every reads.
   */
  public void setEnableReadTime(boolean isEnable)
  {
    _isEnableReadTime = isEnable;
  }

  /**
   * Sets the current read position.
   */
  public boolean position(long pos)
    throws IOException
  {
    if (pos < 0) {
      // Return error on seek to negative stream position

      return false;
    }
    
    long delta = pos - position();
    
    long newOffset = _readOffset + delta;
    
    if (newOffset >= 0 && newOffset < _readLength) {
      _readOffset = (int) newOffset;
      
      return true;
    }
    
    if (delta < 0) {
      // Seek backwards in the stream

      _position = pos;
      _readLength = _readOffset = 0;

      if (_source != null) {
        _source.seekStart(pos);

        return true;
      }
      else {
        return false;
      }
    }
    else {
      // Seek forward in the stream, skip any buffered bytes

      long n = pos - position();

      return (skip(n) == n);
    }
  }

  /**
   * Clears the position for statistics cases like a socket stream.
   */
  public void clearPosition()
  {
    _position = (_readLength - _readOffset);
  }

  public int availableBuffer()
  {
    return _readLength - _readOffset;
  }

  /**
   * Returns an estimate of the available bytes.  If a read would not block,
   * it will always return greater than 0.
   */
  @Override
  public int available() throws IOException
  {
    if (_readOffset < _readLength) {
      return _readLength - _readOffset;
    }

    StreamImpl source = _source;

    if (source != null) {
      return source.getAvailable();
    }
    else {
      return -1;
    }
  }

  public boolean isReady()
  {
    if (_readOffset < _readLength) {
      return true;
    }
    
    StreamImpl source = _source;

    if (source != null) {
      return source.isReady();
    }
    else {
      return false;
    }
  }

  /**
   * Returns the next byte or -1 if at the end of file.
   */
  @Override
  public final int read() throws IOException
  {
    if (_readLength <= _readOffset) {
      if (! readBuffer()) {
        return -1;
      }
    }

    return _readBuffer[_readOffset++] & 0xff;
  }

  /**
   * Unreads the last byte.
   */
  public final void unread()
  {
    if (_readOffset <= 0)
      throw new RuntimeException();

    _readOffset--;
  }

  /**
   * Waits for data to be available.
   */
  public final boolean waitForRead() throws IOException
  {
    if (_readLength <= _readOffset) {
      if (! readBuffer()) {
        return false;
      }
    }

    return true;
  }

  /**
   * Skips the next <code>n</code> bytes.
   *
   * @param n bytes to skip.
   *
   * @return number of bytes skipped.
   */
  @Override
  public long skip(long n)
    throws IOException
  {
    if (n <= 0) {
      return n;
    }
    
    long skipLen = 0;
    
    while (n > 0) {
      long sublen = Math.min(n, _readLength - _readOffset);

      if (sublen > 0) {
        _readOffset += sublen;

        skipLen += sublen;
        n -= sublen;
      }
      else {
        if (_source.hasSkip()) {
          _readOffset = _readLength = 0;
          
          long sourceSkipped = _source.skip(n);

          if (sourceSkipped <= 0) {
            return skipLen;
          }
          else {
            _position += sourceSkipped;
            skipLen += sourceSkipped;
            
            n -= sourceSkipped;
          }
        }
        else if (! readBuffer()) {
          return skipLen;
        }
      }
    }
    
    return skipLen;
  }

  /**
   * Reads into a byte array.  <code>read</code> may return less than
   * the maximum bytes even if more bytes are available to read.
   *
   * @param buf byte array
   * @param offset offset into the byte array to start reading
   * @param length maximum byte allowed to read.
   *
   * @return number of bytes read or -1 on end of file.
   */
  @Override
  public final int read(byte []buf, int offset, int length)
    throws IOException
  {
    int readOffset = _readOffset;
    int readLength = _readLength;

    if (readLength <= readOffset) {
      if (ZERO_COPY_SIZE <= length) {
        int len = _source.read(buf, offset, length);
        
        if (len > 0) {
          _position += len;

          if (_isEnableReadTime) {
            _readTime = CurrentTime.currentTime();
          }
        }

        return len;
      }

      if (! readBuffer()) {
        return -1;
      }

      readOffset = _readOffset;
      readLength = _readLength;
    }

    int sublen = Math.min(length, readLength - readOffset);

    System.arraycopy(_readBuffer, readOffset, buf, offset, sublen);

    _readOffset = readOffset + sublen;

    return sublen;
  }

  /**
   * Reads into a byte array.  <code>readAll</code> will always read
   * <code>length</code> bytes, blocking if necessary, until the end of
   * file is reached.
   *
   * @param buf byte array
   * @param offset offset into the byte array to start reading
   * @param length maximum byte allowed to read.
   *
   * @return number of bytes read or -1 on end of file.
   */
  public int readAll(byte []buf, int offset, int length) throws IOException
  {
    int readOffset = _readOffset;
    int readLength = _readLength;

    int i = 0;

    while (true) {
      int sublen = Math.min(length - i, readLength - readOffset);
      
      System.arraycopy(_readBuffer, readOffset, buf, offset + i, sublen);
      
      i += sublen;
      
      if (length <= i) {
        _readOffset = readOffset + sublen;
        
        return i;
      }
      else {
        if (! readBuffer()) {
          return i == 0 ? -1 : i;
        }

        readOffset = _readOffset;
        readLength = _readLength;
      }
    }
  }

  //
  // data api
  //

  /**
   * Reads a 4-byte network encoded integer
   */
  public int readInt()
    throws IOException
  {
    if (_readOffset + 4 < _readLength) {
      return (((_readBuffer[_readOffset++] & 0xff) << 24)
              + ((_readBuffer[_readOffset++] & 0xff) << 16)
              + ((_readBuffer[_readOffset++] & 0xff) << 8)
              + ((_readBuffer[_readOffset++] & 0xff)));
    }
    else {
      return ((read() << 24)
              + (read() << 16)
              + (read() << 8)
              + (read()));
    }
  }

  /**
   * Reads an 8-byte network encoded long
   */
  public long readLong()
    throws IOException
  {
    return (((long) read() << 56)
            + ((long) read() << 48)
            + ((long) read() << 40)
            + ((long) read() << 32)
            + ((long) read() << 24)
            + ((long) read() << 16)
            + ((long) read() << 8)
            + ((long) read()));
  }

  /**
   * Fills the buffer from the underlying stream.
   */
  public int fillBuffer()
    throws IOException
  {
    if (readBuffer()) {
      return _readLength;
    }
    else {
      return -1;
    }
  }

  /**
   * Fills the buffer with a non-blocking read.
   */
  public boolean readNonBlock()
    throws IOException
  {
    if (_readOffset < _readLength) {
      return true;
    }

    if (_readBuffer == null) {
      _readOffset = 0;
      _readLength = 0;
      return false;
    }

    int readLength = _source.readNonBlock(_readBuffer, 0, _readBuffer.length);

    _readOffset = 0;
    // Setting to 0 is needed to avoid int to long conversion errors with AIX
    if (readLength > 0) {
      _readLength = readLength;
      _position += readLength;

      if (_isEnableReadTime)
        _readTime = CurrentTime.currentTime();

      return true;
    }
    else {
      _readLength = 0;
      return false;
    }
  }

  /**
   * Fills the buffer with a non-blocking read.
   *
   * @param timeout the timeout in milliseconds for the next data

   * @return true on data or end of file, false on timeout
   */
  public int fillWithTimeout(long timeout)
    throws IOException
  {
    if (_readOffset < _readLength) {
      return _readLength - _readOffset;
    }

    if (_readBuffer == null) {
      _readOffset = 0;
      _readLength = 0;
      return -1;
    }

    _readOffset = 0;
    StreamImpl source = _source;

    if (source == null) {
      // return true on end of file
      return -1;
    }

    int readLength
      = source.readTimeout(_readBuffer, 0, _readBuffer.length, timeout);

    if (readLength > 0) {
      _readLength = readLength;
      _position += readLength;

      if (_isEnableReadTime) {
        _readTime = CurrentTime.currentTime();
      }

      return readLength;
    }
    else if (readLength == READ_TIMEOUT) {
      // timeout
      _readLength = 0;
      return 0;
    }
    else {
      // return false on end of file
      _readLength = 0;
      return -1;
    }
  }

  /**
   * Fills the buffer with a timed read, testing for the end of file.
   * Used for cases like comet to test if the read stream has closed.
   *
   * @param timeout the timeout in milliseconds for the next data

   * @return true on data or timeout, false on end of file
   */
  public boolean fillIfLive(long timeout)
    throws IOException
  {
    StreamImpl source = _source;
    byte []readBuffer = _readBuffer;

    if (readBuffer == null || source == null) {
      _readOffset = 0;
      _readLength = 0;
      return false;
    }

    if (_readOffset > 0) {
      System.arraycopy(readBuffer, _readOffset, readBuffer, 0,
                       _readLength - _readOffset);
      _readLength -= _readOffset;
      _readOffset = 0;
    }

    if (_readLength == readBuffer.length)
      return true;

    int readLength
      = source.readTimeout(_readBuffer, _readLength,
                           _readBuffer.length - _readLength, timeout);

    if (readLength >= 0) {
      _readLength += readLength;
      _position += readLength;

      if (_isEnableReadTime)
        _readTime = CurrentTime.currentTime();
      return true;
    }
    else if (readLength == READ_TIMEOUT) {
      // timeout

      return true;
    }
    else {
      // return false on end of file

      return false;
    }
  }

  /**
   * Fills the read buffer, flushing the write buffer.
   *
   * @return false on end of file and true if there's more data.
   */
  private boolean readBuffer()
    throws IOException
  {
    if (_readBuffer == null || _source == null) {
      _readOffset = 0;
      _readLength = 0;
      return false;
    }

    int readLength = _source.read(_readBuffer, 0, _readBuffer.length);
    
    _readOffset = 0;
    // Setting to 0 is needed to avoid int to long conversion errors with AIX
    if (readLength > 0) {
      _readLength = readLength;
      _position += readLength;

      if (_isEnableReadTime)
        _readTime = CurrentTime.currentTime();
      return true;
    }
    else {
      _readLength = 0;
      return false;
    }
  }

  /**
   * Close the stream.
   */
  @Override
  public final void close()
  {
    try {
      TempBufferData tempBuffer = _tempRead;
      
      if (tempBuffer != null && ! _isReuseBuffer) {
        _tempRead = null;
        _readBuffer = null;

        tempBuffer.free();
      }

      if (_source != null) {
        StreamImpl s = _source;
        _source = null;
        s.close();
      }
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Returns a printable representation of the read stream.
   */
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _source + "]";
  }
}
