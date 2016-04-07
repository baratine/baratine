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

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.SelectableChannel;
import java.security.cert.X509Certificate;

import com.caucho.v5.util.ModulePrivate;

/**
 * Abstract socket to handle both normal sockets and JNI sockets.
 */
@ModulePrivate
abstract public class SocketBar implements Closeable 
{
  abstract public int acceptInitialRead(byte []buffer, int offset, int length)
    throws IOException;
  
  /**
   * Returns the server inet address that accepted the request.
   */
  abstract public InetAddress addressLocal();
  
  /**
   * Returns the server inet address that accepted the request.
   */
  abstract public InetSocketAddress ipLocal();
  
  /**
   * Returns the server inet address that accepted the request.
   */
  public String getLocalHost()
  {
    InetAddress localAddress = addressLocal();
    
    if (localAddress != null)
      return localAddress.getHostAddress();
    else
      return null;
  }

 
  /**
   * Returns the server port that accepted the request.
   */
  abstract public int portLocal();

  /**
   * Returns the remote client's inet address.
   */
  abstract public InetAddress addressRemote();

  /**
   * Returns the remote client's inet address.
   */
  public String getRemoteHost()
  {
    InetAddress remoteAddress = addressRemote();
    
    if (remoteAddress != null)
      return remoteAddress.getHostAddress();
    else
      return null;
  }

  /**
   * Returns the remote client's inet address.
   */
  public int getRemoteAddress(byte []buffer, int offset, int length)
  {
    String name = getRemoteHost();
    int len = name.length();

    for (int i = 0; i < len; i++)
      buffer[i + offset] = (byte) name.charAt(i);

    return len;
  }

  public Socket getSocket()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void setSocket(Socket socket)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  /**
   * Returns the TLS negotiated protocol, if available.
   */
  public String getNegotiatedProtocol()
  {
    return null;
  }

  /**
   * Returns the remote client's inet address.
   */
  public byte[] getRemoteIP()
  {
    InetAddress addr = addressRemote();
    return addr.getAddress();
  }
  
  /**
   * Returns the remote client's port.
   */
  abstract public int portRemote();

  public void setTcpNoDelay(boolean value)
    throws SocketException
  {
  }

  public void setSoTimeout(long ms)
    throws SocketException
  {
  }

  public long getSoTimeout()
  {
    return -1;
  }

  public void ssl(SSLFactory sslFactory)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Returns true if the connection is secure.
   */
  public boolean isSecure()
  {
    return false;
  }

  /**
   * Returns any selectable channel.
   */
  public SelectableChannel selectableChannel()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Returns the secure cipher algorithm.
   */
  public String cipherSuite()
  {
    return null;
  }

  /**
   * Returns the secure cipher algorithm.
   */
  public String secureProtocol()
  {
    return null;
  }

  /**
   * Returns the bits in the socket.
   */
  public int cipherBits()
  {
    return 0;
  }

  /**
   * Returns the client certificate.
   */
  public X509Certificate getClientCertificate()
    throws java.security.cert.CertificateException
  {
    return null;
  }

  /**
   * Returns the client certificate chain.
   */
  public X509Certificate []getClientCertificates()
    throws java.security.cert.CertificateException
  {
    X509Certificate cert = getClientCertificate();

    if (cert != null)
      return new X509Certificate[] { cert };
    else
      return null;
  }
  
  public void setRequestExpireTime(long expireTime)
  {
  }
  
  public boolean isEof()
    throws IOException
  {
    return true;
  }
  /**
   * Returns a stream impl for the socket encapsulating the
   * input and output stream.
   */
  abstract public StreamImpl stream()
    throws IOException;

  public ReadBuffer getInputStream()
    throws IOException
  {
    StreamImpl stream = stream();
    
    if (stream != null) {
      return new ReadBuffer(stream);
    }
    else {
      return null;
    }
  }

  public WriteBuffer getOutputStream()
    throws IOException
  {
    StreamImpl stream = stream();
    
    if (stream != null) {
      return new WriteBuffer(stream);
    }
    else {
      return null;
    }
  }

  /**
   * Returns the total number of bytes read from the socket connection.
   */
  abstract public long getTotalReadBytes();

  /**
   * Returns the total number of bytes written to the socket connection.
   */
  abstract public long getTotalWriteBytes();

  /**
   * returns true if it's closed.
   */
  abstract public boolean isClosed();

  public void forceShutdown()
  {
  }
  
  public void closeWrite()
    throws IOException
  {
    close();
  }
  
  abstract public void close()
    throws IOException;
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
            + "[" + addressLocal() + ":" + portLocal() + "]");
  }
}

