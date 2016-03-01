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

package com.caucho.v5.vfs;

import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.util.CharBuffer;
import com.caucho.v5.util.CurrentTime;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;

/**
 * Implements a tcp stream, essentially just a socket pair.
 */
public class TcpPath extends PathImpl {
  // Attribute name for connection timeouts
  public static final String CONNECT_TIMEOUT = "connect-timeout";
  
  private static final long SOCKET_ADDRESS_TIMEOUT = 120 * 1000L;

  private String _host;
  private int _port;
  private SocketAddress _address;
  private long _connectTimeout = 5000L;
  private long _socketTimeout = 600000L;
  private boolean _noDelay;
  
  private long _socketAddressExpireTime = 0;

  public TcpPath(TcpPath root, String userPath,
                 Map<String,Object> newAttributes,
                 String host, int port)
  {
    super(root);

    setUserPath(userPath);

    _host = host;
    _port = port == 0 ? 80 : port;

    if (newAttributes != null) {
      Object connTimeout = newAttributes.get("connect-timeout");

      if (connTimeout instanceof Number)
        _connectTimeout = ((Number) connTimeout).longValue();
      
      Object socketTimeout = newAttributes.get("socket-timeout");

      if (socketTimeout == null)
        socketTimeout = newAttributes.get("timeout");

      if (socketTimeout instanceof Number)
        _socketTimeout = ((Number) socketTimeout).longValue();
      
      if (Boolean.TRUE.equals(newAttributes.get("no-delay"))) {
        _noDelay = true;
      }
    }
  }

  /**
   * Lookup the new path assuming we're the scheme root.
   */
  @Override
  public PathImpl schemeWalk(String userPath,
                         Map<String,Object> newAttributes,
                         String uri,
                         int offset)
  {
    int length = uri.length();

    if (length < 2 + offset
        || uri.charAt(offset) != '/'
        || uri.charAt(1 + offset) != '/')
      throw new RuntimeException("bad scheme");

    CharBuffer buf = new CharBuffer();
    int i = 2 + offset;
    int ch = 0;
    boolean isIpv6 = false;
    
    for (; 
          (i < length
           && (ch = uri.charAt(i)) != '/'
           && ch != '?'
           && ! (ch == ':' && ! isIpv6));
         i++) {
      if (ch == '[')
        isIpv6 = true;
      else if (ch == ']')
        isIpv6 = false;
      
      buf.append((char) ch);
    }

    String host = buf.toString();
    if (host.length() == 0)
      throw new RuntimeException("bad host");

    int port = 0;
    if (ch == ':') {
      for (i++; i < length && (ch = uri.charAt(i)) >= '0' && ch <= '9'; i++) {
        port = 10 * port + uri.charAt(i) - '0';
      }
    }

    return create(this, userPath, newAttributes, host, port);
  }

  protected TcpPath create(TcpPath root,
                           String userPath, Map<String,Object> newAttributes,
                           String host, int port)
  {
    return new TcpPath(root, userPath, newAttributes, host, port);
  }

  @Override
  public String getScheme()
  {
    return "tcp";
  }

  @Override
  public String getURL()
  {
    return (getScheme() + "://" + getHost() + ":" + getPort());
  }

  @Override
  public String getPath()
  {
    return "";
  }

  @Override
  public String getHost()
  {
    return _host;
  }

  @Override
  public int getPort()
  {
    return _port;
  }

  public SocketAddress getSocketAddress()
  {
    long now = CurrentTime.getCurrentTime();
    
    if (_address == null || _socketAddressExpireTime < now) {
      _socketAddressExpireTime = now + SOCKET_ADDRESS_TIMEOUT;
      _address = new InetSocketAddress(_host, _port);
    }

    return _address;
  }
  
  private void clearSocketAddress()
  {
    _socketAddressExpireTime = 0;
  }

  @Override
  public StreamImpl openReadImpl() throws IOException
  {
    boolean isValid = false;
    
    try {
      StreamImpl stream = TcpStream.openRead(this, _connectTimeout, _socketTimeout, _noDelay);
      
      isValid = true;
      
      return stream;
    } finally {
      if (! isValid) {
        clearSocketAddress();
      }
    }
  }

  @Override
  public StreamImpl openReadWriteImpl() throws IOException
  {
    boolean isValid = false;
    
    try {
      StreamImpl stream = TcpStream.openReadWrite(this, _connectTimeout, _socketTimeout, _noDelay);
      
      isValid = true;
      
      return stream;
    } finally {
      if (! isValid) {
        clearSocketAddress();
      }
    }
  }

  @Override
  protected PathImpl cacheCopy()
  {
    return new TcpPath(null, getUserPath(), null, _host, _port);
  }

  @Override
  public String toString()
  {
    return getURL();
  }
}
