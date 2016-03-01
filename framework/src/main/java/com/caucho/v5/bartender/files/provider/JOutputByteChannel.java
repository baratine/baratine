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
 * @author Nam Nguyen
 */

package com.caucho.v5.bartender.files.provider;

import io.baratine.files.BfsFileSync;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.util.L10N;

public class JOutputByteChannel implements SeekableByteChannel
{
  private static final L10N L = new L10N(JOutputByteChannel.class);

  private final BfsFileSync _file;
  private OutputStream _os;
  private long _position;

  public JOutputByteChannel(BfsFileSync file, OutputStream os)
  {
    _file = file;
    _os = os;
  }

  @Override
  public boolean isOpen()
  {
    return _os != null;
  }

  @Override
  public int read(ByteBuffer dst) throws IOException
  {
    throw new IOException(L.l("cannot read on a write channel"));
  }

  @Override
  public int write(ByteBuffer src) throws IOException
  {
    TempBuffer tempBuffer = TempBuffer.allocate();
    byte[] buffer = tempBuffer.buffer();

    int remaining = src.remaining();
    int total = 0;

    while (remaining > 0) {
      int toRead = Math.min(remaining, buffer.length);

      src.get(buffer, 0, toRead);
      _os.write(buffer, 0, toRead);

      total += remaining - src.remaining();
      remaining = src.remaining();
    }

    tempBuffer.freeSelf();

    return total;
  }

  @Override
  public long position() throws IOException
  {
    return _position;
  }

  @Override
  public SeekableByteChannel position(long newPosition) throws IOException
  {
    if (newPosition < _position) {
      throw new IOException(L.l("cannot seek backwards, current position={0}, requested position={1}",
                                _position, newPosition));
    }

    byte[] buffer = new byte[1024];

    while (_position != newPosition) {
      long diff = newPosition - _position;

      int toWrite = (int) Math.min(diff, buffer.length);

      _os.write(buffer, 0, toWrite);

      _position += toWrite;
    }

    return this;
  }

  @Override
  public long size() throws IOException
  {
    return _position;
  }

  @Override
  public SeekableByteChannel truncate(long size) throws IOException
  {
    throw new UnsupportedOperationException(L.l("truncation is not supported"));
  }

  @Override
  public void close() throws IOException
  {
    OutputStream os = _os;
    _os = null;

    if (os != null) {
      os.close();
    }
  }
}
