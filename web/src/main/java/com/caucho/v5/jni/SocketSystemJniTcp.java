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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.jni;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

import com.caucho.v5.io.SocketBar;
import com.caucho.v5.io.SocketSystemTcp;

/**
 * Standard TCP network system.
 */
public class SocketSystemJniTcp extends SocketSystemTcp 
{
  private static final Logger log = Logger.getLogger(SocketSystemJniTcp.class.getName());

  @Override
  public boolean isJni()
  {
    return true;
  }
  
  @Override
  public SocketBar createSocket()
  {
    if (JniSocketImpl.isEnabled()) {
      return new JniSocketImpl();
    }
    else {
      return super.createSocket();
    }
  }
  
  /*
  @Override
  public ServerSocketBar openUnixServerSocket(PathImpl unixPath)
    throws IOException
  {
    ServerSocketBar ss = ServerSocketJni.createPath(unixPath);
    
    return ss;
  }
  */

  @Override
  public SocketBar connect(SocketBar socket,
                         InetSocketAddress addr,
                         long connectTimeout,
                         boolean isSSL)
    throws IOException
  {
    try {
      JniSocketImpl jniSocket = null;
      
      if (socket instanceof JniSocketImpl) {
        jniSocket = (JniSocketImpl) socket;
      }

      if (JniSocketImpl.isEnabled() && ! isSSL) {
        jniSocket = JniSocketImpl.connect(jniSocket,
                                          addr.getAddress().getHostAddress(),
                                          addr.getPort());
      }

      if (jniSocket != null) {
        return jniSocket;
      }
    } catch (Exception e) {
      e.printStackTrace();
      log.finer(e.toString());
    }

    return super.connect(socket, addr, connectTimeout, isSSL);
  }

  /*
  @Override
  public SocketBar connectUnix(SocketBar qSocket, PathImpl path)
    throws IOException
  {
    try {
      JniSocketImpl jniSocket = null;

      if (! JniSocketImpl.isEnabled()) {
        return null;
      }
      
      if (qSocket instanceof JniSocketImpl) {
        jniSocket = (JniSocketImpl) qSocket;
      }
      
      jniSocket = JniSocketImpl.connectUnix(jniSocket, path);

      if (jniSocket != null) {
        return jniSocket;
      }
    } catch (Exception e) {
      e.printStackTrace();
      log.finer(e.toString());
    }

    return super.connectUnix(path);
  }
  */
}

