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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import com.caucho.v5.io.StreamImpl;
import com.caucho.v5.util.CharBuffer;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;

/**
 * Underlying stream handling HTTP requests.
 */
class HttpStream extends StreamImpl {
  private static final L10N L = new L10N(HttpStream.class);
  
  private static final Logger log
    = Logger.getLogger(HttpStream.class.getName());
  // reserved headers that should not be passed to the HTTP server
  private static HashMap<String,String> _reserved;

  private static final Object LOCK = new Object();
  
  // Saved keepalive stream for a new request.
  private static HttpStream _savedStream;
  // Time the stream was saved
  private static long _saveTime;
  
  private static boolean _isKeepaliveAllowed;
  
  private PathImpl _path;
  
  private long _socketTimeout = 30000L;

  private boolean _isSSL;

  private Socket _s;
  private InputStream _is;
  private OutputStream _os;
  private ReadStreamOld _rs;
  private WriteStreamOld _ws;

  // The server's host name
  private String _host;
  // The server's port
  private int _port;

  private String _virtualHost;

  // the method
  private String _method;
  // true for a HEAD stream
  private boolean _isHead;
  // true for a POST stream
  private boolean _isPost;
  
  // true for an HTTP 1.1 client
  private boolean _isHttp11 = true;

  // buffer containing the POST data
  private MemoryStream _tempStream;

  // true if keepalive is allowed
  private boolean _isKeepalive = _isKeepaliveAllowed;
  // true after the request has been sent
  private boolean _didGet;
  // content length from the returned response
  private int _contentLength;
  // true if the response was chunked
  private boolean _isChunked;
  // length of the current chunk, -1 on eof
  private int _chunkLength;
  // the request is done
  private boolean _isRequestDone;

  private HashMap<String,Object> _attributes;

  // Used to read unread bytes on recycle.
  private byte []_tempBuffer;

  /**
   * Create a new HTTP stream.
   */
  private HttpStream(PathImpl path, String host, int port, Socket s)
    throws IOException
  {
    _s = s;

    _host = host;
    _port = port;
    
    _is = _s.getInputStream();
    _os = _s.getOutputStream();

    _ws = VfsStreamOld.openWrite(_os);
    _rs = VfsStreamOld.openRead(_is, _ws);

    _attributes = new HashMap<String,Object>();

    init(path);
  }

  /**
   * Opens a new HTTP stream for reading, i.e. a GET request.
   *
   * @param path the URL for the stream
   *
   * @return the opened stream
   */
  static HttpStreamWrapper openRead(HttpPath path) throws IOException
  {
    HttpStream stream = createStream(path);
    stream._isPost = false;

    HttpStreamWrapper wrapper = new HttpStreamWrapper(stream);
    
    String status = (String) wrapper.getAttribute("status");
    
    // ioc/23p0
    if ("404".equals(status)) {
      throw new FileNotFoundException(L.l("'{0}' returns a HTTP 404.",
                                          path.getURL()));
    }
    
    return wrapper;
  }

  public static void setAllowKeepalive(boolean isAllowKeepalive)
  {
    _isKeepaliveAllowed = isAllowKeepalive;
  }
  
  /**
   * Opens a new HTTP stream for reading and writing, i.e. a POST request.
   *
   * @param path the URL for the stream
   *
   * @return the opened stream
   */
  static HttpStreamWrapper openReadWrite(HttpPath path) throws IOException
  {
    HttpStream stream = createStream(path);
    stream._isPost = true;

    return new HttpStreamWrapper(stream);
  }

