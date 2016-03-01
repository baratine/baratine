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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.io;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import com.caucho.v5.jni.ServerSocketJni;

/**
 * Standard TCP network system.
 */
public class SocketSystemJavaIo extends SocketSystem
{
  public SocketSystemJavaIo()
  {
    SocketSystem.setLocal(this);
  }

  @Override
  public ServerSocketBar openServerSocket(InetAddress address,
                                        int port,
                                        int backlog,
                                        boolean isJni)
    throws IOException
  {
    return ServerSocketJni.create(address, port, backlog, false);
  }
  
  @Override
  public SocketBar createSocket()
  {
    return new SocketWrapperBar();
  }

  @Override
  public SocketBar connect(SocketBar socket,
                         InetSocketAddress addr,
                         long connectTimeout,
                         boolean isSSL)
    throws IOException
  {
    Socket s = new Socket();

    if (connectTimeout > 0)
      s.connect(addr, (int) connectTimeout);
    else
      s.connect(addr);

    if (! s.isConnected()) {
      throw new IOException("connection timeout");
    }

    return new SocketWrapperBar(s);
  }
}

