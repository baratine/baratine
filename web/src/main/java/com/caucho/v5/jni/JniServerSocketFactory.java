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

package com.caucho.v5.jni;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;

import com.caucho.v5.io.ServerSocketBar;


/**
 * Abstract socket to handle both normal sockets and bin/resin sockets.
 */
public class JniServerSocketFactory extends ServerSocketFactoryBar
{
  @Override
  public ServerSocketBar create(InetAddress host, 
                              int port, 
                              int listenBacklog,
                              boolean isEnableJni)
    throws IOException
  {
    if (isEnableJni && JniServerSocketImpl.isEnabled()) {
      String hostAddress;

      if (host != null)
        hostAddress = host.getHostAddress();
      else {
        hostAddress = null;
      }
      
      return JniServerSocketImpl.create(hostAddress, port);
    }
    else {
      return super.create(host, port, listenBacklog, isEnableJni);
    }
  }
  
  @Override
  public ServerSocketBar bindPath(Path path)
      throws IOException
  {
    /*
    if (JniServerSocketImpl.isEnabled()) {
      return JniServerSocketImpl.bindPath(path);
    }
    else {
      return super.bindPath(path);
    }
    */
    return super.bindPath(path);
  }

  @Override
  public ServerSocketBar open(int fd, int port)
      throws IOException
  {
    if (JniServerSocketImpl.isEnabled()) {
      return JniServerSocketImpl.open(fd, port);
    }
    else {
      return super.open(fd, port);
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}

