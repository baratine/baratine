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

package com.caucho.v5.http.protocol;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.io.OutputStreamWithBuffer;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.io.i18n.Encoding;
import com.caucho.v5.io.i18n.EncodingWriter;
import com.caucho.v5.util.L10N;

import io.baratine.io.Buffer;

/**
 * API for handling the output stream.
 */
public abstract class OutResponseBase 
  extends OutputStreamWithBuffer
{
  private static final L10N L = new L10N(OutResponseBase.class);
  private static final Logger log
    = Logger.getLogger(OutResponseBase.class.getName());
  
  private static final int SIZE = TempBuffer.SIZE;
  //protected static final int DEFAULT_SIZE = 8 * SIZE;
  private static final int DEFAULT_SIZE = SIZE;
  private static final int CHAR_SIZE = 1024;

  private State _state = State.START;
  
  private char []_charBuffer = new char[CHAR_SIZE];
  private int _charLength;
  
  private final byte []_singleByteBuffer = new byte[1];

  // head of the expandable buffer
  private TempBuffer _head = TempBuffer.allocate();
  private TempBuffer _tail;

  private byte []_tailByteBuffer;
  private int _tailByteLength;
  private int _tailByteStart;

  // total buffer length
  private int _bufferCapacity;
  // extended buffer length
  private int _bufferSize;

  private long _contentLength;

  private RequestHttpBase _request;

  private EncodingWriter _toByte = Encoding.getLatin1Writer();
  
  //
  // abstract methods
  //
  
  abstract protected TempBuffer flushData(TempBuffer head,
                                          TempBuffer tail,
                                          boolean isEnd);

  //
  // state predicates
  //

  /**
   * Set true for HEAD requests.
   */
  public final boolean isHead()
  {
    return _state.isHead();
  }

  /**
   * Test if data has been flushed to the client.
   */
  public boolean isCommitted()
  {
    return _state.isCommitted();
  }
  
  /**
   * Test if the request is closing.
   */
  public boolean isClosing()
  {
    return _state.isClosing();
  }
  
  @Override
  public boolean isClosed()
  {
    return _state.isClosed();
  }
  
  /**
   * Test if the request is closing.
   */
  public boolean isCloseComplete()
  {
    return _state.isClosing();
  }

  public boolean isChunkedEncoding()
  {
    return false;
  }
  
  /**
   * Initializes the Buffered Response stream at the beginning of a request.
   */
  
  /**
   * Starts the response stream.
   */
  public void start()
  {
    _state = _state.toStart();
    
    _bufferCapacity = DEFAULT_SIZE;
    
    _tail = _head;
    
    _tailByteBuffer = _tail.buffer();
    _tailByteStart = bufferStart();
    _tailByteLength = _tailByteStart;

    _contentLength = 0;
  }

  /**
   * Response stream is a writable stream.
   */
  public boolean canWrite()
  {
    return true;
  }

  public boolean hasData()
  {
    return isCommitted() || _contentLength > 0;
  }

  private boolean lengthWarning(byte []buf, int offset, int length,
                                long contentLengthHeader)
  {
    if (_request.isConnectionClosed() || isHead() || isClosed()) {
    }
    else if (contentLengthHeader < _contentLength) {
      RequestHttpBase request = _request;//.getRequest();
      String msg = L.l("{0}: Can't write {1} extra bytes beyond the content-length header {2}.  Check that the Content-Length header correctly matches the expected bytes, and ensure that any filter which modifies the content also suppresses the content-length (to use chunked encoding).",
                       "uri", // request.getRequestURI(),
                       "" + (length + _contentLength),
                       "" + contentLengthHeader);

      log.fine(msg);

      return false;
    }

    for (int i = (int) (offset + contentLengthHeader - _contentLength);
         i < offset + length;
         i++) {
      int ch = buf[i];

      if (ch != '\r' && ch != '\n' && ch != ' ' && ch != '\t') {
        RequestHttpBase request = _request;//.getRequest();
        String graph = "";

        if (Character.isLetterOrDigit((char) ch))
          graph = "'" + (char) ch + "', ";

        String msg
          = L.l("{0}: tried to write {1} bytes with content-length {2} (At {3}char={4}).  Check that the Content-Length header correctly matches the expected bytes, and ensure that any filter which modifies the content also suppresses the content-length (to use chunked encoding).",
                "uri", // request.getRequestURI(),
                "" + (length + _contentLength),
                "" + contentLengthHeader,
                graph,
                "" + ch);

        log.fine(msg);
        break;
      }
    }

    length = (int) (contentLengthHeader - _contentLength);
    return (length <= 0);
  }

  //@Override
  /*
  protected void writeHeaders(int length)
    throws IOException
  {
    if (isCommitted()) {
      return;
    }

    // server/05ef
    if (! isCloseComplete()) {
      length = -1;
    }

    _response.writeHeaders(length);

    // server/2hf3
    toCommitted();
  }
  */

  //
  // implementations
  //

  /*
  abstract protected void writeNext(byte []buffer, int offset, int length,
                                    boolean isEnd)
    throws IOException;
    */

  protected String dbgId()
  {
    Object request = _request;

    if (request instanceof RequestHttpBase) {
      RequestHttpBase req = (RequestHttpBase) request;

      return req.dbgId();
    }
    else
      return "inc ";
  }
  
  //
  // byte buffer
  //

  /**
   * Returns the byte buffer.
   */
  @Override
  public byte []buffer()
    throws IOException
  {
    return _tailByteBuffer;
  }
  
  protected byte []getBufferImpl()
  {
    return _tailByteBuffer;
  }

  /**
   * Returns the byte offset.
   */
  @Override
  public int offset()
    throws IOException
  {
    return _tailByteLength;
  }

  /**
   * Returns the byte offset.
   */
  public int getByteBufferOffset()
  {
    return _tailByteLength;
  }

  /**
   * Sets the byte offset.
   */
  @Override
  public void offset(int offset)
    throws IOException
  {
    _tailByteLength = offset;
  }

  /**
   * Returns the buffer capacity.
   */
  public int getBufferCapacity()
  {
    return _bufferCapacity;
  }

  /**
   * Sets the buffer capacity.
   */
  public void setBufferCapacity(int size)
  {
    if (isCommitted()) {
      throw new IllegalStateException(L.l("Buffer size cannot be set after commit"));
    }

    _bufferCapacity = Math.max(0, SIZE * ((size + SIZE - 1) / SIZE));
  }

  /**
   * Returns the remaining value left.
   */
  public int getRemaining()
  {
    return _bufferCapacity - getBufferLength();
  }

  /**
   * Returns the data in the buffer
   */
  protected int getBufferLength()
  {    
    return _bufferSize + (_tailByteLength - _tailByteStart) + _charLength;
  }

  public long getContentLength()
  {
    return _contentLength + _tailByteLength - _tailByteStart;
  }

  /**
   * Writes a byte to the output.
   */
  @Override
  public void write(int ch)
    throws IOException
  {
    _singleByteBuffer[0] = (byte) ch;
    
    write(_singleByteBuffer, 0, 1);
  }

  /**
   * Writes a chunk of bytes to the stream.
   */
  @Override
  public void write(byte []buffer, int offset, int length)
  {
    if (isClosed() || isHead()) {
      return;
    }

    int byteLength = _tailByteLength;
    
    while (true) {
      int sublen = Math.min(length, SIZE - byteLength);

      System.arraycopy(buffer, offset, _tailByteBuffer, byteLength, sublen);
      offset += sublen;
      length -= sublen;
      byteLength += sublen;
      
      if (length <= 0) {
        break;
      }
      
      if (_bufferSize + byteLength < _bufferCapacity) {
        _tail.length(byteLength);
        TempBuffer tempBuf = TempBuffer.allocate();
        _tail.setNext(tempBuf);
        _tail = tempBuf;

        _bufferSize += SIZE;
        _tailByteBuffer = _tail.buffer();
        byteLength = _tailByteStart;
      }
      else {
        _tailByteLength = byteLength;
        flushByteBuffer();
        byteLength = _tailByteLength;
      }
    }

    _tailByteLength = byteLength;
  }

  /**
   * Returns the next byte buffer.
   */
  @Override
  public byte []nextBuffer(int offset)
    throws IOException
  {
    if (offset < 0 || SIZE < offset) {
      throw new IllegalStateException(L.l("Invalid offset: " + offset));
    }
    
    if (_bufferCapacity <= SIZE
        || _bufferCapacity <= offset + _bufferSize) {
      _tailByteLength = offset;
      flushByteBuffer();

      return buffer();
    }
    else {
      _tail.length(offset);
      _bufferSize += offset;

      TempBuffer tempBuf = TempBuffer.allocate();
      _tail.setNext(tempBuf);
      _tail = tempBuf;

      _tailByteBuffer = _tail.buffer();
      _tailByteLength = _tailByteStart;

      return _tailByteBuffer;
    }
  }

  protected final void flushByteBuffer()
  {
    flushByteBuffer(false);
  }
  
  protected int bufferStart()
  {
    return 0;
  }
  
  //
  // char buffer
  //

  /**
   * Writes a char array to the output.
   */
  public void print(char []buffer, int offset, int length)
    throws IOException
  {
    if (isClosed() || isHead()) {
      return;
    }

    int charLength = _charLength;

    while (length > 0) {
      int writeLength = _toByte.write(this, _charBuffer, 0, length);

      if (writeLength < length) {
        // XXX: surrogate pair issues
        System.arraycopy(_charBuffer, writeLength, _charBuffer, 0,
                         charLength - writeLength);
        charLength -= writeLength;
      }
      else {
        charLength = 0;
      }
    }

    _charLength = charLength;
  }

  /**
   * Flushes the buffer.
   */
  @Override
  public void flush()
    throws IOException
  {
    flushByteBuffer(false);
  }
  
  /**
   * Flushes the buffered response to the output stream.
   */
  protected void flushByteBuffer(boolean isEnd)
  {
    if (_tailByteStart == _tailByteLength && _bufferSize == 0) {
      if (! isCommitted() || isEnd) {
        // server/0101
        flushData(null, null, isEnd);
        _tailByteStart = bufferStart();
        _tailByteLength = _tailByteStart;
      }
      return;
    }

    _tail.length(_tailByteLength);
    _contentLength += _tailByteLength - _tailByteStart;
    _bufferSize = 0;
    _head = flushData(_head, _tail, isEnd);
    
    _tailByteStart = bufferStart();
    _tailByteLength = _tailByteStart;

    _tail = _head;
    if (! isEnd) {
      _tail.length(_tailByteLength);
    }
    _tailByteBuffer = _tail.buffer();
    /*
    if (! isEnd) {
      flushNext();
    }
    */
  }

  public void write(Buffer data)
  {
    Objects.requireNonNull(data);
    
    int length = data.length();
    
    TempBuffer tBuf = TempBuffer.allocate();
    byte []buffer = tBuf.buffer();

    int pos = 0;
    while (pos < length) {
      int sublen = Math.min(length - pos, buffer.length);
      
      data.getBytes(pos, buffer, 0, sublen);
      
      write(buffer, 0, sublen);
      
      pos += sublen;
    }
    
    tBuf.freeSelf();
  }

  /**
   * Flushes the output buffer.
   */
  //abstract public void flushBuffer()
  //  throws IOException;

  /*
  protected final void closeNext()
    throws IOException
  {
    boolean isValid = false; 
    try {
      closeNextImpl();
      
      isValid = true;
    } finally {
      if (! isValid) {
        _response.clientDisconnect();
      }
    }
  }
  */

  /**
   * Flushes the output.
   */
  /*
  public void flushByte()
    throws IOException
  {
    flushBuffer();
  }
  */

  /**
   * Sends a file.
   *
   * @param path the path to the file
   * @param length the length of the file (-1 if unknown)
   */
  public void sendFile(Path path, long offset, long length)
    throws IOException
  {
    //path.sendfile(this, offset, length);
    throw new UnsupportedOperationException();
  }

  protected void killCaching()
  {
  }
  
  public void completeCache()
  {
  }
  
  //
  // lifecycle
  //

  /**
   * Set true for HEAD requests.
   */
  public final void toHead()
  {
    _state = _state.toHead();
  }

  /**
   * Sets the committed state
   */
  public void toCommitted()
  {
    _state = _state.toCommitted();
  }

  public void upgrade()
  {
    //_state = _state.toUpgrade();
  }
  
  /**
   * Closes the response stream
   */
  @Override
  public final void close()
    throws IOException
  {
    State state = _state;
    
    if (state.isClosing()) {
      return;
    }
    
    _state = state.toClosing();
    
    try {
      closeImpl();
    } finally {
      try {
        _state = _state.toClose();
      } catch (RuntimeException e) {
        throw new RuntimeException(state + ": " + e, e);
      }
    }
  }

  /**
   * Closes the response stream.
   */
  private void closeImpl()
    throws IOException
  {
    flushByteBuffer(true);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _state + "]";
  }
  
  enum State {
    START {
      State toHead() { return HEAD; }
      State toCommitted() { return COMMITTED; }
      State toClosing() { return CLOSING; }
    },
    HEAD {
      boolean isHead() { return true; }
      
      State toHead() { return this; }
      State toCommitted() { return COMMITTED_HEAD; }
      State toClosing() { return CLOSING_HEAD; }
    },
    COMMITTED {
      boolean isCommitted() { return true; }
      
      State toHead() { return COMMITTED_HEAD; }
      State toCommitted() { return this; }
      State toClosing() { return CLOSING_COMMITTED; }
    },
    COMMITTED_HEAD {
      boolean isCommitted() { return true; }
      boolean isHead() { return true; }
      
      State toHead() { return this; }
      State toCommitted() { return this; }
      State toClosing() { return CLOSING_HEAD_COMMITTED; }
    },
    CLOSING {
      boolean isClosing() { return true; }
      
      State toHead() { return CLOSING_HEAD; }
      State toCommitted() { return CLOSING_COMMITTED; }
      State toClose() { return CLOSED; }
    },
    CLOSING_HEAD {
      boolean isHead() { return true; }
      boolean isClosing() { return true; }
      
      State toHead() { return this; }
      State toCommitted() { return CLOSING_HEAD_COMMITTED; }
      State toClose() { return CLOSED; }
    },
    CLOSING_COMMITTED {
      boolean isCommitted() { return true; }
      boolean isClosing() { return true; }
      
      State toHead() { return CLOSING_HEAD_COMMITTED; }
      State toCommitted() { return this; }
      // State toClosing() { Thread.dumpStack(); return CLOSED; }
      State toClose() { return CLOSED; }
    },
    CLOSING_HEAD_COMMITTED {
      boolean isHead() { return true; }
      boolean isCommitted() { return true; }
      boolean isClosing() { return true; }
      
      State toHead() { return this; }
      State toCommitted() { return this; }
      State toClose() { return CLOSED; }
    },
    CLOSED {
      boolean isCommitted() { return true; }
      boolean isClosed() { return true; }
      boolean isClosing() { return true; }
    };
    
    boolean isHead() { return false; }
    boolean isCommitted() { return false; }
    boolean isClosing() { return false; }
    boolean isClosed() { return false; }
   
    State toStart() { return START; }
    
    State toHead()
    { 
      throw new IllegalStateException(toString());
    }
    
    State toCommitted()
    {
      throw new IllegalStateException(toString());
    }
    
    State toClosing()
    {
      throw new IllegalStateException(toString());
    }
    
    State toClose()
    { 
      throw new IllegalStateException(toString());
    }
  }
}
