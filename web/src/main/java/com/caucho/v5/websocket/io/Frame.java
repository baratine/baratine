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

package com.caucho.v5.websocket.io;

import io.baratine.io.Buffer;

public interface Frame
{
  boolean part();
  FrameType type();

  String text();
  Buffer binary();

  enum FrameType
  {
    TEXT,
    BINARY,
    PING,
    PONG;
  }

  public static class FrameBinary implements Frame
  {
    private long _length;
    private boolean _isPart;
    private Buffer _data;

    public FrameBinary(int length,
                       boolean isPart,
                       Buffer data)
    {
      _length = length;
      _isPart = isPart;
      _data = data;
    }

    @Override
    public boolean part()
    {
      return _isPart;
    }

    @Override
    public FrameType type()
    {
      return FrameType.BINARY;
    }

    @Override
    public String text()
    {
      return null;
    }

    @Override
    public Buffer binary()
    {
      return _data;
    }

    @Override
    public String toString()
    {
      return getClass().getSimpleName()
              + "[" + _length
              + (_isPart ? ",part" : "")
              + "," + _data
              + "]";
    }
  }

  public static class FrameText implements Frame
  {
    private long _length;
    private boolean _isPart;
    private String _data;

    public FrameText(int length,
                     boolean isPart,
                     String data)
    {
      _length = length;
      _isPart = isPart;
      _data = data;
    }

    @Override
    public boolean part()
    {
      return _isPart;
    }

    @Override
    public FrameType type()
    {
      return FrameType.TEXT;
    }

    @Override
    public String text()
    {
      return _data;
    }

    @Override
    public Buffer binary()
    {
      throw new IllegalStateException();
    }

    public String toString()
    {
      return getClass().getSimpleName()
              + "[" + _length
              + (_isPart ? ",part" : "")
              + "," + _data
              + "]";
    }
  }

  public static class FramePong extends FrameText
  {
    public FramePong(int length,
                     boolean isPart,
                     String data)
    {
      super(length, isPart, data);
    }
  }

  public static class FramePing extends FrameText
  {
    public FramePing(int length,
                     boolean isPart,
                     String data)
    {
      super(length, isPart, data);
    }
  }
}
