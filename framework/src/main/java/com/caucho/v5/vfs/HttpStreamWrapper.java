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

package com.caucho.v5.vfs;

import java.io.IOException;
import java.net.SocketException;
import java.util.Iterator;
import java.util.logging.Logger;

import com.caucho.v5.io.StreamImpl;

/**
 * Facade to HttpStream to properly handle the close.
 */
public class HttpStreamWrapper extends StreamImpl
{
  private static final Logger log
    = Logger.getLogger(HttpStream.class.getName());
  
  private HttpStream _stream;

  /**
   * Create a new HTTP stream.
   */
  HttpStreamWrapper(HttpStream stream)
    throws IOException
  {
    _stream = stream;
  }

  /**
   * Set if this should be an SSL connection.
   */
  public void setSSL(boolean isSSL)
  {
    _stream.setSSL(isSSL);
  }

  /**
   * Set if this should be an SSL connection.
   */
  public boolean isSSL()
  {
    return _stream.isSSL();
  }

  /**
   * Sets the method
   */
  public void setMethod(String method)
  {
    _stream.setMethod(method);
  }

  /**
   * Sets true if we're only interested in the head.
   */
  public void setHead(boolean isHead)
  {
    _stream.setHead(isHead);
  }

  /**
   * Returns the stream's host.
   */
  public String getHost()
  {
    return _stream.getHost();
  }

  /**
   * Returns the stream's port.
   */
  public int getPort()
  {
    return _stream.getPort();
  }
  
  /**
   * Sets the http version.
   */
  public void setHttp10()
  {
    _stream.setHttp10();
  }
  
  /**
   * Sets the http version.
   */
  public void setHttp11()
  {
    _stream.setHttp11();
  }
  
  /**
   * Returns a header from the response returned from the HTTP server.
   *
   * @param name name of the header
   * @return the header value.
   */
  public Object getAttribute(String name)
    throws IOException
  {
    if (_stream != null)
      return _stream.getAttribute(name);
    else
      return null;
  }

  /**
   * Returns an iterator of the returned header names.
   */
  public Iterator getAttributeNames()
    throws IOException
  {
    if (_stream != null)
      return _stream.getAttributeNames();
    else
      return null;
  }

  /**
   * Sets a header for the request.
   */
  public void setAttribute(String name, Object value)
  {
    if (_stream != null) {
      _stream.setAttribute(name, value);
    }
  }

  /**
   * Remove a header for the request.
   */
  public void removeAttribute(String name)
  {
    if (_stream != null)
      _stream.removeAttribute(name);
  }

  /**
   * Sets the timeout.
   */
  public void setSocketTimeout(long timeout)
    throws SocketException
  {
    if (_stream != null)
      _stream.setSocketTimeout(timeout);
  }

  /**
   * The stream is always writable (?)
   */
  public boolean canWrite()
  {
    if (_stream != null)
      return _stream.canWrite();
    else
      return false;
  }

  /**
   * Writes a buffer to the underlying stream.
   *
   * @param buffer the byte array to write.
   * @param offset the offset into the byte array.
   * @param length the number of bytes to write.
   * @param isEnd true when the write is flushing a close.
   */
  public void write(byte []buf, int offset, int length, boolean isEnd)
    throws IOException
  {
    if (_stream != null)
      _stream.write(buf, offset, length, isEnd);
  }

  /**
   * The stream is readable.
   */
  public boolean canRead()
  {
    if (_stream != null)
      return _stream.canRead();
    else
      return false;
  }

  /**
   * Read data from the connection.  If the request hasn't yet been sent
   * to the server, send it.
   */
  public int read(byte []buf, int offset, int length) throws IOException
  {
    if (_stream != null)
      return _stream.read(buf, offset, length);
    else
      return -1;
  }

  /**
   * Returns the bytes still available.
   */
  public int getAvailable() throws IOException
  {
    if (_stream != null)
      return _stream.getAvailable();
    else
      return -1;
  }

  /**
   * Close the connection.
   */
  public void close() throws IOException
  {
    HttpStream stream = _stream;
    _stream = null;

    if (stream != null)
      stream.close();
  }
}
