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
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.ServerSocketBar;
import com.caucho.v5.io.SocketBar;
import com.caucho.v5.jni.JniUtil.JniLoad;
import com.caucho.v5.util.L10N;

/**
 * Abstract socket to handle both normal sockets and bin/resin sockets.
 */
public class JniServerSocketImpl extends ServerSocketBar
{
  private static final L10N L = new L10N(JniServerSocketImpl.class);
  private static final Logger log = Logger.getLogger(JniServerSocketImpl.class.getName());
  private static final JniTroubleshoot _jniTroubleshoot;
  
  private static final boolean _isSendfileEnabled;
  private static final boolean _isCorkEnabled;

  private long _fd;
  private String _id;
  
  private String _host;
  
  private long _socketTimeout = 120 * 1000L;
  
  private boolean _isSSL;

  /**
   * Creates the new server socket.
   */
  private JniServerSocketImpl(String host, int port)
    throws IOException
  {
    _fd = bindPort(host, port);

    _id = host + ":" + port;
    
    _host = host;

    if (_fd != 0) {
    }
    else if (port < 1000) {
      throw new IOException(L.l("Socket bind failed for {0}:{1} while running as {2}.  Check for for permissions (root on unix) and for other processes listening to the port.",
                                host, port,
                                System.getProperty("user.name")));
    }
    else {
      throw new IOException(L.l("Socket bind failed for {0}:{1} while running as {2}.  Check for other processes listening to the port.",
                                host, port,
                                System.getProperty("user.name")));
    }
  }

  /**
   * Creates the new server socket.
   */
  private JniServerSocketImpl(int fd, int port, boolean isOpen)
    throws IOException
  {
    _fd = nativeOpenPort(fd, port);

    _id = "fd=" + fd + ",port=" + port;

    if (_fd == 0)
      throw new java.net.BindException(L.l("Socket bind failed for port {0} fd={1} opened by watchdog.  Check that the watchdog and Resin permissions are properly configured.", port, fd));
  }

  /**
   * Creates the new server socket.
   */
  /*
  private JniServerSocketImpl(PathImpl path)
    throws IOException
  {
    path.getParent().mkdirs();
    
    _fd = bindUnix(path.getNativePath());

    _id = path.getNativePath();
    
    _host = "localhost";

    if (_fd == 0)
      throw new IOException(L.l("Unix socket bind failed for {0} while running as {1}.  Check for other processes listening to the port and check for permissions (root on unix).",
                                path,
                                System.getProperty("user.name")));
  }
  */

  public static boolean isEnabled()
  {
    return _jniTroubleshoot.isEnabled();
  }

  public static String getInitMessage()
  {
    if (! _jniTroubleshoot.isEnabled())
      return _jniTroubleshoot.getMessage();
    else
      return null;
  }
  
  public static boolean isSendfileEnabledStatic()
  {
    return _isSendfileEnabled;
  }
  
  public void setSSL(boolean isSSL)
  {
    _isSSL = isSSL;
  }
  
  public boolean isSendfileEnabled()
  {
    return isSendfileEnabledStatic() && ! _isSSL;
  }
  
  public static boolean isTcpCorkEnabled()
  {
    return _isCorkEnabled;
  }

  /**
   * Returns the file descriptor to OpenSSLFactory.  No other classes
   * may use the file descriptor.
   */
  public long getFd()
  {
    return _fd;
  }

  /**
   * Returns true if this is a JNI socket, to distinguish between
   * file-descriptors we have extra control over.
   */
  @Override
  public boolean isJni()
  {
    return true;
  }
  
  public boolean isJniValid()
  {
    return isEnabled();
  }

  @Override
  public boolean setSaveOnExec()
  {
    return nativeSetSaveOnExec(_fd);
  }

  @Override
  public int getSystemFD()
  {
    return nativeGetSystemFD(_fd);
  }

  /**
   * Sets the socket's listen backlog.
   */
  @Override
  public void listen(int backlog)
  {
    nativeListen(_fd, backlog);
  }

  public static ServerSocketBar create(String host, int port)
    throws IOException
  {
    _jniTroubleshoot.checkIsValid();

    return new JniServerSocketImpl(host, port);
  }

  public static ServerSocketBar open(int fd, int port)
    throws IOException
  {
    _jniTroubleshoot.checkIsValid();

    return new JniServerSocketImpl(fd, port, true);
  }
  
  /**
   * Creates a unix domain socket.
   */
  /*
  public static ServerSocketBar bindPath(PathImpl path)
    throws IOException
  {
    _jniTroubleshoot.checkIsValid();

    return new JniServerSocketImpl(path);
  }
  */

  /**
   * Sets the connection read timeout.
   */
  @Override
  public void setConnectionSocketTimeout(int ms)
  {
    _socketTimeout = ms;
    
    nativeSetConnectionSocketTimeout(_fd, ms);
  }

  /**
   * Sets the connection tcp-no-delay.
   */
  @Override
  public void setTcpNoDelay(boolean isEnable)
  {
    nativeSetTcpNoDelay(_fd, isEnable);
  }

