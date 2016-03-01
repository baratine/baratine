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

package com.caucho.v5.jni;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.ServerSocketBar;
import com.caucho.v5.io.SocketBar;
import com.caucho.v5.io.SocketWrapperBar;

/**
 * Abstract socket to handle both normal sockets and jni sockets.
 */
public class ServerSocketWrapper extends ServerSocketBar {
  private static final Logger log
    = Logger.getLogger(ServerSocketWrapper.class.getName());
  
  private ServerSocket _ss;
  private boolean _isTcpNoDelay = true;
  private boolean _isTcpKeepalive;
  private int _connectionSocketTimeout = 65000;
  
  public ServerSocketWrapper()
  {
  }

  public ServerSocketWrapper(ServerSocket ss)
  {
    init(ss);
  }

  public void init(ServerSocket ss)
  {
    _ss = ss;
  }

  @Override
  public void setTcpNoDelay(boolean delay)
  {
    _isTcpNoDelay = delay;
  }

  @Override
  public boolean isTcpNoDelay()
  {
    return _isTcpNoDelay;
  }

  @Override
  public void setTcpKeepalive(boolean isEnable)
  {
    _isTcpKeepalive = isEnable;
  }

  @Override
  public boolean isTcpKeepalive()
  {
    return _isTcpKeepalive;
  }

  public void setConnectionSocketTimeout(int socketTimeout)
  {
    _connectionSocketTimeout = socketTimeout;
  }
  
  /**
   * Accepts a new socket.
   */
  public boolean accept(SocketBar qSocket)
    throws IOException
  {
    SocketWrapperBar s = (SocketWrapperBar) qSocket;

    Socket socket = _ss.accept();

    if (socket == null)
      return false;

    s.init(socket);

    /*
    // XXX:
    if (isTcpNoDelay())
      socket.setTcpNoDelay(true);
    
    if (isTcpKeepalive())
      socket.setKeepAlive(true);

    if (_connectionSocketTimeout > 0)
      socket.setSoTimeout(_connectionSocketTimeout);

    s.init(socket);
    */

    if (_connectionSocketTimeout > 0) {
      socket.setSoTimeout(_connectionSocketTimeout);
    }
    
    return true;
  }

  /**
   * Accepts a new socket.
   */
  /*
  @Override
  public int acceptInitialRead(QSocket qSocket,
                               byte []buffer, int offset, int length)
    throws IOException
  {
    QSocketWrapper s = (QSocketWrapper) qSocket;

    Socket socket = s.getSocket();

    // XXX:
    if (isTcpNoDelay())
      socket.setTcpNoDelay(true);
    
    if (isTcpKeepalive())
      socket.setKeepAlive(true);

    if (_connectionSocketTimeout > 0)
      socket.setSoTimeout(_connectionSocketTimeout);

    return socket.getInputStream().read(buffer, offset, length);
  }
  */
  
  /**
   * Creates a new socket object.
   */
  public SocketBar createSocket()
  {
    return new SocketWrapperBar();
  }

  public InetAddress getLocalAddress()
  {
    return _ss.getInetAddress();
  }

  public int getLocalPort()
  {
    return _ss.getLocalPort();
  }

  public Selector getSelector()
  {
    try {
      ServerSocketChannel channel = _ss.getChannel();

      if (channel == null)
        return null;
      
      SelectorProvider provider = channel.provider();

      if (provider != null)
        return provider.openSelector();
      else
        return null;
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      return null;
    }
  }

  /**
   * Closes the underlying socket.
   */
  public void close()
    throws IOException
  {
    ServerSocket ss = _ss;
    _ss = ss;

    if (ss != null) {
      try {
        ss.close();
      } catch (Exception e) {
      }
    }
  }
  
  public String toString()
  {
    return "ServerSocketWrapper[" + getLocalAddress() + ":" + getLocalPort() + "]";
  }
}

