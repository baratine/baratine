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
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import com.caucho.v5.util.ModulePrivate;

/**
 * Abstract socket to handle both normal sockets and jni sockets.
 */
@ModulePrivate
public class SocketChannelWrapperBar extends SocketBar
{
  private static final Logger log
    = Logger.getLogger(SocketChannelWrapperBar.class.getName());
  
  private static HashMap<String,Integer> _sslKeySizes;
  
  private SocketChannel _channel;
  private SSLSocket _sslSocket;
  
  private SocketChannelStream _channelStream;
  private SocketStream _sslStream;
  
  private StreamImpl _streamImpl;
  
  private boolean _isWriteClosed;

  public SocketChannelWrapperBar()
  {
  }

  public SocketChannelWrapperBar(SocketChannel s)
  {
    init(s);
  }

  public void init(SocketChannel s)
  {
    _channel = s;
    _sslSocket = null;
    _streamImpl = null;
  }
  
  @Override
  public Socket getSocket()
  {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public void setSocket(Socket s)
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Accepts a new socket.
   */
  @Override
  public int acceptInitialRead(byte []buffer, int offset, int length)
    throws IOException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Sets the socket timeout.
   */
  public void setReadTimeout(int ms)
    throws IOException
  {
    //_s.setOption(StandardSocketOptionsSocketOption<T>.class;
  }

  @Override
  public void setSoTimeout(long ms)
    throws SocketException
  {
    //_s.setSoTimeout((int) ms);
  }

  @Override
  public long getSoTimeout()
  {
    /*
    try {
      return _s.getSoTimeout();
    } catch (SocketException e) {
      log.log(Level.FINEST, e.toString(), e);
      
      return -1;
    }
    */
    return -1;
  }
  
  @Override
  public void tcpNoDelay(boolean isNoDelay)
    throws SocketException
  {
    try {
      _channel.setOption(StandardSocketOptions.TCP_NODELAY, (Boolean) isNoDelay);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Initialize the ssl
   */
  @Override
  public void ssl(SSLFactory sslFactory)
  {
    try {
      Objects.requireNonNull(sslFactory);
    
      SocketChannel channel = _channel;
      Objects.requireNonNull(channel);
    
      _sslSocket = sslFactory.ssl(channel);
      _sslSocket.startHandshake();
    } catch (IOException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the server inet address that accepted the request.
   */
  @Override
  public InetAddress addressLocal()
  {
    SocketChannel s = _channel;
    
    if (s != null) {
      try {
        InetSocketAddress addr = (InetSocketAddress) s.getLocalAddress();
        
        return addr.getAddress();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      return null;
    }
  }

  /**
   * Returns the server inet address that accepted the request.
   */
  @Override
  public InetSocketAddress ipLocal()
  {
    SocketChannel s = _channel;
    
    if (s != null) {
      try {
        return (InetSocketAddress) s.getLocalAddress();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      return null;
    }
  }

  /**
   * Returns the server inet address that accepted the request.
   */
  @Override
  public InetSocketAddress ipRemote()
  {
    SocketChannel s = _channel;
    
    if (s != null) {
      try {
        return (InetSocketAddress) s.getRemoteAddress();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      return null;
    }
  }
  
  /**
   * Returns the server port that accepted the request.
   */
  @Override
  public int portLocal()
  {
    SocketChannel s = _channel;
    
    if (s != null) {
      try {
        InetSocketAddress addr = (InetSocketAddress) s.getLocalAddress();
        
        return addr.getPort();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      return -1;
    }
  }

  /**
   * Returns the remote client's inet address.
   */
  @Override
  public InetAddress addressRemote()
  {
    SocketChannel s = _channel;
    
    if (s != null) {
      try {
        InetSocketAddress addr = (InetSocketAddress) s.getRemoteAddress();
        
        return addr.getAddress();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      return null;
    }
  }
  
  /**
   * Returns the remote client's port.
   */
  @Override
  public int portRemote()
  {
    if (_channel != null) {
      try {
        SocketAddress addr = _channel.getRemoteAddress();
        
        return 0;
      } catch (Exception e) {
        e.printStackTrace();
        return 0;
      }
    }
    else
      return 0;
  }

  /**
   * Returns true if the connection is secure.
   */
  @Override
  public boolean isSecure()
  {
    return _sslSocket != null;
  }
  
  /**
   * Returns the secure cipher algorithm.
   */
  @Override
  public String cipherSuite()
  {
    SSLSocket sslSocket = _sslSocket;
    
    if (sslSocket == null) {
      return super.cipherSuite();
    }
    
    SSLSession sslSession = sslSocket.getSession();
    
    if (sslSession != null) {
      return sslSession.getCipherSuite();
    }
    else {
      return null;
    }
  }
  
  /**
   * Returns the secure cipher algorithm.
   */
  @Override
  public String secureProtocol()
  {
    SSLSocket sslSocket = _sslSocket;
    
    if (sslSocket == null) {
      return super.secureProtocol();
    }
    
    SSLSession sslSession = sslSocket.getSession();
    
    if (sslSession != null) {
      return sslSession.getProtocol();
    }
    else {
      return null;
    }
  }

  /**
   * Returns the client certificate.
   */
  @Override
  public X509Certificate []getClientCertificates()
    throws CertificateException
  {
    SSLSocket sslSocket = _sslSocket;
    
    if (sslSocket == null) {
      return null;
    }
    
    SSLSession sslSession = sslSocket.getSession();
    if (sslSession == null)
      return null;

    try {
      return (X509Certificate []) sslSession.getPeerCertificates();
    } catch (SSLPeerUnverifiedException e) {
      if (log.isLoggable(Level.FINEST))
        log.log(Level.FINEST, e.toString(), e);
      
      return null;
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);
    }
    
    return null;
  }

  /**
   * Returns the selectable channel.
   */
  @Override
  public SelectableChannel selectableChannel()
  {
    return _channel;
  }
  
  /**
   * Returns the socket's input stream.
   */
  @Override
  public StreamImpl stream()
    throws IOException
  {
    if (_channelStream == null) {
      _channelStream = new SocketChannelStream();
    }
    
    if (_sslSocket == null) {
      _channelStream.init(_channel);
      _streamImpl = _channelStream;
    }
    else {
      if (_sslStream == null) {
        _sslStream = new SocketStream();
      }
      
      _sslStream.init(_sslSocket);
      _streamImpl = _sslStream;
    }

    return _streamImpl;
  }
  
  public void resetTotalBytes()
  {
    if (_channelStream != null) {
      _channelStream.resetTotalBytes();
    }
  }

  @Override
  public long getTotalReadBytes()
  {
    return (_channelStream == null) ? 0 : _channelStream.getTotalReadBytes();
  }

  @Override
  public long getTotalWriteBytes()
  {
    return (_channelStream == null) ? 0 : _channelStream.getTotalWriteBytes();
  }

  /**
   * Returns true for closes.
   */
  @Override
  public boolean isClosed()
  {
    return _channel == null;
  }

  /**
   * Closes the underlying socket.
   */
  @Override
  public void close()
    throws IOException
  {
    SocketChannel s = _channel;
    _channel = null;
    
    SSLSocket sslSocket = _sslSocket;
    _sslSocket = null;

    if (sslSocket != null) {
      try {
        sslSocket.close();
      } catch (Exception e) {
      }
    }

    if (s != null) {
      try {
        s.close();
      } catch (Exception e) {
      }
    }
  }

  /**
   * Closes the write half of the stream.
   */
  @Override
  public void closeWrite() throws IOException
  {
    if (_isWriteClosed) {
      return;
    }
    
    _isWriteClosed = true;
    
    StreamImpl stream = _streamImpl;
    
    if (stream != null) {
      stream.closeWrite();
    }
    else if (_sslSocket != null) {
      _sslSocket.getOutputStream().close();
    }
    else if (_channel != null) {
      try {
        _channel.shutdownOutput();
      } catch (UnsupportedOperationException e) {
        log.log(Level.FINEST, e.toString(), e);
      } catch (Exception e) {
        log.finer(e.toString());
        log.log(Level.FINEST, e.toString(), e);
      }
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _channel + "]";
  }

  static {
    _sslKeySizes = new HashMap<>();
    _sslKeySizes.put("SSL_DH_anon_WITH_DES_CBC_SHA", 56);
    _sslKeySizes.put("SSL_DH_anon_WITH_3DES_EDE_CBC_SHA", 168);
    _sslKeySizes.put("SSL_DH_anon_WITH_RC4_128_MD5", 128);
    _sslKeySizes.put("SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA", 40);
    _sslKeySizes.put("SSL_DH_anon_EXPORT_WITH_RC4_40_MD5", 40);
    _sslKeySizes.put("SSL_DHE_DSS_WITH_DES_CBC_SHA", 56);
    _sslKeySizes.put("SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA", 40);
    _sslKeySizes.put("SSL_RSA_WITH_RC4_128_MD5", 128);
    _sslKeySizes.put("SSL_RSA_WITH_RC4_128_SHA", 128);
    _sslKeySizes.put("SSL_RSA_WITH_DES_CBC_SHA", 56);
    _sslKeySizes.put("SSL_RSA_WITH_3DES_EDE_CBC_SHA", 168);
    _sslKeySizes.put("SSL_RSA_EXPORT_WITH_RC4_40_MD5", 40);
    _sslKeySizes.put("SSL_RSA_WITH_NULL_MD5", 0);
    _sslKeySizes.put("SSL_RSA_WITH_NULL_SHA", 0);
    _sslKeySizes.put("SSL_DSA_WITH_RC4_128_MD5", 128);
    _sslKeySizes.put("SSL_DSA_WITH_RC4_128_SHA", 128);
    _sslKeySizes.put("SSL_DSA_WITH_DES_CBC_SHA", 56);
    _sslKeySizes.put("SSL_DSA_WITH_3DES_EDE_CBC_SHA", 168);
    _sslKeySizes.put("SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA", 168);
    _sslKeySizes.put("SSL_DSA_EXPORT_WITH_RC4_40_MD5", 40);
    _sslKeySizes.put("SSL_DSA_WITH_NULL_MD5", 0);
    _sslKeySizes.put("SSL_DSA_WITH_NULL_SHA", 0);
  } 
}

