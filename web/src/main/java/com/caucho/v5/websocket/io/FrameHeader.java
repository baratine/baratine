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
import java.util.logging.Level;
import java.util.logging.Logger;

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
 *   1 - text
 *   2 - binary
 *   8 - close
 * </pre></code>
 */
public final class FrameHeader implements WebSocketConstants
{
  private static final Logger log
    = Logger.getLogger(FrameHeader.class.getName());
  
  private boolean _isFinal = true;
  private int _op;
  private long _length;
  
  private int _frame1;
  private int _frame2;

  public FrameHeader()
  {
  }

  public int getOpcode()
  {
    return _op;
  }
  
  public boolean isFinal()
  {
    return _isFinal;
  }

  public long getLength()
  {
    return _length;
  }
  
  public int getRsv()
  {
    return _frame1 & 0x70;
  }

  public boolean readFrameHeader(InputStream is)
    throws IOException
  {
    int frame1 = is.read();
    int frame2 = is.read();
    
    _frame1 = frame1;
    _frame2 = frame2;

    /*
    System.out.println("WS: 0x" + Integer.toHexString(frame1)
                       + " 0x" + Integer.toHexString(frame2));
                       */

    if (frame2 < 0) {
      return false;
    }

    boolean isFinal = (frame1 & FLAG_FIN) == FLAG_FIN;
    _op = frame1 & 0xf;
    
    int rsv = frame1 & 0x70;
    
    if (rsv != 0) {
      /*
      if (getContext() != null) {
        getContext().close(CLOSE_ERROR, "illegal request");
      }
      */
      
      if (log.isLoggable(Level.FINE)) {
        log.fine(this + " WebSocket BAD_REQ:"+ Integer.toHexString(frame1)
                 + " " + Integer.toHexString(frame2));
      }

      return false;
    }

    _isFinal = isFinal;

    long length = frame2 & 0x7f;

    if (length < 0x7e) {
    }
    else if (length == 0x7e) {
      length = ((((long) is.read()) << 8)
          + (((long) is.read())));
    }
    else {
      length = ((((long) is.read()) << 56)
          + (((long) is.read()) << 48)
          + (((long) is.read()) << 40)
          + (((long) is.read()) << 32)
          + (((long) is.read()) << 24)
          + (((long) is.read()) << 16)
          + (((long) is.read()) << 8)
          + (((long) is.read())));
    }

    _length = length;

    return true;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
