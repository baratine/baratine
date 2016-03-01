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

package io.baratine.web;

import java.io.OutputStream;
import java.io.Writer;

import io.baratine.io.Buffer;
import io.baratine.web.WebSocketClose.WebSocketCloses;

public interface WebSocket<T> // extends InSource<Buffer> // , OutPipe<Buffer>
{
  String uri();
  String path();
  String pathInfo();
  
  void next(T data);
  
  void write(Buffer data);
  void writePart(Buffer data);
  
  void write(byte []buffer, int offset, int length);
  void writePart(byte []buffer, int offset, int length);
  
  void write(String data);
  void writePart(String data);
  
  void ping(String data);
  void pong(String data);

  default OutputStream outputStream()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  default Writer writer()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  void flush();
  
  boolean isClosed();
  
  void fail(Throwable exn);
  
  void close(WebSocketClose code, String text);
  
  default void close()
  {
    close(WebSocketCloses.NORMAL_CLOSURE, "ok");
  }
  
  public interface Frame
  {
    boolean part();
    FrameType type();
    
    String text();
    Buffer binary();
  }
  
  enum FrameType
  {
    TEXT,
    BINARY,
    PING,
    PONG;
  }
}
