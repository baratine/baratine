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

package com.caucho.v5.http.protocol;

import com.caucho.v5.util.CharBuffer;

/**
 * The invocation key is a <host, port, url> triple
 */
public class InvocationKey {
  private boolean _isSecure;
  
  private CharSequence _host;
  private int _port;
  
  private byte []_uri;
  private int _uriLength;

  /**
   * Create an empty invocation key.
   */
  public InvocationKey()
  {
  }

  /**
   * Create a new invocation key with the given initial values.  The
   * values are copied.
   *
   * @param host the request's host
   * @param uri the raw byte array containing the uri
   * @param urlLength the length of the uri in the byte array
   * @param port the request's port
   */
  InvocationKey(boolean isSecure,
                CharSequence host, int port,
                byte []uri, int uriLength)
  {
    _isSecure = isSecure;
    
    if (host != null) {
      CharBuffer cb = new CharBuffer();
      cb.append(host);
      _host = cb;
    }
    _port = port;
    
    _uri = new byte[uriLength];
    System.arraycopy(uri, 0, _uri, 0, uriLength);
    _uriLength = uriLength;
  }

  /**
   * Initialize the InvocationKey with a new triple.
   *
   * @param host the request's host
   * @param port the request's port
   * @param uri the raw byte array containing the uri
   * @param urlLength the length of the uri in the byte array
   */
  public void init(boolean isSecure,
                   CharSequence host, int port,
                   byte []uri, int uriLength)
  {
    _isSecure = isSecure;
    
    _host = host;
    _port = port;
    
    _uri = uri;
    _uriLength = uriLength;
  }

  public boolean isSecure()
  {
    return _isSecure;
  }

  /**
   * Returns the InvocationKey's host.
   */
  public CharSequence getHost()
  {
    return _host;
  }

  /**
   * Sets the InvocationKey's host.
   */
  public void setHost(CharSequence host)
  {
    _host = host;
  }

  /**
   * Returns the InvocationKey's port
   */
  public int getPort()
  {
    return _port;
  }

  /**
   * Sets the InvocationKey's port.
   */
  public void setPort(int port)
  {
    _port = port;
  }

  /**
   * Returns the raw byte array containing the uri
   */
  public byte []getUriBuffer()
  {
    return _uri;
  }

  /**
   * Returns the length of the uri in the byte array.
   */
  public int getUriLength()
  {
    return _uriLength;
  }

  /**
   * Returns a hash code of the key.
   */
  @Override
  public int hashCode()
  {
    int hash = _port + (_isSecure ? 65521 : 31);
    byte []uri = _uri;
    int length = _uriLength;

    for (int i = length - 1; i >= 0; i--) {
      hash = 65521 * hash + uri[i];
    }

    CharSequence host = _host;
    if (host != null) {
      hash = 65521 * hash + host.hashCode();
    }

    return hash;
  }

  /**
   * Returns true if the key matches the test key.
   */
  public boolean equals(Object b)
  {
    if (! (b instanceof InvocationKey))
      return false;
    
    InvocationKey test = (InvocationKey) b;

    if (_isSecure != test._isSecure)
      return false;

    if (_port != test._port)
      return false;
      
    int length = _uriLength;

    if (length != test._uriLength)
      return false;
      
    byte []uriA = _uri;
    byte []uriB = test._uri;

    for (int i = length - 1; i >= 0; i--)
      if (uriA[i] != uriB[i])
        return false;

    if (_host == null)
      return test._host == null;
    else
      return _host.equals(test._host);
  }

  public Object clone()
  {
    return new InvocationKey(_isSecure, _host, _port, _uri, _uriLength);
  }

  /**
   * Returns a printable representation of the key.
   */
  public String toString()
  {
    if (_host == null)
      return "InvocationKey[" + new String(_uri, 0, _uriLength) + "]";
    else
      return ("InvocationKey[host=" + _host + ",port=" + _port +
              ",uri=" + new String(_uri, 0, _uriLength) + "]");
  }
}