  /**
   * Sets the connection tcp-no-delay.
   */
  @Override
  public boolean isTcpNoDelay()
  {
    return nativeIsTcpNoDelay(_fd);
  }

  /**
   * Sets the connection tcp-keepalive delay.
   */
  @Override
  public void setTcpKeepalive(boolean isEnable)
  {
    nativeSetTcpKeepalive(_fd, isEnable);
  }

  /**
   * Sets the connection tcp-keepalive delay.
   */
  @Override
  public boolean isTcpKeepalive()
  {
    return nativeIsTcpKeepalive(_fd);
  }

  /**
   * Sets the connection tcp-keepalive delay.
   */
  @Override
  public void setTcpCork(boolean isEnable)
  {
    if (_isCorkEnabled) {
      nativeSetTcpCork(_fd, isEnable);
    }
  }

  /**
   * Sets the connection tcp-keepalive delay.
   */
  @Override
  public boolean isTcpCork()
  {
    return nativeIsTcpCork(_fd);
  }

  /**
   * Accepts a new connection from the socket.
   *
   * @param socket the socket connection structure
   *
   * @return true if the accept returns a new socket.
   */
  @Override
  public boolean accept(SocketBar socket)
    throws IOException
  {
    JniSocketImpl jniSocket = (JniSocketImpl) socket;

    if (_fd == 0)
      throw new IOException("accept from closed socket");

    return jniSocket.accept(this, _fd, _socketTimeout);
  }

  /**
   * Factory method creating an instance socket.
   */
  @Override
  public SocketBar createSocket()
  {
    return new JniSocketImpl();
  }

  @Override
  public InetAddress getLocalAddress()
  {
    try {
      if (_host != null)
        return InetAddress.getByName(_host);
      else
        return InetAddress.getLocalHost();
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public int getLocalPort()
  {
    return nativeLocalPort(_fd);
  }

  public boolean isClosed()
  {
    return _fd == 0;
  }

  /**
   * Closes the socket.
   */
  @Override
  public void close()
    throws IOException
  {
    long fd;

    synchronized (this) {
      fd = _fd;
      _fd = 0;
    }
    
    if (fd != 0) {
      closeNative(fd);
    }
  }

  public String toString()
  {
    return getClass().getSimpleName() + "[" + _id + "]";
  }

  @Override
  public void finalize()
    throws Throwable
  {
    try {
      close();
    } catch (Throwable e) {
    }
    
    super.finalize();
  }

  /**
   * Binds the port.
   */
  static native long bindPort(String ip, int port);

  /**
   * Binds the port.
   */
  static native long bindUnix(String path);

  /**
   * Open the port.
   */
  static native long nativeOpenPort(int fd, int port);

  /**
   * Sets the connection read timeout.
   */
  native void nativeSetConnectionSocketTimeout(long fd, int timeout);

  /**
   * Sets the connection tcp-no-delay
   */
  native void nativeSetTcpNoDelay(long fd, boolean isEnable);

  /**
   * Sets the connection tcp-no-delay
   */
  native boolean nativeIsTcpNoDelay(long fd);

  /**
   * Sets the connection tcp-keepalive
   */
  native void nativeSetTcpKeepalive(long fd, boolean isEnable);

  /**
   * Sets the connection tcp-keepalive
   */
  native boolean nativeIsTcpKeepalive(long fd);

  /**
   * Sets the connection tcp-cork
   */
  native void nativeSetTcpCork(long fd, boolean isEnable);

  /**
   * Sets the connection tcp-cork
   */
  native boolean nativeIsTcpCork(long fd);
  
  /**
   * Sets the listen backlog
   */
  native void nativeListen(long fd, int listen);

  /**
   * Returns the server's local port.
   */
  private native int nativeLocalPort(long fd);

  /**
   * Returns the OS file descriptor.
   */
  private native int nativeGetSystemFD(long fd);

  /**
   * Save across an exec.
   */
  private native boolean nativeSetSaveOnExec(long fd);

  /**
   * True if sendfile is available.
   */
  private static native boolean nativeIsSendfileEnabled();

  /**
   * True if cork is available.
   */
  private static native boolean nativeIsCorkEnabled();

  /**
   * Closes the server socket.
   */
  native int closeNative(long fd)
    throws IOException;

  static {
    boolean isSendfileEnabled = false;
    boolean isCorkEnabled = false;

    _jniTroubleshoot
    = JniUtil.load(JniServerSocketImpl.class,
                   new JniLoad() { 
                     public void load(String path) { System.load(path); }},
                   "baratine");

    JniUtil.acquire();
    try {
      isSendfileEnabled = nativeIsSendfileEnabled(); 
      isCorkEnabled = nativeIsCorkEnabled();
    } catch (Throwable e) {
      log.finer(e.toString());
      log.log(Level.FINEST, e.toString(), e);
    } finally {
      JniUtil.release();
    }

    _isSendfileEnabled = isSendfileEnabled;
    _isCorkEnabled = isCorkEnabled;
  }
}