  /**
   * Creates a new HTTP stream.  If there is a saved connection to
   * the same host, use it.
   *
   * @param path the URL for the stream
   *
   * @return the opened stream
   */
  static private HttpStream createStream(HttpPath path) throws IOException
  {
    String host = path.getHost();
    int port = path.getPort();

    HttpStream stream = null;
    long streamTime = 0;
    synchronized (LOCK) {
      if (_savedStream != null
          && host.equals(_savedStream.getHost())
          && port == _savedStream.getPort()) {
        stream = _savedStream;
        streamTime = _saveTime;
        _savedStream = null;
      }
    }

    if (stream != null) {
      long now;
      
      now = CurrentTime.currentTime();
      
      if (now < streamTime + 5000) {
        // if the stream is still valid, use it
        stream.init(path);
        return stream;
      }
      else {
        // if the stream has timed out, close it
        try {
          stream._isKeepalive = false;
          stream.close();
        } catch (IOException e) {
          log.log(Level.FINE, e.toString(), e);
        }
      }
    }

    Socket s;

    try {
      s = new Socket(host, port);
      
      if (path instanceof HttpsPath) {
        SSLContext context = SSLContext.getInstance("TLS");

        javax.net.ssl.TrustManager tm =
          new javax.net.ssl.X509TrustManager() {
            public java.security.cert.X509Certificate[]
              getAcceptedIssuers() {
              return null;
            }
            public void checkClientTrusted(
                                           java.security.cert.X509Certificate[] cert, String foo) {
            }
            public void checkServerTrusted(
                                           java.security.cert.X509Certificate[] cert, String foo) {
            }
          };

      
        context.init(null, new javax.net.ssl.TrustManager[] { tm }, null);
        SSLSocketFactory factory = context.getSocketFactory();

        s = factory.createSocket(s, host, port, true);
      }
    } catch (ConnectException e) {
      throw new ConnectException(path.getURL() + ": " + e.getMessage());
    } catch (Exception e) {
      throw new ConnectException(path.getURL() + ": " + e.toString());
    }

    int socketTimeout = 300 * 1000;

    try {
      s.setSoTimeout(socketTimeout);
    } catch (Exception e) {
    }
          
    return new HttpStream(path, host, port, s);
  }

  /**
   * Initializes the stream for the next request.
   */
  private void init(PathImpl path)
  {
    _contentLength = -1;
    _isChunked = false;
    _isRequestDone = false;
    _didGet = false;
    _isPost = false;
    _isHead = false;
    _method = null;
    _attributes.clear();
    
    //setPath(path);

    if (path instanceof HttpPath)
      _virtualHost = ((HttpPath) path).getVirtualHost();
  }

  /**
   * Set if this should be an SSL connection.
   */
  public void setSSL(boolean isSSL)
  {
    _isSSL = isSSL;
  }

  /**
   * Set if this should be an SSL connection.
   */
  public boolean isSSL()
  {
    return _isSSL;
  }

  /**
   * Sets the method
   */
  public void setMethod(String method)
  {
    _method = method;
  }

  /**
   * Sets true if we're only interested in the head.
   */
  public void setHead(boolean isHead)
  {
    _isHead = isHead;
  }

  /**
   * Returns the stream's host.
   */
  public String getHost()
  {
    return _host;
  }

  /**
   * Returns the stream's port.
   */
  public int getPort()
  {
    return _port;
  }
  
  /**
   * Sets the http version.
   */
  public void setHttp10()
  {
    _isHttp11 = false;
  }
  
  /**
   * Sets the http version.
   */
  public void setHttp11()
  {
    _isHttp11 = true;
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
    if (! _didGet)
      getConnInput();
    
    return _attributes.get(name.toLowerCase(Locale.ENGLISH));
  }

  /**
   * Returns an iterator of the returned header names.
   */
  public Iterator getAttributeNames()
    throws IOException
  {
    if (! _didGet)
      getConnInput();

    return _attributes.keySet().iterator();
  }

  /**
   * Sets a header for the request.
   */
  //@Override
  public void setAttribute(String name, Object value)
  {
    if (name.equals("method")) {
      setMethod((String) value);
    }
    else if (name.equals("socket-timeout")) {
      if (value instanceof Integer) {
        int socketTimeout = ((Integer) value).intValue();

        if (socketTimeout > 0) {
          try {
            if (_s != null)
              _s.setSoTimeout(socketTimeout);
          } catch (Exception e) {

          }
        }
      }
    }
    else {
      Object oldValue = _attributes.put(name.toLowerCase(Locale.ENGLISH), value);

      if (oldValue instanceof String[]) {
        String []old = (String []) oldValue;
        String []newValue = new String[old.length + 1];
        System.arraycopy(old, 0, newValue, 0, old.length);
        newValue[old.length] = String.valueOf(value);
        _attributes.put(name.toLowerCase(Locale.ENGLISH), newValue);
      }
      else if (oldValue != null) {
        String []newValue = new String[] { String.valueOf(oldValue),
                                           String.valueOf(value) };
        _attributes.put(name.toLowerCase(Locale.ENGLISH), newValue);
      }
    }
  }

