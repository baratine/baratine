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
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.caucho.v5.jni.ServerSocketJni;
import com.caucho.v5.jni.SocketSystemJniTcp;

/**
 * Standard TCP network system.
 */
public class SocketSystemTcp extends SocketSystem
{
  private static final Logger log = Logger.getLogger(SocketSystemTcp.class.getName());

  protected SocketSystemTcp()
  {
  }

  public static SocketSystem create()
  {
    try {
      return new SocketSystemJniTcp();
    } catch (Exception e) {
      log.fine(e.toString());
    }

    return new SocketSystemTcp();
  }

  @Override
  public ServerSocketBar openServerSocket(InetAddress address,
                                        int port,
                                        int backlog,
                                        boolean isJni)
    throws IOException
  {
    return ServerSocketJni.create(address, port, backlog, isJni);
  }
  
  @Override
  public SocketBar createSocket()
  {
    return new SocketWrapperBar();
  }
  
  @Override
  public SocketBarBuilder connect()
  {
    return new SocketBarBuilderTcp();
  }

  @Override
  public SocketBar connect(SocketBar socket,
                           InetSocketAddress addressRemote,
                           InetSocketAddress addressLocal,
                           long connectTimeout,
                           boolean isSSL)
    throws IOException
  {
//    throw new UnsupportedOperationException();

    Socket s;
    
    s = new Socket();

    if (connectTimeout > 0)
      s.connect(addressRemote, (int) connectTimeout);
    else
      s.connect(addressRemote);

    if (isSSL) {
      throw new UnsupportedOperationException();
      //s = connectSSL(s, addressRemote);
    }
    
    if (! s.isConnected()) {
      throw new IOException("connection timeout");
    }

    return new SocketWrapperBar(s);

  }
  
  private Socket connectSSL(Socket s, SocketBarBuilderTcp builder)
    throws IOException
  {
    try {
      SSLContext context = SSLContext.getInstance("TLS");

      TrustManager tm = new NullTrustManager();

      context.init(null, new TrustManager[] { tm }, null);
      SSLSocketFactory factory = context.getSocketFactory();
      
      InetSocketAddress addr = builder.address();
      
      s = factory.createSocket(s, 
                               addr.getAddress().getHostAddress(), 
                               addr.getPort(), 
                               true);

      SSLSocket sslSocket = (SSLSocket) s;
      sslSocket.setUseClientMode(true);
      
      if (builder.sslProtocols() != null) {
        sslSocket.setEnabledProtocols(builder.sslProtocols());
      }

      sslSocket.startHandshake();

      return s;
    } catch (IOException | RuntimeException e) {
      e.printStackTrace();
      throw e;
    } catch (Exception e) {
      e.printStackTrace();
      throw new IOException(e);
    }
    
  }
  
  private static class NullTrustManager implements X509TrustManager
  {
    @Override
    public X509Certificate[] getAcceptedIssuers()
    {
      return null;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] cert, String foo)
    {
    }
      
    @Override
    public void checkServerTrusted(X509Certificate[] cert, String foo)
    {
    }
  }

  public class SocketBarBuilderTcp implements SocketBarBuilder
  {
    private SocketBar _socket;
    private InetSocketAddress _address;
    private InetSocketAddress _addressLocal;
    private long _timeoutConnect;
    private boolean _isSsl;
    private int _port;
    private String []_sslProtocols;

    @Override
    public SocketBarBuilder socket(SocketBar socket)
    {
      Objects.requireNonNull(socket);
      
      _socket = socket;
      
      return this;
    }

    /*
    @Override
    public SocketBarBuilder address(String address)
    {
      throw new UnsupportedOperationException(address);
    }
    */

    @Override
    public SocketBarBuilder address(InetSocketAddress address)
    {
      Objects.requireNonNull(address);
      
      _address = address;

      return this;
    }
    
    InetSocketAddress address()
    {
      return _address;
    }

    @Override
    public SocketBarBuilder addressLocal(InetSocketAddress address)
    {
      _addressLocal = address;

      return this;
    }
    
    InetSocketAddress addressLocal()
    {
      return _addressLocal;
    }

    /*
    @Override
    public SocketBarBuilder port(int port)
    {
      _port = port;

      return this;
    }
    */

    @Override
    public SocketBarBuilder timeoutConnect(long timeout)
    {
      _timeoutConnect = timeout;
      
      return this;
    }

    @Override
    public SocketBarBuilder ssl(boolean isSsl)
    {
      _isSsl = isSsl;

      return this;
    }

    @Override
    public SocketBarBuilder sslProtocols(String ...protocols)
    {
      Objects.requireNonNull(protocols);
      
      _sslProtocols = protocols;

      return this;
    }

    public String []sslProtocols()
    {
      return _sslProtocols;
    }
    
    @Override
    public SocketBar get()
      throws IOException
    {
      Socket s;
    
      s = new Socket();

      if (_timeoutConnect > 0)
        s.connect(_address, (int) _timeoutConnect);
      else
        s.connect(_address);

      if (_isSsl) {
        s = connectSSL(s, this);
      }
    
      if (! s.isConnected()) {
        throw new IOException("connection timeout");
      }

      return new SocketWrapperBar(s);
    }
  }
}

