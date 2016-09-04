/*
 * Copyright (c) 1998-2016 Caucho Technology -- all rights reserved
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
 * @author Nam Nguyen
 */

package com.caucho.v5.http.websocket;

import java.util.logging.Logger;

import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.Utf8Util;
import com.caucho.v5.websocket.io.MessageState;

import io.baratine.io.Buffer;
import io.baratine.web.WebSocketClose;

public class FrameOut<T,S>
{
  private static final L10N L = new L10N(FrameOut.class);
  private static final Logger log = Logger.getLogger(FrameOut.class.getName());

  private WebSocketBase<T,S> _ws;

  private char []_charBuf = new char[256];
  private TempBuffer _payload = TempBuffer.create();
  private MessageState _state = MessageState.IDLE;

  public FrameOut(WebSocketBase<T,S> ws)
  {
    _ws = ws;
  }

  public void write(byte[] buffer, int offset, int length, boolean isFinal)
  {
    int end = offset + length;

    do {
      offset += writeBytes(buffer, offset, end - offset, isFinal);
    } while (offset < end);
  }

  private int writeBytes(byte[] buffer, int offset, int length, boolean isFinal)
  {
    _state = _state.toBinary();

    int sublen = Math.min(_payload.capacity() - _payload.length(), length);
    _payload.write(buffer, offset, sublen);

    Buffer b = completeFrame(isFinal && sublen == length);
    send(b);

    return sublen;
  }

  public void write(Buffer buffer, boolean isFinal)
  {
    int offset = 0;
    int len = buffer.length();
    int end = offset + len;

    do {
      offset += write(buffer, offset, end - offset, isFinal);

    } while (offset < end);
  }

  private int write(Buffer buffer, int offset, int length, boolean isFinal)
  {
    _state = _state.toBinary();

    int sublen = Math.min(_payload.capacity() - _payload.length(), length);
    _payload.set(_payload.length(), buffer, offset, sublen);
    _payload.length(_payload.length() + sublen);

    Buffer b = completeFrame(isFinal && sublen == length);
    send(b);

    return sublen;
  }

  public void writeString(String data, boolean isFinal)
  {
    int offset = 0;
    int end = data.length();

    do {
      offset += writeString(data, offset, end - offset, isFinal);

    } while (offset < end);
  }

  private int writeString(String data, int offset, int length, boolean isFinal)
  {
    _state = _state.toText();

    int writeLen = 0;
    int end = offset + length;

    while (true) {
      char cBuf[] = _charBuf;

      int sublen = Math.min(end - offset, cBuf.length);
      data.getChars(offset, offset + sublen, cBuf, 0);

      int cOffset = Utf8Util.write(_payload, _payload.length(), _payload.capacity(), cBuf, 0, sublen);

      offset += cOffset;
      writeLen += cOffset;

      if (writeLen == length) {
        Buffer buffer = completeFrame(isFinal);
        send(buffer);

        break;
      }
      else if (_payload.capacity() - _payload.length() < 4) {
        Buffer buffer = completeFrame(false);
        send(buffer);

        break;
      }
    }

    return writeLen;
  }

  public void pong(String data)
  {
    MessageState state = _state;
    _state = MessageState.PONG;

    char cBuf[] = _charBuf;

    int sublen = Math.min(125, data.length());
    data.getChars(0, sublen, cBuf, 0);

    // control frame payloads must not exceed 0x7d bytes
    int cOffset = Utf8Util.write(_payload, _payload.length(), 0x7d, cBuf, 0, sublen);
    Buffer buffer = completeFrame(true);
    send(buffer);

    _state = state;
  }

  public void ping(String data)
  {
    MessageState state = _state;
    _state = MessageState.PING;

    char cBuf[] = _charBuf;

    int sublen = Math.min(125, data.length());
    data.getChars(0, sublen, cBuf, 0);

    // control frame payloads must not exceed 0x7d bytes
    int cOffset = Utf8Util.write(_payload, _payload.length(), 0x7d, cBuf, 0, sublen);

    Buffer buffer = completeFrame(true);
    send(buffer);

    _state = state;
  }

  public void close(WebSocketClose reason, String data)
  {
    _state = _state.toClose();

    char cBuf[] = _charBuf;

    int code = reason.code();

    byte[] buffer = _payload.buffer();
    buffer[0] = (byte) (code >> 8);
    buffer[1] = (byte) code;
    _payload.length(2);

    if (data != null && data.length() > 0) {
      int sublen = Math.min(125, data.length());
      data.getChars(0, sublen, cBuf, 0);

      // control frame payloads must not exceed 0x7d bytes
      int cOffset = Utf8Util.write(_payload, _payload.length(), 0x7d, cBuf, 0, sublen);
    }

    Buffer b = completeFrame(true);
    sendEnd(b);
  }

  private Buffer completeFrame(boolean isFinal)
  {
    if (isFinal) {
      _state = _state.toFinal();
    }

    byte[] header = createHeader(_state.code(), isFinal);

    if (isFinal) {
      _state = _state.toIdle();
    }
    else {
      _state = _state.toCont();
    }

    return new FrameOutBuffer(header, _payload);
  }

  private byte[] createHeader(int opCode, boolean isFinal)
  {
    byte[] header;

    long len = _payload.length();

    if (len <= 0x7d) {
      header = new byte[2];
      header[1] = (byte) (len);
    }
    else if (len <= 1024 * 64) {
      header = new byte[4];

      header[1] = (byte) (0x7e);
      header[2] = (byte) (len >> 8);
      header[3] = (byte) (len & 0xff);
    }
    else {
      header = new byte[10];

      header[1] = (byte) (0x7f);
      header[2] = (byte) (len >> 56);
      header[3] = (byte) (len >> 48);
      header[4] = (byte) (len >> 40);
      header[5] = (byte) (len >> 32);
      header[6] = (byte) (len >> 24);
      header[7] = (byte) (len >> 16);
      header[8] = (byte) (len >> 8);
      header[9] = (byte) (len & 0xff);
    }

    header[0] = (byte) opCode;

    return header;
  }

  public void send(Buffer buffer)
  {
    _ws.send(buffer);

    _payload = TempBuffer.create();
  }

  public void sendEnd(Buffer buffer)
  {
    _ws.sendEnd(buffer);

    _payload = TempBuffer.create();
  }
}
