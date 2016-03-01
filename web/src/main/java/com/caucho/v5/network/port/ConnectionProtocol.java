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

package com.caucho.v5.network.port;

import java.io.IOException;

/**
 * Protocol specific information for each connection. ConnectionProtocol
 * is reused to reduce memory allocations.
 *
 * <p>ConnectionProtocol are created by Protocol.newConnection
 */
public interface ConnectionProtocol
{
  /**
   * Returns a request URL for debugging/management.
   */
  default String url()
  {
    return toString();
  }
  
  /**
   * Called on connection accept.
   */
  default void onAccept()
  {
  }

  /**
   * Return true if the connection should wait for a read before
   * handling the request.
   */
  default boolean isWaitForRead()
  {
    return true;
  }

  /**
   * Handles a request.  The controlling TcpServer may call
   * service again after the connection completes, so
   * the implementation must initialize any variables for each connection.
   */
  StateConnection service() throws IOException;
  
  /**
   * Called when the connection times out, either from suspend or keepalive.
   */
  default void onTimeout()
  {
  }
  
  /**
   * The read has closed, reached eof.
   * @return 
   */
  default StateConnection onCloseRead()
  {
    return StateConnection.CLOSE;
  }

  /**
   * The connection is closed.
   */
  default void onClose()
  {
  }
  
  // XXX: following should be refactored

  /*
  default void onCloseWrite(ConnectionProtocol nextOut)
  {
  }

  default void next(ConnectionProtocol request)
  {
  }
  */
}
