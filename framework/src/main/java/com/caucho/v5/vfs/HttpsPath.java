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

import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.util.L10N;

import java.io.IOException;
import java.util.Map;

/**
 * The HTTP scheme.  Currently it supports GET and POST.
 *
 * <p>TODO: support WEBDAV, enabling the full Path API.
 */
public class HttpsPath extends HttpPath {
  protected static L10N L = new L10N(HttpsPath.class);

  /**
   * Creates a new HTTP root path with a host and a port.
   *
   * @param host the target host
   * @param port the target port, if zero, uses port 80.
   */
  public HttpsPath(String host, int port)
  {
    super(host, port);
  }

  /**
   * Creates a new HTTP sub path.
   *
   * @param root the HTTP filesystem root
   * @param userPath the argument to the calling lookup()
   * @param newAttributes any attributes passed to http
   * @param path the full normalized path
   * @param query any query string
   */
  public HttpsPath(FilesystemPath root, String userPath,
                   Map<String,Object> newAttributes,
                   String path, String query)
  {
    super(root, userPath, newAttributes, path, query);
  }

  protected HttpPath create(String host, int port)
  {
    return new HttpsPath(host, port);
  }

  protected HttpPath create(FilesystemPath root,
                            String userPath,
                            Map<String,Object> newAttributes,
                            String path, String query)
  {
    return new HttpsPath(root, userPath, newAttributes, path, query);
  }

  /**
   * Returns the scheme, http.
   */
  public String getScheme()
  {
    return "https";
  }

  /**
   * Returns a read stream for a GET request.
   */
  public StreamImpl openReadImpl() throws IOException
  {
    HttpStreamWrapper stream = HttpStream.openRead(this);

    stream.setSSL(true);
    
    return stream;
  }

  /**
   * Returns a read/write pair for a POST request.
   */
  public StreamImpl openReadWriteImpl() throws IOException
  {
    HttpStreamWrapper stream = HttpStream.openReadWrite(this);

    stream.setSSL(true);
    
    return stream;
  }

  /**
   * Returns a hashCode for the path.
   */
  public int hashCode()
  {
    return 17 + 65537 * super.hashCode() + 37 * _host.hashCode() + _port;
  }

  /**
   * Overrides equals to test for equality with an HTTP path.
   */
  public boolean equals(Object o)
  {
    if (! (o instanceof HttpsPath))
      return false;

    HttpsPath test = (HttpsPath) o;

    if (! _host.equals(test._host))
      return false;
    else if (_port != test._port)
      return false;
    else if (_query != null && ! _query.equals(test._query))
      return false;
    else if (_query == null && test._query != null)
      return false;
    else
      return true;
  }
}
