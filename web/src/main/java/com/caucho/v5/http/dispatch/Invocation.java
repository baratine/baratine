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

package com.caucho.v5.http.dispatch;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.io.Dependency;

/**
 * A repository for request information gleaned from the uri.
 */
public class Invocation implements Dependency
{
  private static final Logger log
    = Logger.getLogger(Invocation.class.getName());

  private final boolean _isFiner;

  private ClassLoader _classLoader;

  private String _rawHost;

  // canonical host and port
  private String _hostName;
  private int _port;

  private String _rawURI;
  private boolean _isSecure;

  private String _uri;
  private String _queryString;
  
  private Dependency _depend = Dependency.neverModified();

  /**
   * Creates a new invocation
   */
  public Invocation()
  {
    _classLoader = Thread.currentThread().getContextClassLoader();
    
    _isFiner = log.isLoggable(Level.FINER);
  }
  
  /**
   * Returns the secure flag
   */
  public final boolean isSecure()
  {
    return _isSecure;
  }

  /**
   * Sets the secure flag
   */
  public final void setSecure(boolean isSecure)
  {
    _isSecure = isSecure;
  }

  /**
   * Returns the raw host from the protocol.  This may be different
   * from the canonical host name.
   */
  public final String getHost()
  {
    return _rawHost;
  }

  /**
   * Sets the protocol's host.
   */
  public final void setHost(String host)
  {
    _rawHost = host;
  }

  /**
   * Returns canonical host name.
   */
  public final String getHostName()
  {
    return _hostName;
  }

  /**
   * Sets the protocol's host.
   */
  public final void setHostName(String hostName)
  {
    if (hostName != null && ! hostName.equals(""))
      _hostName = hostName;
  }

  /**
   * Returns canonical port
   */
  public final int getPort()
  {
    return _port;
  }

  /**
   * Sets the canonical port
   */
  public final void setPort(int port)
  {
    _port = port;
  }

  /**
   * Returns the raw URI from the protocol before any normalization.
   * The raw URI includes the query string. (?)
   */
  public final String getRawURI()
  {
    return _rawURI;
  }

  /**
   * Sets the raw URI from the protocol before any normalization.
   * The raw URI includes the query string. (?)
   */
  public final void setRawURI(String uri)
  {
    _rawURI = uri;
  }

  /**
   * Returns the raw URI length.
   */
  public int getURLLength()
  {
    if (_rawURI != null)
      return _rawURI.length();
    else
      return 0;
  }

  /**
   * Returns the URI after normalization, e.g. character escaping,
   * URL session, and query string.
   */
  public final String getURI()
  {
    return _uri;
  }

  /**
   * Sets the URI after normalization.
   */
  public final void setURI(String uri)
  {
    _uri = uri;

    // setContextURI(uri);
  }

  /**
   * Returns the query string.  Characters remain unescaped.
   */
  public final String getQueryString()
  {
    return _queryString;
  }

  /**
   * Returns the query string.  Characters remain unescaped.
   */
  public final void setQueryString(String queryString)
  {
    _queryString = queryString;
  }

  /**
   * Sets the class loader.
   */
  public void setClassLoader(ClassLoader loader)
  {
    _classLoader = loader;
  }

  /**
   * Gets the class loader.
   */
  public ClassLoader getClassLoader()
  {
    return _classLoader;
  }

  /**
   * Service a request.
   *
   * @param request the http request facade
   * @param response the http response facade
   */
  /*
  public void service(RequestFacade request, ResponseFacade response)
    throws Exception
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */

  /**
   * Copies from the invocation.
   */
  public void copyFrom(Invocation invocation)
  {
    _classLoader = invocation._classLoader;
    
    _rawHost = invocation._rawHost;
    _rawURI = invocation._rawURI;

    _hostName = invocation._hostName;
    _port = invocation._port;
    _uri = invocation._uri;
    
    _depend = invocation._depend;
    
    _queryString = invocation._queryString;
  }
  
  public final void setDependency(Dependency depend)
  {
    Objects.requireNonNull(depend);
    
    _depend = depend;
  }
  
  public final Dependency getDependency()
  {
    return _depend;
  }

  @Override
  public boolean isModified()
  {
    return _depend.isModified();
  }

  @Override
  public boolean logModified(Logger log)
  {
    return _depend.logModified(log);
  }

  /**
   * Returns the invocation's hash code.
   */
  @Override
  public int hashCode()
  {
    int hash = _rawURI.hashCode();

    if (_rawHost != null) {
      hash = hash * 65521 + _rawHost.hashCode();
    }

    hash = hash * 65521 + _port;

    return hash;
  }

  /**
   * Checks for equality
   */
  @Override
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }
    else if (o == null) {
      return false;
    }

    if (getClass() != o.getClass()) {
      return false;
    }

    Invocation inv = (Invocation) o;

    if (_isSecure != inv._isSecure) {
      return false;
    }

    if (_rawURI != inv._rawURI &&
        (_rawURI == null || ! _rawURI.equals(inv._rawURI))) {
      return false;
    }

    if (_rawHost != inv._rawHost &&
        (_rawHost == null || ! _rawHost.equals(inv._rawHost))) {
      return false;
    }

    if (_port != inv._port) {
      return false;
    }

    String aQuery = getQueryString();
    String bQuery = inv.getQueryString();

    if (aQuery != bQuery &&
        (aQuery == null || ! aQuery.equals(bQuery))) {
      return false;
    }

    return true;
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();

    sb.append(getClass().getSimpleName());
    sb.append("[");
    sb.append(_uri);

    if (_queryString != null)
      sb.append("?").append(_queryString);

    sb.append("]");

    return sb.toString();
  }
}
