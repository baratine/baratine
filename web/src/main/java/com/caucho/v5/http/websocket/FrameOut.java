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

  private static final int MAX_HEADER_LENGTH_NO_MASK = 14;

  private WebSocketBase<T,S> _ws;

  private char []_charBuf = new char[256];
  private TempBuffer _tBuf = TempBuffer.create();

  private int _headerLength;
  private int _expectedPayloadLength;

  private MessageState _state = MessageState.IDLE;

  public FrameOut(WebSocketBase<T,S> ws)
  {
    _ws = ws;
  }

  public void write(byte[] buffer, int offset, int length, boolean isFinal)
  {
    int end = offset + length;

    do {
      offset = writeBytes(buffer, offset, length, isFinal);
    } while (offset < end);
  }

  private int writeBytes(byte[] buffer, int offset, int length, boolean isFinal)
  {
    _state = _state.toBinary();

    int end = offset + length;

    // preallocate space for header
    preHeader(Math.min(length - offset, _tBuf.capacity() - MAX_HEADER_LENGTH_NO_MASK));

    while (true) {
      TempBuffer tBuf = _tBuf;
      int tCapacity = tBuf.capacity();

      int sublen = Math.min(end - offset, tCapacity - MAX_HEADER_LENGTH_NO_MASK);

      int tOffset = tBuf.length();
      System.arraycopy(buffer, offset, tBuf.buffer(), tOffset, sublen);

      offset += sublen;
      tOffset += sublen;

      if (offset == end) {
        tBuf.length(tOffset);
        completeFrame(isFinal);
        flush();

        break;
      }
      else if (tOffset == tCapacity) {
        tBuf.length(tOffset);
        completeFrame(false);
        flush();

        break;
      }
    }

    return offset;
  }

  public void write(Buffer buffer, boolean isFinal)
  {
    int offset = 0;
    int len = buffer.length();
    int end = offset + len;

    do {
      offset = write(buffer, offset, len, isFinal);
    } while (offset < end);
  }

  private int write(Buffer buffer, int offset, int length, boolean isFinal)
  {
    _state = _state.toBinary();

    int end = offset + length;

    // preallocate space for header
    preHeader(Math.min(length - offset, _tBuf.capacity() - MAX_HEADER_LENGTH_NO_MASK));

    while (true) {
      TempBuffer tBuf = _tBuf;
      int tCapacity = tBuf.capacity();
      int tOffset = tBuf.length();

      int sublen = Math.min(end - offset, tCapacity - tOffset);

      buffer.get(offset, tBuf.buffer(), tOffset, sublen);

      offset += sublen;
      tOffset += sublen;

      if (offset == end) {
        tBuf.length(tOffset);
        completeFrame(isFinal);
        flush();

        break;
      }
      else if (tOffset == tCapacity) {
        tBuf.length(tOffset);
        completeFrame(false);
        flush();

        break;
      }
    }

    return offset;
  }

  public void writeString(String data, boolean isFinal)
  {
    int offset = 0;
    int len = data.length();
    int end = offset + len;

    do {
      offset = writeString(data, offset, len, isFinal);
    } while (offset < end);
  }

  private int writeString(String data, int offset, int length, boolean isFinal)
  {
    _state = _state.toText();

    preHeader(Math.min(length - offset, _tBuf.capacity() - MAX_HEADER_LENGTH_NO_MASK));

    while (true) {
      TempBuffer tBuf = _tBuf;
      char cBuf[] = _charBuf;

      int sublen = Math.min(length - offset, cBuf.length);

      data.getChars(offset, offset + sublen, cBuf, 0);
      int cOffset = Utf8Util.write(tBuf, cBuf, 0, sublen);

      offset += cOffset;

      if (offset == length) {
        completeFrame(isFinal);
        flush();

        break;
      }
      else if (tBuf.buffer().length - tBuf.length() < 4) {
        completeFrame(false);
        flush();

        break;
      }
    }

    return offset;
  }

  public void pong(String data)
  {
    MessageState state = _state;

    _state = MessageState.PONG;

    char cBuf[] = _charBuf;
    TempBuffer tBuf = _tBuf;

    int sublen = Math.min(125, data.length());
    preHeader(sublen);

    int length = tBuf.length();

    data.getChars(0, sublen, cBuf, 0);
    int cOffset = Utf8Util.write(tBuf, cBuf, 0, sublen);

    // control frames bodies must not exceed 125 bytes
    tBuf.length(Math.min(length + 125, tBuf.length()));

    completeFrame(true);

    _state = state;
    flush();
  }

  public void ping(String data)
  {
    MessageState state = _state;

    _state = MessageState.PING;

    char cBuf[] = _charBuf;
    TempBuffer tBuf = _tBuf;

    int sublen = Math.min(125, data.length());
    preHeader(sublen);

    int length = tBuf.length();

    data.getChars(0, sublen, cBuf, 0);
    int cOffset = Utf8Util.write(tBuf, cBuf, 0, sublen);

    // control frames bodies must not exceed 125 bytes
    tBuf.length(Math.min(length + 125, tBuf.length()));

    completeFrame(true);

    for (int i = 0; i < tBuf.length(); i++) {
      byte[] buffer = tBuf.buffer();
    }

    _state = state;
    flush();
  }

  public void close(WebSocketClose reason, String data)
  {
    _state = _state.toClose();

    char cBuf[] = _charBuf;
    TempBuffer tBuf = _tBuf;
    byte []buffer = tBuf.buffer();

    int sublen = Math.min(125, data.length());
    preHeader(sublen);

    int code = reason.code();
    int length = tBuf.length();

    buffer[length + 0] = (byte) (code >> 8);
    buffer[length + 1] = (byte) (code);

    tBuf.length(length + 2);

    data.getChars(0, sublen, cBuf, 0);
    int cOffset = Utf8Util.write(tBuf, cBuf, 0, sublen);

    // control frames bodies must not exceed 125 bytes
    tBuf.length(Math.min(length + 125, tBuf.length()));

    completeFrame(true);

    flushEnd();
  }

  private void preHeader(int length)
  {
    _headerLength = calculateExpectedHeaderLength(length);
    _expectedPayloadLength = length;

    _tBuf.length(_headerLength);
  }

  public void completeFrame(boolean isFinal)
  {
    if (isFinal) {
      _state = _state.toFinal();
    }

    byte []buffer = _tBuf.buffer();
    int bufferLen = _tBuf.length();

    int actualPayloadLen = bufferLen - _headerLength;

    if (actualPayloadLen == _expectedPayloadLength) {
    }
    else {
      // payload is not as expected

      int expectedLenBytes = calculatePayloadLengthHeaderBytes(_expectedPayloadLength);
      int actualLenBytes = calculatePayloadLengthHeaderBytes(actualPayloadLen);

      if (expectedLenBytes != actualLenBytes) {
        System.arraycopy(buffer, _headerLength,
                         buffer, _headerLength + actualLenBytes - expectedLenBytes,
                         bufferLen);
      }
    }

    writeHeader(actualPayloadLen);

    if (isFinal) {
      _state = _state.toIdle();
    }
    else {
      _state = _state.toCont();
    }
  }

  private void writeHeader(int length)
  {
    byte []buffer = _tBuf.buffer();

    // XXX: mask
    int mask = 0;

    buffer[0] = (byte) _state.code();

    int sizeBytes = writeLengthHeader(length, mask);

    int headerLength = 2 + sizeBytes;

    _headerLength = headerLength;
  }

  /**
   * @return size of payload size header
   */
  private int writeLengthHeader(int length, int mask)
  {
    byte []buffer = _tBuf.buffer();

    if (length <= 125) {
      // 0x7d
      buffer[1] = (byte) (mask | length);

      return 0;
    }
    else if (length <= 1024 * 64) {
      // 0x7e
      buffer[1] = (byte) (mask | 126);
      buffer[2] = (byte) (length >> 8);
      buffer[3] = (byte) (length & 0xff);

      return 2;
    }
    else {
      // 0x7f
      buffer[1] = (byte) (mask | 127);
      buffer[2] = (byte) (length >> 56);
      buffer[3] = (byte) (length >> 48);
      buffer[4] = (byte) (length >> 40);
      buffer[5] = (byte) (length >> 32);
      buffer[6] = (byte) (length >> 24);
      buffer[7] = (byte) (length >> 16);
      buffer[8] = (byte) (length >> 8);
      buffer[9] = (byte) (length & 0xff);

      return 8;
    }
  }

  public void flush()
  {
    _ws.send(_tBuf);

    _tBuf = TempBuffer.create();
  }

  public void flushEnd()
  {
    _ws.sendEnd(_tBuf);

    _tBuf = TempBuffer.create();
  }

  private static int calculateExpectedHeaderLength(int length)
  {
    return 2 + calculatePayloadLengthHeaderBytes(length);
  }

  private static int calculatePayloadLengthHeaderBytes(int length)
  {
    if (length <= 125) {
      return 0;
    }
    else if (length <= 1024 * 64) {
      return 2;
    }
    else {
      return 8;
    }
  }


}
