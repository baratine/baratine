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

package com.caucho.v5.http.protocol2;


/**
 * Constants for HTTP/2.0
 */
public interface Http2Constants
{
  // frame types
  static final int FRAME_DATA = 0;
  static final int FRAME_HEADERS = 1;
  static final int FRAME_PRIORITY = 2;
  static final int FRAME_RST_STREAM = 3;
  static final int FRAME_SETTINGS = 4;
  static final int FRAME_PUSH_PROMISE = 5;
  static final int FRAME_PING = 6;
  static final int FRAME_GOAWAY = 7;
  static final int FRAME_WINDOW_UPDATE = 8;
  static final int FRAME_CONT = 9;
  
  static final int FRAME_BLOCKED = 0x0b; // not in RFC
  
  // flags
  static final int END_STREAM = 0x01;
  static final int END_SEGMENT = 0x02;
  static final int END_HEADERS = 0x04;
  static final int PRIORITY = 0x08;
  static final int PAD_LOW = 0x10;
  static final int PAD_HIGH = 0x20;
  
  static final int ACK = 0x01; // settings flag
  
  // settings
  static final int SETTINGS_HEADER_TABLE_SIZE = 1;
  static final int SETTINGS_ENABLE_PUSH = 2;
  static final int SETTINGS_MAX_CONCURRENT_STREAMS = 3;
  static final int SETTINGS_INITIAL_WINDOW_SIZE = 4;
  static final int SETTINGS_MAX_FRAME_SIZE = 5;
  static final int SETTINGS_HEADER_LIST_SIZE = 6;
  
  static final int INIT_HEADER_TABLE_SIZE = 4096;
  static final int INIT_ENABLE_PUSH = 1;
  static final int INIT_INITIAL_WINDOW_SIZE = 65536;
  static final int INIT_MAX_FRAME_SIZE = 16384;
  
  // error codes
  static final int NO_ERROR = 0;
  static final int PROTOCOL_ERROR = 1;
  static final int INTERNAL_ERROR = 2;
  static final int FLOW_CONTROL_ERROR = 3;
  static final int SETTINGS_TIMEOUT = 4;
  static final int STREAM_CLOSED = 5;
  static final int FRAME_SIZE_ERROR = 6;
  static final int REFUSED_STREAM = 7;  
  static final int CANCEL = 8;
  static final int COMPRESSION_ERROR = 9;
  static final int CONNECT_ERROR = 0xa;
  static final int ENHANCE_YOUR_CALM = 0xb;
  static final int INADEQUATE_SECURITY = 0xc;
  static final int HTTP_1_1_REQUIRED = 0xd;
  
  static final byte []CONNECTION_HEADER
    = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes();
  }
