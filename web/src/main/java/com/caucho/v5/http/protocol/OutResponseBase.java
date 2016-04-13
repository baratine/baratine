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
import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.io.OutputStreamWithBuffer;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.util.L10N;

import io.baratine.io.Bytes;

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
  private static final int DEFAULT_SIZE = SIZE;

  private State _state = State.START;
  
  private final byte []_singleByteBuffer = new byte[1];

  // head of the expandable buffer
  private TempBuffer _head = TempBuffer.create();
  private TempBuffer _tail;

  private byte []_tailByteBuffer;
  private int _tailByteLength;
  private int _tailByteStart;

  // total buffer length
  private int _bufferCapacity;
  // extended buffer length
  private int _bufferSize;

  private long _contentLength;

  //private RequestHttpBase _request;

  //
  // abstract methods
  //
  
  abstract protected void flush(Bytes data, boolean isEnd);

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

  public boolean isChunked()
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

  //
  // implementations
  //
  
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
   * Sets the byte offset.
   */
  @Override
  public void offset(int offset)
    throws IOException
  {
    _tailByteLength = offset;
  }

  public long contentLength()
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
        TempBuffer tempBuf = TempBuffer.create();
        _tail.next(tempBuf);
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

  public void write(Bytes data)
  {
    Objects.requireNonNull(data);
    
    int length = data.length();
    
    TempBuffer tBuf = TempBuffer.create();
    byte []buffer = tBuf.buffer();

    int pos = 0;
    while (pos < length) {
      int sublen = Math.min(length - pos, buffer.length);
      
      data.get(pos, buffer, 0, sublen);
      
      write(buffer, 0, sublen);
      
      pos += sublen;
    }
    
    tBuf.freeSelf();
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

      TempBuffer tempBuf = TempBuffer.create();
      _tail.next(tempBuf);
      _tail = tempBuf;

      _tailByteBuffer = _tail.buffer();
      _tailByteLength = _tailByteStart;

      return _tailByteBuffer;
    }
  }

  protected final void flushByteBuffer()
  {
    flush(false);
  }
  
  protected int bufferStart()
  {
    return 0;
  }

  /**
   * Flushes the buffer.
   */
  @Override
  public void flush()
    throws IOException
  {
    flush(false);
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
      flush(true);
    } finally {
      try {
        _state = _state.toClose();
      } catch (RuntimeException e) {
        throw new RuntimeException(state + ": " + e, e);
      }
    }
  }
  
  /**
   * Flushes the buffered response to the output stream.
   */
  private void flush(boolean isEnd)
  {
    if (_tailByteStart == _tailByteLength && _bufferSize == 0) {
      if (! isCommitted() || isEnd) {
        flush(null, isEnd);
        _tailByteStart = bufferStart();
        _tailByteLength = _tailByteStart;
      }
      return;
    }

    int sublen = _tailByteLength - _tailByteStart;
    _tail.length(_tailByteLength);
    _contentLength += _tailByteLength - _tailByteStart;
    _bufferSize = 0;
    
    if (_tailByteStart > 0) {
      fillChunkHeader(_tail, sublen);
    }
    
    flush(_head, isEnd);
    _head = TempBuffer.create();
    
    _tailByteStart = bufferStart();
    _tailByteLength = _tailByteStart;

    _tail = _head;
    if (! isEnd) {
      _tail.length(_tailByteLength);
    }
    _tailByteBuffer = _tail.buffer();
  }
  
  protected void fillChunkHeader(TempBuffer buf, int sublen)
  {
    throw new UnsupportedOperationException();
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
