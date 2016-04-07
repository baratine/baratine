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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import com.caucho.v5.io.ReadBuffer;
import com.caucho.v5.io.SocketBar;
import com.caucho.v5.io.WriteBuffer;


/**
 * Proxy interface for the tcp connection.
 */
public interface ConnectionTcpApi
{
  /**
   * Returns the connection id.  Primarily for debugging.
   */
  long id();

  /**
   * Returns the connection's buffered read stream.
   */
  ReadBuffer readStream();

  /**
   * Returns the connection's buffered write stream. 
   */
  WriteBuffer writeStream();

  /**
   * Returns true if secure (ssl)
   */
  boolean isSecure();
  
  StateConnection getState();
  
  /**
   * Returns the static configured virtual host
   */
  String getVirtualHost();
  
  /**
   * Returns the local address of the connection
   */
  InetAddress getLocalAddress();
  
  /**
   * Returns the local address of the connection
   */
  InetSocketAddress ipLocal();
  
  /**
   * Returns the local host of the connection
   */
  String getLocalHost();

  /**
   * Returns the local port of the connection
   */
  int getLocalPort();

  /**
   * Returns the remote address of the connection
   */
  InetAddress getRemoteAddress();

  /**
   * Returns the remote client's inet address.
   */
  String ip();

  /**
   * Returns the remote address of the connection
   */
  int getRemoteAddress(byte []buffer, int offset, int length);

  /**
   * Returns the remove port of the connection
   */
  int getRemotePort();
  
  //
  // SSL-related information
  //
  
  /**
   * Returns the security protocol
   */
  String secureProtocol();
  
  /**
   * Returns the cipher suite
   */
  String cipherSuite();
  
  /***
   * Returns the key size.
   */
  //int keySize();
  
  /**
   * Returns any client certificates.
   * @throws CertificateException 
   */
  X509Certificate []clientCertificates()
    throws CertificateException;
  
  //
  // checks for Port enable/disable
  //

  SocketBar socket();
  
  PortSocket port();
  
  boolean isPortActive();

  long idleStartTime();
  long idleExpireTime();
   
  void clientDisconnect();

  void disconnect();

  String getStateName();

  //boolean isWake();

  long getIdleTimeout();
  void setIdleTimeout(long timeout);

  ConnectionTcpProxy proxy();

  int fillWithTimeout(long timeout) throws IOException;
}
