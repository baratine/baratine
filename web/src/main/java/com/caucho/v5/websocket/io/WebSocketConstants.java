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

/**
 * WebSocketOutputStream writes a single WebSocket packet.
 *
 * <code><pre>
 * 0x82 0x8X 0x8X 0x0X binarydata
 * </pre></code>
 */
public interface WebSocketConstants {
  public static final String VERSION = "13";
  
  public static final int FLAG_FIN = 0x80;
  public static final int MASK_OPCODE = 0x0f;
  public static final int FLAG_MASK = 0x80;
  
  public static final int OP_CONT = 0x00;
  public static final int OP_TEXT = 0x01;
  public static final int OP_BINARY = 0x02;
  
  public static final int OP_CLOSE = 0x08;
  public static final int OP_PING = 0x09;
  public static final int OP_PONG = 0x0a;
  
  public static final int CLOSE_OK = 1000;
  public static final int CLOSE_ERROR = 1002;
  public static final int CLOSE_UTF8 = 1007;
  public static final int CLOSE_MESSAGE_TOO_BIG = 1008;
}
