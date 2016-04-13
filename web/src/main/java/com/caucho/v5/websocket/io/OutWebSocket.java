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

package com.caucho.v5.websocket.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

import com.caucho.v5.io.TempBuffer;

import static com.caucho.v5.websocket.io.WebSocketConstants.*; 

/**
 * WebSocketOutputStream writes a single WebSocket packet.
 *
 * <code><pre>
 * 0x82 0xNN binarydata
 * </pre></code>
 */
public class OutWebSocket
{
  private static final int BINARY_PASSTHROUGH_SIZE = 2048;
  
  private OutputStream _os;
  private byte []_buffer;
  private int _offset;
  
  private int _headOffset = 4;
  
  private MessageState _state = MessageState.IDLE;
  private boolean _isBatching = false;
  private boolean _isMasked;

  public OutWebSocket(OutputStream os, byte []workingBuffer)
  {
    Objects.requireNonNull(os);
    Objects.requireNonNull(workingBuffer);
    
    _os = os;

    _buffer = workingBuffer;
    
    setMasked(true);
  }

  public OutWebSocket(OutputStream os)
    throws IOException
  {
    this(os, TempBuffer.create().buffer());
  }
  
  public void setBatching(boolean isBatching)
  {
    _isBatching = isBatching;
  }
  
  public void setMasked(boolean isMasked)
  {
    _isMasked = isMasked;
    
    _headOffset = isMasked ? 8 : 4;
  }

  public boolean isIdle()
  {
    return _state == MessageState.IDLE;
  }
  
  public void init()
  {
    if (_state != MessageState.IDLE) {
      throw new IllegalStateException(String.valueOf(_state));
    }
    
    _state = MessageState.FIRST;
    
    _offset = _headOffset;
    
    if (_isMasked) {
      Arrays.fill(_buffer, 4, 4, (byte) 0);
    }
  }

  public void write(int ch)
    throws IOException
  {
    if (! _state.isActive())
      throw new IllegalStateException(String.valueOf(_state));
    
    byte []buffer = _buffer;

    if (_offset == buffer.length) {
      complete(false);
    }

    buffer[_offset++] = (byte) ch;
  }

  public void write(ByteBuffer data)
    throws IOException
  {
    if (! _state.isActive()) {
      throw new IllegalStateException(String.valueOf(_state));
    }
    
    byte []buffer = _buffer;
    
    while (data.hasRemaining()) {
      int avail = data.remaining();
      int sublen = Math.min(avail, buffer.length - _offset);

      if (sublen > 0) {
        data.get(buffer, _offset, sublen);
        
        _offset += sublen;
      }
      else {
        complete(false);
      }
    }
  }

  public void write(byte []buffer, int offset, int length)
    throws IOException
  {
    if (! _state.isActive())
      throw new IllegalStateException(String.valueOf(_state));
    
    byte []wsBuffer = _buffer;

    if (length >= BINARY_PASSTHROUGH_SIZE && ! _isMasked) {
      complete(false);
      
      while (length > 0) {
        int sublen = Math.min(0xffff, length);
        
        int writeOffset = fillHeader(false, sublen + 4);
        
        _os.write(wsBuffer, writeOffset, 4 - writeOffset);
        _os.write(buffer, offset, sublen);
        
        offset += sublen;
        length -= sublen;
      }
      
      return;
    }
      
    while (length > 0) {
      if (_offset == wsBuffer.length) {
        complete(false);
      }

      int sublen = Math.min(wsBuffer.length - _offset, length);

      System.arraycopy(buffer, offset, wsBuffer, _offset, sublen);

      offset += sublen;
      length -= sublen;
      _offset += sublen;
    }
  }

  public void flush()
    throws IOException
  {
    complete(false);

    _os.flush();
  }

  public void close()
    throws IOException
  {
    if (_state == MessageState.IDLE) {
      return;
    }
    
    try {
      complete(true);

      if (! _isBatching) {
        _os.flush();
      }
    } finally {
      _state = MessageState.IDLE;
    }
  }

  private void complete(boolean isFinal)
    throws IOException
  {
    byte []buffer = _buffer;
    
    int offset = _offset;
    _offset = _headOffset;

    int writeOffset = fillHeader(isFinal, offset); 

    // don't flush empty chunk
    if (writeOffset >= 0) {
      _os.write(buffer, writeOffset, offset - writeOffset);
    }
    
    if (_isMasked) {
      Arrays.fill(buffer, 4, 4, (byte) 0);
    }
  }

  private int fillHeader(boolean isFinal, int tailOffset)
    throws IOException
  {
    byte []buffer = _buffer;

    // don't flush empty chunk
    if (tailOffset == _headOffset && ! isFinal) {
      return -1;
    }

    int length = tailOffset - _headOffset;
    
    int code1;
    
    if (_state == MessageState.FIRST) {
      code1 = OP_BINARY;
    }
    else {
      code1 = OP_CONT;
    }
    
    _state = MessageState.CONT;
    
    if (isFinal) {
      code1 |= FLAG_FIN;
    }
    
    int mask = 0;
    
    if (_isMasked) {
      mask = 0x80;
      
      for (int i = 0; i < length; i++) {
        buffer[i + 8] ^= (byte) buffer[4 + (i & 0x3)]; 
      }
      
      //length += 4;
    }

    if (length < 0x7e) {
      buffer[2] = (byte) code1;
      buffer[3] = (byte) (length | mask);
    
      return 2;
    }
    else if (length <= 0xffff) {
      buffer[0] = (byte) code1;
      buffer[1] = (byte) (0x7e | mask);
      buffer[2] = (byte) (length >> 8);
      buffer[3] = (byte) (length);
      
      return 0;
    }
    else {
      throw new IllegalStateException();
    }
  }
  
  public void destroy()
    throws IOException
  {
    _state = MessageState.DESTROYED;
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _os + "]";
  }
  
  enum MessageState {
    IDLE,
    FIRST {
      @Override
      public boolean isActive() { return true; } 
    },
    CONT {
      @Override
      public boolean isActive() { return true; }
    },
    DESTROYED;
    
    public boolean isActive()
    {
      return false;
    }
  }
}
