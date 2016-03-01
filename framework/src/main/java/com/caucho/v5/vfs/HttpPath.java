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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.util.CharBuffer;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.LruCache;
import com.caucho.v5.util.QDate;

/**
 * The HTTP scheme.  Currently it supports GET and POST.
 *
 * <p>TODO: support WEBDAV, enabling the full Path API.
 */
public class HttpPath extends FilesystemPath {
  protected static L10N L = new L10N(HttpPath.class);

  protected static LruCache<String,CacheEntry> _cache
    = new LruCache<String,CacheEntry>(1024);
  
  protected String _host;
  protected int _port;
  protected String _query;

  protected String _virtualHost;

  protected CacheEntry _cacheEntry;

  /**
   * Creates a new HTTP root path with a host and a port.
   *
   * @param host the target host
   * @param port the target port, if zero, uses port 80.
   */
  public HttpPath(String host, int port)
  {
    super(null, "/", "/");

    _root = this;
    _host = host;
    _port = port == 0 ? 80 : port;
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
  HttpPath(FilesystemPath root,
           String userPath, Map<String,Object> newAttributes,
           String path, String query)
  {
    super(root, userPath, path);

    _host = ((HttpPath) root)._host;
    _port = ((HttpPath) root)._port;
    _query = query;

    if (newAttributes != null) {
      _virtualHost = (String) newAttributes.get("host");
    }
  }

  /**
   * Overrides the default lookup to parse the host and port
   * before parsing the path.
   *
   * @param userPath the path passed in by the user
   * @param newAttributes attributes passed by the user
   *
   * @return the final path.
   */
  @Override
  public PathImpl lookupImpl(String userPath, 
                         Map<String,Object> newAttributes,
                         boolean isAllowRoot)
  {
    String newPath;

    if (userPath == null)
      return _root.fsWalk(getPath(), newAttributes, "/");

    int length = userPath.length();
    int colon = userPath.indexOf(':');
    int slash = userPath.indexOf('/');
    int query = userPath.indexOf('?');

    // parent handles scheme:xxx
    if (colon != -1 && (colon < slash || slash == -1))
      return super.lookupImpl(userPath, newAttributes, isAllowRoot);
      
      // //hostname
    if (slash == 0 && length > 1 && userPath.charAt(1) == '/')
      return schemeWalk(userPath, newAttributes, userPath, 0);

      // /path
    else if (slash == 0) {
      String queryString = "";
      if (query >= 0) {
        queryString = userPath.substring(query);
        userPath = userPath.substring(0, query);
      }
      
      newPath = normalizePath("/", userPath, 0, '/');
      
      if (query >= 0)
        newPath += queryString;
    }
      // path
    else {
      String queryString = "";
      if (query >= 0) {
        queryString = userPath.substring(query);
        userPath = userPath.substring(0, query);
      }
      
      newPath = normalizePath(_pathname, userPath, 0, '/');
      
      if (query >= 0)
        newPath += queryString;
    }

    // XXX: does missing root here cause problems with restrictions?
    return _root.fsWalk(userPath, newAttributes, newPath);
  }

  /**
   * Walk down the path starting from the portion immediately following
   * the scheme.  i.e. schemeWalk is responsible for parsing the host and
   * port from the URL.
   *
   * @param userPath the user's passed in path
   * @param attributes the attributes for the new path
   * @param uri the normalized full uri
   * @param offset offset into the uri to start processing, i.e. after the
   *  scheme.
   *
   * @return the looked-up path.
   */
  @Override
  public PathImpl schemeWalk(String userPath,
                         Map<String,Object> attributes,
                         String uri,
                         int offset)
  {
    int length = uri.length();

    if (length < 2 + offset
        || uri.charAt(offset) != '/'
        || uri.charAt(offset + 1) != '/')
      throw new RuntimeException(L.l("bad scheme in `{0}'", uri));

    CharBuffer buf = CharBuffer.allocate();
    int i = 2 + offset;
    int ch = 0;
    boolean isInBrace = false;
    
    for (; i < length
           && ((ch = uri.charAt(i)) != ':' || isInBrace)
           && ch != '/'
           && ch != '?';
         i++) {
      buf.append((char) ch);
      
      if (ch == '[')
        isInBrace = true;
      else if (ch == ']')
        isInBrace = false;
    }

    String host = buf.close();
    if (host.length() == 0)
      throw new RuntimeException(L.l("bad host in `{0}'", uri));

    int port = 0;
    if (ch == ':') {
      for (i++; i < length && (ch = uri.charAt(i)) >= '0' && ch <= '9'; i++) {
        port = 10 * port + uri.charAt(i) - '0';
      }
    }

    if (port == 0)
      port = 80;

    HttpPath root = create(host, port);

    return root.fsWalk(userPath, attributes, uri.substring(i));
  }

  /**
   * Scans the path portion of the URI, i.e. everything after the
   * host and port.
   *
   * @param userPath the user's supplied path
   * @param attributes the attributes for the new path
   * @param uri the full uri for the new path.
   *
   * @return the found path.
   */
  public PathImpl fsWalk(String userPath,
                     Map<String,Object> attributes,
                     String uri)
  {
    String path;
    String query = null;
    int queryIndex = uri.indexOf('?');
    if (queryIndex >= 0) {
      path = uri.substring(0, queryIndex);
      query = uri.substring(queryIndex + 1);
    } else
      path = uri;

    if (path.length() == 0)
      path = "/";

    return create(_root, userPath, attributes, path, query);
  }

  protected HttpPath create(String host, int port)
  {
    return new HttpPath(host, port);
  }

  protected HttpPath create(FilesystemPath root,
                            String userPath,
                            Map<String,Object> newAttributes,
                            String path, String query)
  {
    return new HttpPath(root, userPath, newAttributes, path, query);
  }

  /**
   * Returns the scheme, http.
   */
  public String getScheme()
  {
    return "http";
  }

  /**
   * Returns a full URL for the path.
   */
  public String getURL()
  {
    int port = getPort();

    return (getScheme() + "://" + getHost() + 
            (port == 80 ? "" : ":" + getPort()) +
            getPath() +
            (_query == null ? "" : "?" + _query));
  }
  
  public String getNativePath()
  {
    return getURL();
  }

  /**
   * Returns the host part of the url.
   */
  public String getHost()
  {
    return _host;
  }

  /**
   * Returns the port part of the url.
   */
  public int getPort()
  {
    return _port;
  }

  /**
   * Returns the user's path.
   */
  public String getUserPath()
  {
    return _userPath;
  }

  /**
   * Returns the virtual host, if any.
   */
  public String getVirtualHost()
  {
    return _virtualHost;
  }

  /**
   * Returns the query string.
   */
  public String getQuery()
  {
    return _query;
  }

  /**
   * Returns the last modified time.
   */
  @Override
  public long getLastModified()
  {
    return getCache().lastModified;
  }

  /**
   * Returns the file's length
   */
  @Override
  public long length()
  {
    return getCache().length;
  }

  /**
   * Returns true if the file exists.
   */
  @Override
  public boolean exists()
  {
    // php/1x1e
    // return getCache().lastModified >= 0;
    return true;
  }

  /**
   * Returns true if the file exists.
   */
  @Override
  public boolean isFile()
  {
    // php/1x1e
    // return ! getPath().endsWith("/") && getCache().lastModified >= 0;
    return true;
  }

  /**
   * Returns true if the file is readable.
   */
  @Override
  public boolean canRead()
  {
    // php/1x1e
    // return isFile();
    return true;
  }

  /**
   * Returns the last modified time.
   */
  @Override
  public boolean isDirectory()
  {
    // php/1x1e
    // return getPath().endsWith("/") && getCache().lastModified >= 0;
    return false;
  }
  
  /**
   * @return The contents of this directory or null if the path does not
   * refer to a directory.
   */
  /*
  public String []list() throws IOException
  {
    try {
      HttpStream stream = (HttpStream) openReadWriteImpl();
      stream.setMethod("PROPFIND");
      stream.setAttribute("Depth", "1");

      WriteStream os = new WriteStream(stream);
      os.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
      os.println("<propfind xmlns=\"DAV:\"><prop>");
      os.println("<resourcetype/>");
      os.println("</prop></propfind>");
      os.flush();

      ReadStream is = new ReadStream(stream);
    
      ListHandler handler = new ListHandler(getPath());
      XmlParser parser = new XmlParser();
      parser.setContentHandler(handler);

      parser.parse(is);

      is.close();
      os.close();
      stream.close();

      ArrayList<String> names = handler.getNames();
      String []list = new String[names.size()];
      names.toArray(list);
      
      return list;
    } catch (Exception e) {
      throw new IOException(L.l("list() is not supported by this server"));
    }
  }
  */

  protected CacheEntry getCache()
  {
    if (_cacheEntry == null) {
      synchronized (_cache) {
        _cacheEntry = _cache.get(getPath());
        if (_cacheEntry == null) {
          _cacheEntry = new CacheEntry();
          _cache.put(getPath(), _cacheEntry);
        }
      }
    }
    
    long now;
    
    now = CurrentTime.getCurrentTime();
    
    synchronized (_cacheEntry) {
      try {
        if (_cacheEntry.expires > now)
          return _cacheEntry;
      
        HttpStreamWrapper stream = (HttpStreamWrapper) openReadImpl();
        stream.setHead(true);
        stream.setSocketTimeout(120000);

        String status = (String) stream.getAttribute("status");
        if (status.equals("200")) {
          String lastModified = (String) stream.getAttribute("last-modified");

          _cacheEntry.lastModified = 0;
          if (lastModified != null) {
            QDate date = QDate.getGlobalDate();
            synchronized (date) {
              _cacheEntry.lastModified = date.parseDate(lastModified);
            }
          }

          String length =  (String) stream.getAttribute("content-length");
          _cacheEntry.length = 0;
          if (length != null) {
            _cacheEntry.length = Integer.parseInt(length);
          }
        }
        else
          _cacheEntry.lastModified = -1;
        
        _cacheEntry.expires = now + 5000;
      
        stream.close();
        return _cacheEntry;
      } catch (Exception e) {
        _cacheEntry.lastModified = -1;
        _cacheEntry.expires = now + 5000;

        return _cacheEntry;
      }
    }
  }
  

  /**
   * Returns a read stream for a GET request.
   */
  @Override
  public StreamImpl openReadImpl() throws IOException
  {
    return HttpStream.openRead(this);
  }

  /**
   * Returns a read/write pair for a POST request.
   */
  public StreamImpl openReadWriteImpl() throws IOException
  {
    return HttpStream.openReadWrite(this);
  }

  @Override
  protected PathImpl cacheCopy()
  {
    return new HttpPath(getRoot(), getUserPath(),
                        null,
                        getPath(), _query);
  }
  
  /**
   * Returns the string form of the http path.
   */
  @Override
  public String toString()
  {
    return getURL();
  }

  /**
   * Returns a hashCode for the path.
   */
  @Override
  public int hashCode()
  {
    return 65537 * super.hashCode() + 37 * _host.hashCode() + _port;
  }

  /**
   * Overrides equals to test for equality with an HTTP path.
   */
  public boolean equals(Object o)
  {
    if (! (o instanceof HttpPath))
      return false;

    HttpPath test = (HttpPath) o;

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

  static class CacheEntry {
    long lastModified;
    long length;
    boolean canRead;
    long expires;
  }

  static class ListHandler extends org.xml.sax.helpers.DefaultHandler {
    String _prefix;
    ArrayList<String> _names = new ArrayList<String>();
    boolean _inHref;

    ListHandler(String prefix)
    {
      _prefix = prefix;
    }
    
    ArrayList<String> getNames()
    {
      return _names;
    }

    public void startElement (String uri, String localName,
                              String qName, Attributes attributes)
    {
      if (localName.equals("href"))
        _inHref = true;
    }

    public void characters(char []data, int offset, int length)
        throws SAXException
    {
      if (! _inHref)
        return;

      String href = new String(data, offset, length).trim();
      if (! href.startsWith(_prefix))
        return;

      href = href.substring(_prefix.length());
      if (href.startsWith("/"))
        href = href.substring(1);

      int p = href.indexOf('/');
      if (href.equals("") || p == 0)
        return;

      if (p < 0)
        _names.add(href);
      else
        _names.add(href.substring(0, p));
    }

    public void endElement (String uri, String localName, String qName)
        throws SAXException
    {
      if (localName.equals("href"))
        _inHref = false;
    }
  }
}
