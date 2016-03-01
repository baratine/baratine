/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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

package com.caucho.v5.io;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.Selector;

/**
 * Abstract socket to handle both normal sockets and jni sockets.
 */
abstract public class ServerSocketBar {
  public void setTcpNoDelay(boolean delay)
  {
  }
  
  public boolean isTcpNoDelay()
  {
    return false;
  }
  
  public void setTcpKeepalive(boolean isKeepalive)
  {
  }
  
  public boolean isTcpKeepalive()
  {
    return false;
  }
  
  public void setTcpCork(boolean isCork)
  {
  }
  
  public boolean isTcpCork()
  {
    return false;
  }
  
  public boolean isJni()
  {
    return false;
  }

  public boolean setSaveOnExec()
  {
    return false;
  }

  public int getSystemFD()
  {
    return -1;
  }

  /**
   * Sets the socket's listen backlog.
   */
  public void listen(int backlog)
  {
  }

  /**
   * Sets the connection read timeout.
   */
  abstract public void setConnectionSocketTimeout(int ms);
  
  abstract public boolean accept(SocketBar socket)
    throws IOException;
  
  abstract public SocketBar createSocket();

  abstract public InetAddress getLocalAddress();

  abstract public int getLocalPort();

  public Selector getSelector()
  {
    return null;
  }

  public boolean isClosed()
  {
    return false;
  }
  
  abstract public void close()
    throws IOException;
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getLocalAddress() + ":" + getLocalPort() + "]";
  }
}