  /**
   * Remove a header for the request.
   */
  public void removeAttribute(String name)
  {
    _attributes.remove(name.toLowerCase(Locale.ENGLISH));
  }

  /**
   * Sets the timeout.
   */
  public void setSocketTimeout(long timeout)
    throws SocketException
  {
    if (_s != null)
      _s.setSoTimeout((int) timeout);
  }

  /**
   * The stream is always writable (?)
   */
  public boolean canWrite()
  {
    return true;
  }

  /**
   * Writes a buffer to the underlying stream.
   *
   * @param buffer the byte array to write.
   * @param offset the offset into the byte array.
   * @param length the number of bytes to write.
   * @param isEnd true when the write is flushing a close.
   */
  @Override
  public void write(byte []buf, int offset, int length, boolean isEnd)
    throws IOException
  {
    if (! _isPost) {
      return;
    }

    if (_tempStream == null) {
      _tempStream = new MemoryStream();
    }

    _tempStream.write(buf, offset, length, isEnd);
  }

  /**
   * The stream is readable.
   */
  public boolean canRead()
  {
    return true;
  }

  /**
   * Read data from the connection.  If the request hasn't yet been sent
   * to the server, send it.
   */
  public int read(byte []buf, int offset, int length) throws IOException
  {
    try {
      return readInt(buf, offset, length);
    } catch (IOException e) {
      _isKeepalive = false;
      throw e;
    } catch (RuntimeException e) {
      _isKeepalive = false;
      throw e;
    }
  }
  
  /**
   * Read data from the connection.  If the request hasn't yet been sent
   * to the server, send it.
   */
  public int readInt(byte []buf, int offset, int length) throws IOException
  {
    if (! _didGet) {
      getConnInput();
    }

    if (_isRequestDone) {
      return -1;
    }

    try {
      int len = length;

      if (_isChunked) {
        if (_chunkLength == 0) {
          int ch;

          for (ch = _rs.read();
               ch >= 0 && (ch == '\r' || ch == '\n' || ch == ' ');
               ch = _rs.read()) {
          }
        
          for (; ch >= 0 && ch != '\n'; ch = _rs.read()) {
            if (ch >= '0' && ch <= '9')
              _chunkLength = 16 * _chunkLength + ch - '0';
            else if (ch >= 'a' && ch <= 'f')
              _chunkLength = 16 * _chunkLength + ch - 'a' + 10;
            else if (ch >= 'A' && ch <= 'F')
              _chunkLength = 16 * _chunkLength + ch - 'A' + 10;
          }

          if (_chunkLength == 0) {
            _isRequestDone = true;
            return -1;
          }
        }
        else if (_chunkLength < 0)
          return -1;
      
        if (_chunkLength < len)
          len = _chunkLength;
      }
      else if (_contentLength < 0) {
      }
      else if (_contentLength == 0) {
        _isRequestDone = true;
        return -1;
      }
      else if (_contentLength < len)
        len = _contentLength;

      len = _rs.read(buf, offset, len);

      if (len < 0) {
      }
      else if (_isChunked)
        _chunkLength -= len;
      else if (_contentLength > 0)
        _contentLength -= len;

      return len;
    } catch (IOException e) {
      _isKeepalive = false;
      throw e;
    } catch (RuntimeException e) {
      _isKeepalive = false;
      throw e;
    }
  }

  /**
   * Sends the request and initializes the response.
   */
  private void getConnInput() throws IOException
  {
    if (_didGet)
      return;

    try {
      getConnInputImpl();
    } catch (IOException e) {
      _isKeepalive = false;
      throw e;
    } catch (RuntimeException e) {
      _isKeepalive = false;
      throw e;
    }
  }

