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
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Path;

import com.caucho.v5.io.ServerSocketBar;
import com.caucho.v5.util.L10N;


/**
 * Abstract socket to handle both normal sockets and jni sockets.
 */
public class ServerSocketFactoryBar
{
  private static final L10N L = new L10N(ServerSocketFactoryBar.class);

  public ServerSocketBar create(InetAddress host, 
                              int port, 
                              int listenBacklog,
                              boolean isEnableJni)
    throws IOException
  {
    ServerSocketChannel ss = ServerSocketChannel.open();
    
    SocketAddress addr = new InetSocketAddress(host, port);
    
    for (int i = 0; i < 10; i++) {
      /*
      try {
        ServerSocket ss = new ServerSocket(port, listenBacklog, host);
      
        return new ServerSocketWrapper(ss);
      } catch (BindException e) {
      }
      */

      try {
        ss.bind(addr, listenBacklog);
        //ServerSocket ss = new ServerSocket(port, listenBacklog, host);
      
        return new ServerSocketChannelWrapper(ss);
      } catch (BindException e) {
      }

      try {
        Thread.sleep(1);
      } catch (Throwable e) {
      }
    }
    
    try {
      //ServerSocket ss = new ServerSocket(port, listenBacklog, host);
      ss.bind(addr, listenBacklog);
      
      return new ServerSocketChannelWrapper(ss);
    } catch (BindException e) {
      if (host != null)
        throw new BindException(L.l("{0}\nCan't bind to {1}:{2}.\nCheck for another server listening to that port.", e.getMessage(), host, String.valueOf(port)));
      else
        throw new BindException(L.l("{0}\nCan't bind to *:{1}.\nCheck for another server listening to that port.", e.getMessage(), String.valueOf(port)));
    }
  }
  
  public ServerSocketBar bindPath(Path path)
    throws IOException
  {
      throw new BindException(L.l("Unix socket '{0}' requires compiled JNI."));
  }

  public ServerSocketBar open(int fd, int port)
      throws IOException
  {
    throw new BindException(L.l("Socket file descriptor open fd={0} port={1} requires compiled JNI.",
                                fd, port));
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}

