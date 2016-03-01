/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Baratine is software; you can redistribute it and/or modify
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

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.ServerSocketBar;
import com.caucho.v5.io.SocketBar;

import io.baratine.service.ServiceRef;

/**
 * Accepts requests from a tcp server socket.
 */

class AcceptTcp implements Runnable
{
  private static final Logger log
    = Logger.getLogger(AcceptTcp.class.getName());

  private final PortTcp _port;
  private final ServerSocketBar _serverSocket;

  /**
   * Creates a new accept thread
   */
  AcceptTcp(PortTcp port, ServerSocketBar serverSocket)
  {
    Objects.requireNonNull(port);
    Objects.requireNonNull(serverSocket);

    _port = port;
    _serverSocket = serverSocket;
  }

  /**
   * Returns the port which generated the connection.
   */
  PortTcp port()
  {
    return _port;
  }

  private boolean accept()
  {
    if (! _port.isActive()) {
      return false;
    }
    
    ConnectionTcp conn = port().newConnection();
    
    boolean isAccept = false;
    
    try {
      ServiceRef.flushOutbox();
      
      isAccept = accept(conn.socket());
      
      if (isAccept) {
        conn.proxy().requestAccept();
      }
    } finally {
      if (! isAccept) {
        port().freeConnection(conn);
      }
    }
    
    return isAccept;
  }

  /**
   * Accepts a new connection.
   */
  private boolean accept(SocketBar socket)
  {
    PortTcp port = port();
    
    try {
      while (! port().isClosed()) {
        // Thread.interrupted();

        if (_serverSocket.accept(socket)) {
          if (port.isClosed()) {
            socket.close();
            return false;
          }
          else if (isThrottle()) {
            socket.close();
          }
          else {
            return true;
          }
        }
      }
    } catch (Throwable e) {
      if (port.isActive() && log.isLoggable(Level.FINER)) {
        log.log(Level.FINER, e.toString(), e);
      }
    }

    return false;
  }
  
  private boolean isThrottle()
  {
    return false;
  }

  @Override
  public void run()
  {
    Thread thread = Thread.currentThread();
    String oldName = thread.getName();
    
    try {
      thread.setName("accept-" + _port.port());

      while (accept()) {
      }
    } catch (Exception e) {
      e.printStackTrace();
      log.log(Level.WARNING, e.toString(), e);
    } finally {
      thread.setName(oldName);
    }
  }
}
