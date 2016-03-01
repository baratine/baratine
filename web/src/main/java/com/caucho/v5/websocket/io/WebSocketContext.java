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
import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * Bidirectional TCP connection based on a HTTP upgrade, e.g. WebSocket.
 *
 * The context and its values are not thread safe.  The DuplexListener
 * thread normally is the only thread reading from the input stream.
 */
public interface WebSocketContext
{
  /**
   * Returns the output stream for a binary message.
   * The message will complete when the OutputStream is closed.
   */
  public OutputStream startBinaryMessage()
    throws IOException;

  /**
   * Returns the output stream for a binary message.
   * The message will complete when the Writer is closed.
   */
  public PrintWriter startTextMessage()
    throws IOException;

  /**
   * Sets the read timeout.
   */
  public void setTimeout(long timeout);

  /**
   * Gets the read timeout.
   */
  public long getTimeout();
  
  /**
   * auto-flush after each message is sent.
   */
  public void setAutoFlush(boolean isAutoFlush);
  
  /**
   * returns the current flush mode.
   */
  public boolean isAutoFlush();
  
  /**
   * flushes the output stream
   */
  public void flush() throws IOException;

  /**
   * gracefully close the connection, waiting for unread messages.
   */
  public void close();

  /**
   * gracefully close the connection, waiting for unread messages.
   */
  public void close(int code, String message);

  /**
   * sends a pong message
   */
  public void pong(byte[] value)
    throws IOException;

  /**
   * Disconnect the connection.
   */
  public void disconnect();

  /**
   * @param closeCode
   * @param closeMessage
   */
  public void onClose(int closeCode, String closeMessage);
}
