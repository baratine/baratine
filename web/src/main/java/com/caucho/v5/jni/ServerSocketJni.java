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
import java.nio.file.Path;
import java.util.logging.Logger;

import com.caucho.v5.io.ServerSocketBar;
import com.caucho.v5.util.L10N;

/**
 * Abstract socket to handle both normal sockets and jni sockets.
 */
public class ServerSocketJni {
  private static final L10N L = new L10N(ServerSocketJni.class);
  private static final Logger log = Logger.getLogger(ServerSocketJni.class.getName());
  
  private static final ServerSocketJni _instance = new ServerSocketJni();
  
  private final ServerSocketFactoryBar _factory;
  
  private ServerSocketJni()
  {
    ServerSocketFactoryBar factory = new ServerSocketFactoryBar();
    
    try {
      //Class<?> cl = Class.forName("com.caucho.jni.JniServerSocketFactory");

      //factory = (QServerSocketFactory) cl.newInstance();
      factory = new JniServerSocketFactory();
    } catch (Throwable e) {
      log.fine(L.l("JNI Socket support requires compiled JNI.\n  {0}", e));
    }
      
    _factory = factory;
  }
  
  private static ServerSocketFactoryBar currentFactory()
  {
    return _instance.getFactory();
  }
  
  private ServerSocketFactoryBar getFactory()
  {
    return _factory;
  }

  /**
   * Creates the SSL ServerSocket.
   */
  public static ServerSocketBar create(int port, int listenBacklog)
    throws IOException
  {
    return create(null, port, listenBacklog, true);
  }
  
  public static ServerSocketBar create(InetAddress host, int port,
                                     int listenBacklog)
    throws IOException
  {
    return create(host, port, listenBacklog, true);
  }
  
  public static ServerSocketBar open(int fd, int port)
    throws IOException
  {
    return currentFactory().open(fd, port);
  }

  /**
   * Creates the SSL ServerSocket.
   */
  public static ServerSocketBar create(InetAddress host, int port,
                                     int listenBacklog,
                                     boolean isEnableJni)
    throws IOException
  {
    return currentFactory().create(host, port, listenBacklog, isEnableJni);
  }

  /**
   * Creates the SSL ServerSocket.
   */
  public static ServerSocketBar createJNI(InetAddress host, int port)
    throws IOException
  {
      return currentFactory().create(host, port, 0, true);
  }

  /**
   * Creates the SSL ServerSocket.
   */
  public static ServerSocketBar createPath(Path path)
    throws IOException
  {
    return currentFactory().bindPath(path);
  }

  /**
   * Creates the SSL ServerSocket.
   */
  public static ServerSocketBar openJNI(int fd, int port)
    throws IOException
  {
    return currentFactory().open(fd, port);
  }
}

