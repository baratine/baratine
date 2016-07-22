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
import java.io.InputStream;

import com.caucho.v5.util.L10N;

/**
 * WebSocketInputStream reads a single WebSocket packet.
 *
 * <code><pre>
 * +-+------+---------+-+---------+
 * |F|xxx(3)|opcode(4)|R|len(7)   |
 * +-+------+---------+-+---------+
 * 
 * OPCODES
 *   0 - cont
 *   1 - close
 *   2 - ping
 *   3 - pong
 *   4 - text
 *   5 - binary
 * </pre></code>
 */
public class WebSocketInputStream extends InputStream 
  implements WebSocketConstants
{
  private static final L10N L = new L10N(WebSocketInputStream.class);
  
  private final FrameIn _is;

  public WebSocketInputStream(FrameIn is)
  {
    _is = is;
  }

  public void init()
  {
  }

  /**
   * @return
   */
  public boolean startBinaryMessage()
    throws IOException
  {
    if (! _is.readFrameHeader()) {
      return false;
    }
    
    if (_is.getOpcode() != OP_BINARY) {
      throw new IllegalStateException(L.l("expected binary at '{0}' (in {1})",
                                          _is.getOpcode(),
                                          this));
    }
    return true;
  }

  public long getLength()
  {
    return _is.length();
  }
  
  @Override
  public int available()
  {
    long length = _is.length();
    
    if (length > 0) {
      return (int) Math.min(Integer.MAX_VALUE, length);
    }
    else if (_is.isFinal()) {
      return -1;
    }
    else {
      return 1;
    }
  }

  @Override
  public int read()
    throws IOException
  {
    return _is.readBinary();
  }

  @Override
  public int read(byte []buffer, int offset, int length)
    throws IOException
  {
    return _is.readBinary(buffer, offset, length);
  }
  
  public long skip(long length)
    throws IOException
  {
    return _is.skipBinary(length);
  }
  
  @Override
  public void close()
    throws IOException
  {
    _is.skipToMessageEnd();
  }
}
