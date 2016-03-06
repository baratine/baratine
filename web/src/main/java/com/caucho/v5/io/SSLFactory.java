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

package com.caucho.v5.io;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.SocketChannel;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLSocket;

import com.caucho.v5.config.ConfigException;

/**
 * SSL factory to connect both normal sockets and JNI sockets.
 */
public interface SSLFactory {
  /**
   * Creates the SSL ServerSocket.
   */
  public ServerSocketBar create(InetAddress host, int port)
    throws ConfigException, IOException, GeneralSecurityException;
  
  /**
   * Creates the SSL ServerSocket.
   */
  public ServerSocketBar bind(ServerSocketBar ss)
    throws ConfigException, IOException, GeneralSecurityException;

  default SSLSocket ssl(SocketChannel s)
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
}

