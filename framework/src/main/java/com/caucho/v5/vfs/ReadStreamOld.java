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
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.io.i18n.Encoding;
import com.caucho.v5.util.CharBuffer;
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
public final class ReadStreamOld extends InputStream
    // implements LockableStream
{
  public static int ZERO_COPY_SIZE = 1024;
  public static final int READ_TIMEOUT = -4;

  private TempBuffer _tempRead;
  private byte []_readBuffer;
  private int _readOffset;
  private int _readLength;

  private StreamImpl _source;
  private long _position;

  private boolean _isEnableReadTime;
  private long _readTime;

  private Reader _readEncoding;
  private String _readEncodingName;
  private CharBuffer _cb;

  private boolean _disableClose;
  private boolean _isDisableCloseSource;
  private boolean _reuseBuffer;
  private Reader _reader;

  /**
   * Creates an uninitialized stream. Use <code>init</code> to initialize.
   */
  public ReadStreamOld()
  {
  }

  /**
   * Creates a stream and initializes with a specified source.
   *
   * @param source Underlying source for the stream.
   */
  public ReadStreamOld(StreamImpl source)
  {
    init(source);
  }

  /**
   * Creates a stream and initializes with a specified source.
   *
   * @param source Underlying source for the stream.
   * @param sibling Sibling write stream.
   */
  /*
  public ReadStream(StreamImpl source, WriteStream sibling)
  {
    init(source, sibling);
  }
  */

  /**
   * Initializes the stream with a given source.
   *
   * @param source Underlying source for the stream.
   * @param sibling Sibling write stream
   */
  public void init(StreamImpl source)
  {
    _disableClose = false;
    _isDisableCloseSource = false;
    _readTime = 0;

    if (_source != null && _source != source) {
      close();
    }

    if (source == null) {
      throw new IllegalArgumentException();
    }

    _source = source;

    if (source.canRead()) {
      if (_tempRead == null) {
        _tempRead = TempBuffer.create();
        _readBuffer = _tempRead.buffer();
      }
    }
    _readOffset = 0;
    _readLength = 0;

    _readEncoding = null;
    _readEncodingName = "ISO-8859-1";
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

  public void setSource(StreamImpl source)
  {
    _source = source;
  }

  public void setReuseBuffer(boolean reuse)
  {
    _reuseBuffer = reuse;
  }

  /**
   * Pushes a filter on the top of the stream stack.
   *
   * @param filter the filter to be added.
   */
  /*
  public void pushFilter(StreamFilter filter)
  {
    filter.init(_source);
    _source = filter;
  }
  */

  public byte []buffer()
  {
    return _readBuffer;
  }

  public int offset()
  {
    return _readOffset;
  }

  public void setOffset(int offset)
  {
    if (offset < 0)
      throw new IllegalStateException("illegal offset=" + offset);

    _readOffset = offset;
  }

  public int getLength()
  {
    return _readLength;
  }

  public void setLength(int length)
  {
    if (length< 0 || _readBuffer.length < length)
      throw new IllegalStateException("illegal length=" + length);

    _readLength = length;
  }

  /**
   * Returns the read position.
   */
  public long getPosition()
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
  public boolean setPosition(long pos)
    throws IOException
  {
    // Allow seeks to be reflected in the char Reader.
    if (_readEncoding != null) {
      _readEncoding = Encoding.getReadEncoding(this, _readEncodingName);
    }
    
    if (pos < 0) {
      // Return error on seek to negative stream position

      return false;
    }
    
    long delta = pos - getPosition();
    
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

      long n = pos - getPosition();

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

  /**
   * Returns true if the stream allows reading.
   */
  public boolean canRead()
  {
    return _source.canRead();
  }

  /**
   * Clears the read buffer.
   */
  public void clearRead()
  {
    _readOffset = 0;
    _readLength = 0;
  }

  /**
   * Returns an estimate of the available bytes.  If a read would not block,
   * it will always return greater than 0.
   */
  public int getAvailable() throws IOException
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
   * Returns true if data in the buffer is available.
   */
  public int availableBuffer() throws IOException
  {
    return _readLength - _readOffset;
  }

  /**
   * Compatibility with InputStream.
   */
  @Override
  public int available() throws IOException
  {
    return getAvailable();
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

          if (_isEnableReadTime)
            _readTime = CurrentTime.currentTime();
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

  /*
   * Reader methods
   */

  /**
   * Sets the current read encoding.  The encoding can either be a
   * Java encoding name or a mime encoding.
   *
   * @param encoding name of the read encoding
   */
  public void setEncoding(String encoding)
    throws UnsupportedEncodingException
  {
    String mimeName = Encoding.getMimeName(encoding);

    if (mimeName != null && mimeName.equals(_readEncodingName))
      return;

    _readEncoding = Encoding.getReadEncoding(this, encoding);
    _readEncodingName = mimeName;
  }

  /**
   * Returns the mime-encoding currently read.
   */
  public String getEncoding()
  {
    return _readEncodingName;
  }

  /**
   * Reads a character from the stream, returning -1 on end of file.
   */
  public final int readChar() throws IOException
  {
    if (_readEncoding != null) {
      int ch = _readEncoding.read();
      return ch;
    }

    if (_readLength <= _readOffset) {
      if (! readBuffer())
        return -1;
    }

    return _readBuffer[_readOffset++] & 0xff;
  }

  /**
   * Reads into a character buffer from the stream.  Like the byte
   * array version, read may return less characters even though more
   * characters are available.
   *
   * @param buf character buffer to fill
   * @param offset starting offset into the character buffer
   * @param length maximum number of characters to read
   * @return number of characters read or -1 on end of file.
   */
  public final int read(char []buf, int offset, int length)
    throws IOException
  {
    if (_readEncoding != null) {
      return _readEncoding.read(buf, offset, length);
    }

    byte []readBuffer = _readBuffer;
    if (readBuffer == null)
      return -1;

    int readOffset = _readOffset;
    int readLength = _readLength;

    int sublen = Math.min(length, readLength - readOffset);

    if (readLength <= readOffset) {
      if (! readBuffer()) {
        return -1;
      }

      readLength = _readLength;
      readOffset = _readOffset;

      sublen = Math.min(length, readLength - readOffset);
    }

    for (int i = sublen - 1; i >= 0; i--) {
      buf[offset + i] = (char) (readBuffer[readOffset + i] & 0xff);
    }

    _readOffset = readOffset + sublen;

    return sublen;
  }

  /**
   * Reads into a character buffer from the stream.  <code>length</code>
   * characters will always be read until the end of file is reached.
   *
   * @param buf character buffer to fill
   * @param offset starting offset into the character buffer
   * @param length maximum number of characters to read
   * @return number of characters read or -1 on end of file.
   */
  public int readAll(char []buf, int offset, int length) throws IOException
  {
    int readLength = 0;

    while (length > 0) {
      int sublen = read(buf, offset, length);

      if (sublen <= 0) {
        return readLength > 0 ? readLength : -1;
      }

      offset += sublen;
      readLength += sublen;
      length -= sublen;
    }

    return readLength;
  }

  /**
   * Reads characters from the stream, appending to the character buffer.
   *
   * @param buf character buffer to fill
   * @param length maximum number of characters to read
   * @return number of characters read or -1 on end of file.
   */
  public int read(CharBuffer buf, int length) throws IOException
  {
    int len = buf.length();

    buf.length(len + length);
    
    int readLength = read(buf.buffer(), len, length);
    
    if (readLength < 0) {
      buf.length(len);
    }
    else if (readLength < length) {
      buf.length(len + readLength);
    }

    return length;
  }

  /**
   * Reads characters from the stream, appending to the character buffer.
   * <code>length</code> characters will always be read until the end of
   * file.
   *
   * @param buf character buffer to fill
   * @param length maximum number of characters to read
   * @return number of characters read or -1 on end of file.
   */
  public int readAll(CharBuffer buf, int length) throws IOException
  {
    int len = buf.length();

    buf.length(len + length);
    int readLength = readAll(buf.buffer(), len, length);
    if (readLength < 0)
      buf.length(len);
    else if (readLength < length)
      buf.length(len + readLength);

    return length;
  }

  /**
   * Reads a line from the stream, returning a string.
   */
  public final String readln() throws IOException
  {
    return readLine();
  }

  /**
   * Reads a line, returning a string.
   */
  public String readLine() throws IOException
  {
    CharBuffer cb = _cb;

    if (cb == null) {
      cb = _cb = new CharBuffer();
    }
    else {
      cb.length(0);
    }

    String result;

    if (readLine(cb, true)) {
      result = cb.toString();
      cb.length(0);
    }
    else if (cb.length() == 0)
      result = null;
    else {
      result = cb.toString();
      cb.length(0);
    }

    return result;
  }

  /**
   * Reads a line, returning a string.
   */
  public String readLineNoChop() throws IOException
  {
    CharBuffer cb = new CharBuffer();

    if (readLine(cb, false))
      return cb.toString();
    else if (cb.length() == 0)
      return null;
    else
      return cb.toString();
  }

  /**
   * Fills the buffer with the next line from the input stream.
   *
   * @return true on success, false on end of file.
   */
  public final boolean readln(CharBuffer cb) throws IOException
  {
    return readLine(cb, true);
  }

  /**
   * Reads a line into the character buffer.  \r\n is converted to \n.
   *
   * @param buf character buffer to fill
   * @return false on end of file
   */
  public final boolean readLine(CharBuffer cb)
    throws IOException
  {
    return readLine(cb, true);
  }

  /**
   * Reads a line into the character buffer.  \r\n is converted to \n.
   *
   * @param buf character buffer to fill
   * @return false on end of file
   */
  public final boolean readLine(CharBuffer cb, boolean isChop)
    throws IOException
  {
    if (_readEncoding != null)
      return readlnEncoded(cb, isChop);

    int capacity = cb.capacity();
    int offset = cb.length();
    char []buf = cb.buffer();

    byte []readBuffer = _readBuffer;

    while (true) {
      int readOffset = _readOffset;
      int readLength = _readLength;

      int sublen = Math.min(capacity - offset, readLength - readOffset);

      for (; sublen > 0; sublen--) {
        int ch = readBuffer[readOffset++] & 0xff;

        if (ch != '\n') {
          buf[offset++] = (char) ch;
        }
        else if (isChop) {
          if (offset > 0 && buf[offset - 1] == '\r') {
            cb.length(offset - 1);
          }
          else {
            cb.length(offset);
          }

          _readOffset = readOffset;

          return true;
        }
        else {
          buf[offset++] = (char) '\n';

          cb.length(offset);

          _readOffset = readOffset;

          return true;
        }
      }

      _readOffset = readOffset;

      if (readLength <= readOffset) {
        if (! readBuffer()) {
          cb.length(offset);
          return offset > 0;
        }
      }

      if (capacity <= offset) {
        cb.length(offset + 1);
        capacity = cb.capacity();
        buf = cb.buffer();
      }
    }
  }

  /**
   * Reads a line into the character buffer.  \r\n is converted to \n.
   *
   * @param buf character buffer to fill.
   * @param length number of characters to fill.
   *
   * @return -1 on end of file or the number of characters read.
   */
  public final int readLine(char []buf, int length)
    throws IOException
  {
    return readLine(buf, length, true);
  }

  /**
   * Reads a line into the character buffer.  \r\n is converted to \n.
   *
   * @param buf character buffer to fill.
   * @param length number of characters to fill.
   *
   * @return -1 on end of file or the number of characters read.
   */
  public final int readLine(char []buf, int length, boolean isChop)
    throws IOException
  {
    byte []readBuffer = _readBuffer;

    int offset = 0;

    while (true) {
      int readOffset = _readOffset;

      int sublen = Math.min(length, _readLength - readOffset);

      for (; sublen > 0; sublen--) {
        int ch = readBuffer[readOffset++] & 0xff;

        if (ch != '\n') {
        }
        else if (isChop) {
          _readOffset = readOffset;

          if (offset > 0 && buf[offset - 1] == '\r')
            return offset - 1;
          else
            return offset;
        }
        else {
          buf[offset++] = (char) ch;

          _readOffset = readOffset;

          return offset + 1;
        }

        buf[offset++] = (char) ch;
      }
      _readOffset = readOffset;

      if (readOffset <= _readLength) {
        if (! readBuffer()) {
          return offset;
        }
      }

      if (length <= offset)
        return length + 1;
    }
  }

  private boolean readlnEncoded(CharBuffer cb, boolean isChop)
    throws IOException
  {
    while (true) {
      int ch = readChar();

      if (ch < 0)
        return cb.length() > 0;

      if (ch != '\n') {
      }
      else if (isChop) {
        if (cb.length() > 0 && cb.charAt(cb.length() - 1) == '\r')
          cb.length(cb.length() - 1);

        return true;
      }
      else {
        cb.append('\n');

        return true;
      }

      cb.append((char) ch);
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
   * Reads a utf-8 string
   */
  public int readUTF8ByByteLength(char []buffer, int offset, int byteLength)
    throws IOException
  {
    int k = 0;
    for (int i = 0; i < byteLength; i++) {
      if (_readLength <= _readOffset) {
        readBuffer();
      }

      int ch = _readBuffer[_readOffset++] & 0xff;

      if (ch < 0x80) {
        buffer[k++] = (char) ch;
      }
      else if ((ch & 0xe0) == 0xc0) {
        int c2 = read();
        i += 1;
        buffer[k++] = (char) (((ch & 0x1f) << 6) + (c2 & 0x3f));
      }
      else {
        int c2 = read();
        int c3 = read();

        i += 2;
        buffer[k++] = (char) (((ch & 0x1f) << 12)
                              + ((c2 & 0x3f) << 6)
                              + ((c3 & 0x3f)));
      }
    }

    return k;
  }

  /**
   * Copies this stream to the output stream.
   *
   * @param os destination stream.
   */
  public void writeToStream(OutputStream os) throws IOException
  {
    if (_readLength <= _readOffset) {
      readBuffer();
    }

    while (_readOffset < _readLength) {
      os.write(_readBuffer, _readOffset, _readLength - _readOffset);

      readBuffer();
    }
  }

  /**
   * Writes <code>len<code> bytes to the output stream from this stream.
   *
   * @param os destination stream.
   * @param len bytes to write.
   */
  public void writeToStream(OutputStream os, int len)
    throws IOException
  {
    while (len > 0) {
      if (_readLength <= _readOffset) {
        if (! readBuffer())
          return;
      }

      int sublen = Math.min(len, _readLength - _readOffset);

      os.write(_readBuffer, _readOffset, sublen);
      _readOffset += sublen;
      len -= sublen;
    }
  }

  /**
   * Copies this stream to the output stream.
   *
   * @param out destination writer
   */
  public void writeToWriter(Writer out) throws IOException
  {
    int ch;

    while ((ch = readChar()) >= 0) {
      out.write((char) ch);
    }
  }

  /**
   * Fills the buffer from the underlying stream.
   */
  public int fillBuffer()
    throws IOException
  {
    if (! readBuffer())
      return -1;
    else
      return _readLength;
  }

  /**
   * Fills the buffer with a non-blocking read.
   */
  public boolean readNonBlock()
    throws IOException
  {
    if (_readOffset < _readLength)
      return true;

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
   * Disables close.  Sometimes an application will pass a stream
   * to a client that may close the stream at an inappropriate time.
   * Setting disable close gives the calling routine control over closing
   * the stream.
   */
  public void setDisableClose(boolean disableClose)
  {
    _disableClose = disableClose;
  }

  /**
   * Disables closing of the underlying source.
   */
  public void setDisableCloseSource(boolean disableClose)
  {
    _isDisableCloseSource = disableClose;
  }

  /**
   * Close the stream.
   */
  @Override
  public final void close()
  {
    try {
      if (_disableClose)
        return;

      if (! _reuseBuffer) {
        if (_tempRead != null) {
          TempBuffer tempBuffer = _tempRead;
          _tempRead = null;

          TempBuffer.free(tempBuffer);
        }
        _readBuffer = null;
      }

      if (_readEncoding != null) {
        Reader reader = _readEncoding;
        _readEncoding = null;
        reader.close();
      }

      if (_source != null && ! _isDisableCloseSource) {
        StreamImpl s = _source;
        _source = null;
        s.close();
      }
    } catch (IOException e) {
      log().log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Returns a named attribute.  For example, an HTTP stream
   * may use this to return header values.
   */
  /*
  public Object getAttribute(String name)
    throws IOException
  {
    return _source.getAttribute(name);
  }
  */

  /**
   * Lists all named attributes.
   */
  /*
  public Iterator<String> getAttributeNames()
    throws IOException
  {
    return _source.getAttributeNames();
  }
  */

  /**
   * Sets a named attribute.  For example, an HTTP stream
   * may use this to set header values.
   */
  /*
  public void setAttribute(String name, Object value)
    throws IOException
  {
    _source.setAttribute(name, value);
  }
  */

  /**
   * Removes a named attribute.
   */
  /*
  public void removeAttribute(String name)
    throws IOException
  {
    _source.removeAttribute(name);
  }
  */

  /**
   * Returns the Path which opened this stream.
   */
  /*
  public PathImpl getPath()
  {
    return _source == null ? null : _source.getPath();
  }
  */

  /**
   * Returns the user path which opened this stream.
   *
   * <p>Parsing routines typically use this for error reporting.
   */
  /*
  public String getUserPath()
  {
    if (_source == null || _source.getPath() == null)
      return "stream";
    else
      return _source.getPath().getUserPath();
  }
  */

  /**
   * Returns the user path which opened this stream.
   *
   * <p>Parsing routines typically use this for error reporting.
   */
  /*
  public String getURL()
  {
    if (_source == null || _source.getPath() == null)
      return "stream:";
    else
      return _source.getPath().getURL();
  }
  */

  /**
   * Sets a path name associated with the stream.
   */
  /*
  public void setPath(PathImpl path)
  {
    _source.setPath(path);
  }
  */

  /**
   * Returns a Reader reading to this stream.
   */
  public Reader getReader()
  {
    if (_reader == null)
      _reader = new StreamReader();

    return _reader;
  }

  private static Logger log()
  {
    return Logger.getLogger(ReadStreamOld.class.getName());
  }

  /**
   * Returns a printable representation of the read stream.
   */
  public String toString()
  {
    return "ReadStream[" + _source + "]";
  }

  /*
  public boolean lock(boolean shared, boolean block)
  {
    if (! (_source instanceof LockableStream))
      return true;

    LockableStream ls = (LockableStream) _source;
    return ls.lock(shared, block);
  }

  public boolean unlock()
  {
    if (! (_source instanceof LockableStream))
      return true;

    LockableStream ls = (LockableStream) _source;
    return ls.unlock();
  }
  */

  public class StreamReader extends Reader {
    public final int read()
      throws IOException
    {
      return ReadStreamOld.this.readChar();
    }

    public final int read(char []cbuf, int off, int len)
      throws IOException
    {
      return ReadStreamOld.this.read(cbuf, off, len);
    }

    public boolean ready()
      throws IOException
    {
      return ReadStreamOld.this.available() > 0;
    }

    public final void close()
      throws IOException
    {
    }

    public ReadStreamOld getStream()
    {
      return ReadStreamOld.this;
    }
  }
}