  /**
   * Send the request to the server, wait for the response and parse
   * the headers.
   */
  private void getConnInputImpl() throws IOException
  {
    if (_didGet)
      return;

    _didGet = true;
    
    if (log.isLoggable(Level.FINER))
      log.finer(this + " connect " + _method
                + " post=" + _isPost + " " + _path.getPath());

    if (_method != null) {
      _ws.print(_method);
      _ws.print(' ');
    }
    else if (_isPost)
      _ws.print("POST ");
    else if (_isHead)
      _ws.print("HEAD ");
    else
      _ws.print("GET ");

    // Not splitting query? Also fullpath?
    _ws.print(_path.getPath());

    if (_path.getQuery() != null) {
      _ws.print("?");
      _ws.print(_path.getQuery());
    }
    
    if (_isHttp11) {
      _ws.print(" HTTP/1.1\r\n");
      Object host = getAttribute("host");
      _ws.print("Host: ");
      if (host != null) {
        _ws.print(host);
      }
      else if (_virtualHost != null)
        _ws.print(_virtualHost);
      else {
        _ws.print(_path.getHost());
        if (_path.getPort() != 80) {
      _ws.print(":");
      _ws.print(String.valueOf(_path.getPort()));
        }
      }
    }
    else
      _ws.print(" HTTP/1.0\r\n");
    
    _ws.print("\r\n");
    
    Object userAgent = getAttribute("User-Agent");
    if (userAgent == null)
      _ws.print("User-Agent: Mozilla/4.0 (compatible; Baratine 1.0; JDK)\r\n");
    else
      _ws.print("User-Agent: " + userAgent + "\r\n");
    Iterator iter = getAttributeNames();
    while (iter.hasNext()) {
      String name = (String) iter.next();
      if (_reserved.get(name.toLowerCase(Locale.ENGLISH)) == null) {
        Object value = getAttribute(name);
        if (value instanceof String[]) {
          String []values = (String []) value;
          for (int i = 0; i < values.length; i++) {
            _ws.print(name + ": " + values[i] + "\r\n");
          }
        }
        else
          _ws.print(name + ": " + value + "\r\n");
      }
    }
    if (! _isKeepalive)
      _ws.print("Connection: close\r\n");
    
    if (_isPost) {
      int writeLength = 0;
      if (_tempStream != null)
        writeLength = _tempStream.getLength();
      
      Object contentLength = getAttribute("Content-Length");

      if (contentLength != null) {
        long len = 0;
        
        if (contentLength instanceof Number)
          len = ((Number) contentLength).longValue();
        else {
          String lenStr = contentLength.toString().trim();
          
          for (int i = 0; i < lenStr.length(); i++) {
            char ch = lenStr.charAt(i);
            
            if ('0' <= ch && ch <= '9') 
              len = len * 10 + ch - '0';
            else
              break;
          }
        }
        
        // server/1963
        if (len != writeLength) {
          throw new IOException(L.l("Content-Length={0} but only received {1}",
                                    len, "" + writeLength));
        }
      }

      _ws.print("Content-Length: " + writeLength);
      _ws.print("\r\n");
    }
    _ws.print("\r\n");

    if (_isPost) {
      MemoryStream tempStream = _tempStream;
      _tempStream = null;
      if (tempStream != null) {
        tempStream.writeToStream(_ws);
        tempStream.destroy();
      }
    }

    _attributes.clear();

    parseHeaders();

    if (_isHead)
      _isRequestDone = true;
  }

  /**
   * Parse the headers returned from the server.
   */
  private void parseHeaders() throws IOException
  {
    _ws.flush();
    
    CharBuffer line = new CharBuffer();

    // Skip blank lines
    int count = 0;
    do {
      line.clear();
      if (! _rs.readln(line)) {
        _isKeepalive = false;
        return;
      }
    } while (line.length() == 0 && ++count < 10);

    if (line.length() == 0) {
      _isKeepalive = false;
      return;
    }

    if (line.startsWith("HTTP/1.1 100")) {
      count = 100;
      do {
        line.clear();
        if (! _rs.readln(line)) {
          _isKeepalive = false;
          return;
        }
      } while (line.length() != 0 && count-- > 0);
      
      count = 100;
      do {
        line.clear();
        if (! _rs.readln(line)) {
          _isKeepalive = false;
          return;
        }
      } while (line.length() == 0 && count-- > 0);
    }

    if (line.length() == 0) {
      _isKeepalive = false;
      return;
    }

    int i = 0;
    for (i = 0; i < line.length() && line.charAt(i) != ' '; i++) {
    }
    
    for (; i < line.length() && line.charAt(i) == ' '; i++) {
    }
    
    int status = 0;
    for (; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch >= '0' && ch <= '9')
        status = 10 * status + ch - '0';
      else
        break;
    }

