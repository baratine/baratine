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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import io.baratine.io.Buffer;

public class FrameOutBuffer implements Buffer
{
  private byte[] _header;
  private Buffer _payload;

  private int _index;

  public FrameOutBuffer(byte[] header, Buffer payload)
  {
    _header = header;
    _payload = payload;
  }

  @Override
  public int length()
  {
    return _header.length - _index + _payload.length();
  }

  @Override
  public void read(ByteBuffer buffer)
  {
    int pos = _index;

    if (pos < _header.length) {
      int len = Math.min(_header.length - pos, buffer.remaining());

      buffer.put(_header, pos, len);

      _index += len;
    }

    _payload.read(buffer);
  }

  @Override
  public int read(byte []buffer, int offset, int length)
  {
    int readLen = 0;
    int pos = _index;

    if (pos < _header.length) {
      int len = Math.min(_header.length - pos, length);

      System.arraycopy(_header, pos, buffer, offset, len);

      length -= len;
      readLen += len;
    }

    if (length > 0) {
      readLen += _payload.read(buffer, offset + readLen, length);
    }

    _index += readLen;

    return readLen;
  }

  @Override
  public void read(OutputStream os) throws IOException
  {
    int pos = _index;
    int length = length();

    if (pos < _header.length) {
      int len = Math.min(_header.length - pos, length);

      os.write(_header, pos, len);

      length -= len;
      _index += len;
    }

    if (length > 0) {
      _payload.read(os);
    }
  }

  @Override
  public Buffer get(int pos, byte []buffer, int offset, int length)
  {
    if (pos < _header.length) {
      int len = Math.min(pos - _header.length, length);

      System.arraycopy(_header, pos, buffer, offset, len);

      length -= len;
    }

    pos -= _header.length;

    _payload.get(pos, buffer, offset + _header.length, length);

    return this;
  }

  @Override
  public Buffer set(int pos, byte []buffer, int offset, int length)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Buffer set(int pos, Buffer buffer, int offset, int length)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Buffer write(byte []buffer, int offset, int length)
  {
    if (true) {
      throw new UnsupportedOperationException();
    }

    _payload.write(buffer, offset, length);

    return this;
  }

  @Override
  public Buffer write(InputStream is)
    throws IOException
  {
    if (true)
    throw new UnsupportedOperationException();

    _payload.write(is);

    return this;
  }
}