    if (status != 200)
      _isKeepalive = false;
    else if (! line.startsWith("HTTP/1.1 "))
      _isKeepalive = false;

    _attributes.put("status", String.valueOf(status));
    _attributes.put("status-message", line.toString());

    CharBuffer key = new CharBuffer();
    while (true) {
      line.clear();
      if (! _rs.readln(line) || line.length() == 0)
          break;

      int lineLength = line.length();
      
      for (i = 0;
           i < lineLength && Character.isWhitespace(line.charAt(i));
           i++) {
      }

      key.clear();
      for (;
           i < lineLength && ! Character.isWhitespace(line.charAt(i))
             && line.charAt(i) != ':';
           i++) {
        key.append((char) line.charAt(i));
      }

      for (;
           i < lineLength && Character.isWhitespace(line.charAt(i));
           i++) {
      }

      if (key.length() == 0 || lineLength <= i || line.charAt(i) != ':')
        continue;

      for (i++;
           i < lineLength && Character.isWhitespace(line.charAt(i));
           i++) {
      }

      key.toLowerCase();
      String value = line.substring(i);

      if (log.isLoggable(Level.FINE))
        log.fine(key + ": " + value);
      
      if (key.matchesIgnoreCase("content-length")) {
        _contentLength = Integer.parseInt(value.trim());
      }
      else if (key.matchesIgnoreCase("connection")
               && value.equalsIgnoreCase("close")) {
        _isKeepalive = false;
      }
      else if (key.matchesIgnoreCase("transfer-encoding")
               && value.equalsIgnoreCase("chunked")) {
        
        _isChunked = true;
        _chunkLength = 0;
      }

      String keyString = key.toLowerCase().toString();
      
      String oldValue = (String) _attributes.put(keyString, value);

      if (oldValue != null) {
        value = oldValue + '\n' + value;
      }
      
      _attributes.put(keyString, value);
    }
  }

  /**
   * Returns the bytes still available.
   */
  public int getAvailable() throws IOException
  {
    if (! _didGet)
      getConnInput();

    // php/164q
    if (_isRequestDone)
      return 0;
    else if (_contentLength > 0)
      return _contentLength;
    else
      return _rs.getAvailable();
  }
  
  @Override
  public void closeWrite()
    throws IOException
  {
    if (! _didGet) {
      getConnInput();
    }
  }


  /**
   * Close the connection.
   */
  public void close() throws IOException
  {
    if (_isKeepalive) {
      // If recycling, read any unread data
      if (! _didGet)
        getConnInput();

      if (! _isRequestDone) {
        if (_tempBuffer == null)
          _tempBuffer = new byte[256];

        try {
          while (read(_tempBuffer, 0, _tempBuffer.length) > 0) {
          }
        } catch (IOException e) {
          _isKeepalive = false;
        }
      }
    }

    if (_isKeepalive) {
      HttpStream oldSaved;
      
      long now;
      
      now = CurrentTime.currentTime();
      
      synchronized (LOCK) {
        oldSaved = _savedStream;
        _savedStream = this;
        _saveTime = now;
      }

      if (oldSaved != null && oldSaved != this) {
        oldSaved._isKeepalive = false;
        oldSaved.close();
      }

      return;
    }

    try {
      try {
        if (_ws != null)
          _ws.close();
      } catch (Throwable e) {
      }
      _ws = null;

      try {
        if (_rs != null)
          _rs.close();
      } catch (Throwable e) {
      }
      _rs = null;

      try {
        if (_os != null)
          _os.close();
      } catch (Throwable e) {
      }
      _os = null;

      try {
        if (_is != null)
          _is.close();
      } catch (Throwable e) {
      }
      _is = null;
    } finally {
      if (_s != null)
        _s.close();
      _s = null;
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _s + "]";
  }

  static {
    _reserved = new HashMap<String,String>();
    _reserved.put("user-agent", "");
    _reserved.put("content-length", "");
    //_reserved.put("content-encoding", "");
    _reserved.put("connection", "");
    //_reserved.put("host", "");
  }
}
