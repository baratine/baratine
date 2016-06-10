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

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.health.meter.CountSensor;
import com.caucho.v5.health.meter.MeterService;
import com.caucho.v5.http.container.HttpContainer;
import com.caucho.v5.http.dispatch.Invocation;
import com.caucho.v5.http.dispatch.InvocationDecoder;
import com.caucho.v5.http.dispatch.InvocationManager;
import com.caucho.v5.http.log.LogBuffer;
import com.caucho.v5.io.IoUtil;
import com.caucho.v5.io.OutputStreamWithBuffer;
import com.caucho.v5.io.WriteStream;
import com.caucho.v5.io.i18n.Encoding;
import com.caucho.v5.network.port.ConnectionProtocol;
import com.caucho.v5.network.port.ConnectionTcp;
import com.caucho.v5.util.CaseInsensitiveIntMap;
import com.caucho.v5.util.CharBuffer;
import com.caucho.v5.util.CharSegment;
import com.caucho.v5.util.ClockCurrent;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.LruCache;
import com.caucho.v5.web.CookieWeb;
import com.caucho.v5.web.webapp.InvocationBaratine;
import com.caucho.v5.web.webapp.RequestBaratine;
import com.caucho.v5.web.webapp.WriterUtf8;

/**
 * Abstract request implementing methods common to the different
 * request implementations.
 */
public abstract class RequestHttpBase implements OutHttpTcp, RequestOut
{
  private static final Logger log
    = Logger.getLogger(RequestHttpBase.class.getName());

  private static final L10N L = new L10N(RequestHttpBase.class);

  protected static final CaseInsensitiveIntMap _headerCodes;

  public static final String SHUTDOWN = "com.caucho.shutdown";

  private static final char []CONNECTION = "connection".toCharArray();
  private static final char []COOKIE = "cookie".toCharArray();
  private static final char []CONTENT_LENGTH = "content-length".toCharArray();
  private static final char []CONTENT_TYPE = "content-type".toCharArray();
  private static final char []EXPECT = "expect".toCharArray();
  private static final char []HOST = "host".toCharArray();
  private static final char []TRANSFER_ENCODING = "transfer-encoding".toCharArray();
  private static final char []X_FORWARDED_HOST = "x-forwarded-host".toCharArray();
  
  private static final int CONNECTION_KEY
    = 'c' | (10 << 8);
  private static final int COOKIE_KEY
    = 'c' | (6 << 8);
  private static final int CONTENT_LENGTH_KEY
    = 'c' | (14 << 8);
  private static final int EXPECT_KEY
    = 'e' | (6 << 8);
  private static final int HOST_KEY
    = 'h' | (4 << 8);
  private static final int TRANSFER_ENCODING_KEY
    = 't' | (17 << 8);
  private static final int X_FORWARDED_HOST_KEY
    = 'x' | (16 << 8);

  private static final char []CONTINUE_100 = "100-continue".toCharArray();
  private static final char []CLOSE = "close".toCharArray();
  private static final char []KEEPALIVE = "keep-alive".toCharArray();
  private static final char []UPGRADE = "Upgrade".toCharArray();
  
  private static final CharBuffer CACHE_CONTROL_CB
    = new CharBuffer("cache-control");
  private static final CharBuffer CONNECTION_CB
    = new CharBuffer("connection");
  private static final CharBuffer CONTENT_TYPE_CB
    = new CharBuffer("content-type");
  private static final CharBuffer CONTENT_LENGTH_CB
    = new CharBuffer("content-length");
  private static final CharBuffer DATE_CB
    = new CharBuffer("date");
  private static final CharBuffer SERVER_CB
    = new CharBuffer("server");

  public static final boolean []TOKEN;
  private static final boolean []VALUE;

  private static final CookieWeb []NULL_COOKIES = new CookieWeb[0];
  
  private static final CountSensor _statusXxxSensor
  = MeterService.createCountMeter("Caucho|Http|xxx");
  
  private static final CountSensor _status2xxSensor
  = MeterService.createCountMeter("Caucho|Http|2xx");
  private static final CountSensor _status200Sensor
  = MeterService.createCountMeter("Caucho|Http|200");
  private static final CountSensor _status3xxSensor
  = MeterService.createCountMeter("Caucho|Http|3xx");
  private static final CountSensor _status304Sensor
  = MeterService.createCountMeter("Caucho|Http|304");
  private static final CountSensor _status4xxSensor
  = MeterService.createCountMeter("Caucho|Http|4xx");
  private static final CountSensor _status400Sensor
  = MeterService.createCountMeter("Caucho|Http|400");
  private static final CountSensor _status404Sensor
  = MeterService.createCountMeter("Caucho|Http|404");
  private static final CountSensor _status5xxSensor
  = MeterService.createCountMeter("Caucho|Http|5xx");
  private static final CountSensor _status500Sensor
  = MeterService.createCountMeter("Caucho|Http|500");
  private static final CountSensor _status503Sensor
  = MeterService.createCountMeter("Caucho|Http|503");

  //private static final CaseInsensitiveIntMap _headerCodes;

  private static final CharBuffer CACHE_CONTROL
  = new CharBuffer("cache-control");
  //private static final CharBuffer CONNECTION
  //= new CharBuffer("connection");
  //private static final CharBuffer CONTENT_LENGTH
  //= new CharBuffer("content-length");
  private static final CharBuffer DATE
  = new CharBuffer("date");
  private static final CharBuffer SERVER
  = new CharBuffer("server");

  private static final long MINUTE = 60 * 1000L;
  private static final long HOUR = 60 * MINUTE;

  private static final ConcurrentHashMap<String,ContentType> _contentTypeMap
  = new ConcurrentHashMap<>();
  
  private static final LruCache<CharBuffer,String> _nameCache
    = new LruCache<>(1024);
  
  private static final int HEADER_CACHE_CONTROL = 1;
  private static final int HEADER_CONTENT_TYPE = HEADER_CACHE_CONTROL + 1;
  private static final int HEADER_CONTENT_LENGTH = HEADER_CONTENT_TYPE + 1;
  private static final int HEADER_DATE = HEADER_CONTENT_LENGTH + 1;
  private static final int HEADER_SERVER = HEADER_DATE + 1;
  private static final int HEADER_CONNECTION = HEADER_SERVER + 1;

  private static DateTimeFormatter _dateFormatter;

  private final ProtocolHttp _protocolHttp;

  private final InvocationKey _invocationKey = new InvocationKey();

  // Connection stream
  //private final ReadStream _rawRead;
  // Stream for reading post contents
  //private final ReadStream _readStream;

  private final ArrayList<WebCookie> _cookies = new ArrayList<>();

  // Servlet input stream for post contents
  //private final ServletInputStreamImpl _is = new ServletInputStreamImpl(this);
  // Reader for post contents
  //private final BufferedReaderAdapter _bufferedReader;

  // private ErrorPageManager _errorManager;

  // Efficient date class for printing date headers
  // private final QDate _calendar = new QDate();
  private final CharBuffer _cbName = new CharBuffer();
  private final CharBuffer _cbValue = new CharBuffer();
  private final CharBuffer _cb = new CharBuffer();

  private byte []_smallUriBuffer = new byte[256];
  private char []_smallHeaderBuffer = new char[1024];
  private CharSegment []_smallHeaderKeys = new CharSegment[32];
  private CharSegment []_smallHeaderValues = new CharSegment[32];
  
  private HttpBufferStore _largeHttpBuffer;
  
  private char []_cBuffer = new char[1024];

  //private RequestFacade _requestFacade;

  //private ConnectionTcp _conn;
  //private ConnectionHttp _connHttp;
  
  private long _startTime;
  private long _expireTime;

  private CharSegment _hostHeader;
  private CharSegment _xForwardedHostHeader;
  private boolean _expect100Continue;

  private long _contentLengthIn = -1;
  // True if the post stream has been initialized
  private boolean _hasReadStream;
  // character incoding for a Post
  private String _readEncoding;

  private boolean _isUpgrade;
  
  //
  // output data
  //
  
  private int _statusCode = 200;
  private String _statusMessage = "OK";

  private final ArrayList<String> _headerKeysOut = new ArrayList<>();
  private final ArrayList<String> _headerValuesOut = new ArrayList<>();
  
  private String _contentTypeOut;
  private String _contentEncodingOut;

  private final ArrayList<String> _footerKeys = new ArrayList<>();
  private final ArrayList<String> _footerValues = new ArrayList<>();

  private OutHttpApp _responseStream;

  private final LogBuffer _logBuffer;

  private final byte []_dateBuffer = new byte[64];
  private final CharBuffer _dateCharBuffer = new CharBuffer();

  private int _dateBufferLength;
  private long _lastDate;

  private final byte []_logDateBuffer = new byte[64];
  private final CharBuffer _logDateCharBuffer = new CharBuffer();
  private int _logMinutesOffset;
  private int _logSecondsOffset;
  private int _logDateBufferLength;
  private long _lastLogDate;

  private boolean _isHeaderWritten;

  private String _serverHeader;
  private long _contentLengthOut;
  private boolean _isClosed;

  private ArrayList<CookieWeb> _cookiesOut;

  private boolean _isKeepalive;

  private RequestBaratine _request;

  private boolean _isChunkedIn;

  private Writer _writer;

  /**
   * Create a new Request.  Because the actual initialization occurs with
   * the start() method, this just allocates statics.
   *
   * @param server the parent server
   */
  protected RequestHttpBase(ProtocolHttp protocolHttp)
  {
    Objects.requireNonNull(protocolHttp);
    
    _protocolHttp = protocolHttp;
    
    //_rawRead = conn.getReadStream();

    //_readStream = new ReadStream();
    //_readStream.setReuseBuffer(true);

    //_bufferedReader = new BufferedReaderAdapter(_readStream);
    
    for (int i = 0; i < _smallHeaderKeys.length; i++) {
      _smallHeaderKeys[i] = new CharSegment();
      _smallHeaderValues[i] = new CharSegment();
    }

    int logSize = 256;
      
    if (getHttpContainer() != null) {
      logSize = getHttpContainer().getAccessLogBufferSize();
    }
      
    _logBuffer = new LogBuffer(logSize, true);
    
    _serverHeader = protocolHttp.serverHeader();
  }
  
  public void init(RequestBaratine requestWeb)
  {
    Objects.requireNonNull(requestWeb);
  
    _request = requestWeb;
    
    //_connHttp = _request.
  }
  
  /*
  public void init(ConnectionHttp connHttp)
  {
    Objects.requireNonNull(connHttp);
    
    _conn = connHttp.connTcp();
    _connHttp = connHttp;
  }
  */

  /**
   * Prepare the Request object for a new request.
   *
   * @param httpBuffer the raw connection stream
   */
  protected void initRequest()
  {
    _hostHeader = null;
    _xForwardedHostHeader = null;
    _expect100Continue = false;

    _cookies.clear();

    _contentLengthIn = -1;

    _hasReadStream = false;

    _readEncoding = null;

    //_request = request;
    //_requestFacade = getHttp().createFacade(this);
    
    _startTime = -1;
    _expireTime = -1;
    
    _isUpgrade = false;
    
    _statusCode = 200;
    _statusMessage = "OK";

    _headerKeysOut.clear();
    _headerValuesOut.clear();
    
    _contentTypeOut = null;
    _contentEncodingOut = null;
    _contentLengthOut = -1;

    _footerKeys.clear();
    _footerValues.clear();

    out().start();

    _isHeaderWritten = false;

    _isClosed = false;
    //_serverHeader = http().serverHeader();
    
    _isKeepalive = true;
  }
  
  public RequestBaratine request()
  {
    return _request;
  }
  
  public OutHttpProxy outProxy()
  {
    return connHttp().outProxy();
  }
  
  public ProtocolHttp protocolHttp()
  {
    return _protocolHttp;
  }
  
  public ConnectionHttp connHttp()
  {
    return request().connHttp();
  }

  /**
   * Returns the connection.
   */
  public final ConnectionTcp connTcp()
  {
    return connHttp().connTcp();
  }

  public HttpContainer http()
  {
    if (_protocolHttp != null) {
      return _protocolHttp.http();
    }
    else {
      return null;
    }
  }

  public final long connectionId()
  {
    return connTcp().id();
  }

  /**
   * returns the dispatch server.
   */
  public final InvocationManager getInvocationManager()
  {
    return http().getInvocationManager();
  }

  protected final CharBuffer getCharBuffer()
  {
    return _cb;
  }

  /*
  protected RequestFacade request()
  {
    throw new UnsupportedOperationException();
  }
  
  protected RequestFacade next()
  {
    return null;
  }
  */
  
  public void closeWrite()
  {
  }

  public Invocation parseInvocation()
    throws IOException
  {
    return null;
  }

  public boolean readBodyChunk()
    throws IOException
  {
    return false;
  }

  protected void clearRequest()
  {
    //_requestFacade = null;
  }

  /**
   * Returns true if a request has been set
   */
  /*
  public boolean hasRequest()
  {
    //return _requestFacade != null;
    return false;
  }
  */

  protected final byte []getSmallUriBuffer()
  {
    return _smallUriBuffer;
  }
  
  protected final char []getSmallHeaderBuffer()
  {
    return _smallHeaderBuffer;
  }
  
  protected final CharSegment []getSmallHeaderKeys()
  {
    return _smallHeaderKeys;
  }
  
  protected final CharSegment []getSmallHeaderValues()
  {
    return _smallHeaderValues;
  }
  
  /**
   * Returns the http buffer store
   */
  protected final HttpBufferStore getHttpBufferStore()
  {
    return _largeHttpBuffer;
  }
  
  protected final HttpBufferStore allocateHttpBufferStore()
  {
    if (_largeHttpBuffer != null) {
      throw new IllegalStateException();
    }

    _largeHttpBuffer = http().allocateHttpBuffer();
    
    return _largeHttpBuffer;
  }

  /*
  public WriteStream getRawWrite()
  {
    return _conn.getWriteStream();
  }
  */

  public abstract byte []uriBuffer();

  public abstract int uriLength();

  /**
   * Returns true if client disconnects should be ignored.
   */
  public boolean isIgnoreClientDisconnect()
  {
    // server/183c

    return http().isIgnoreClientDisconnect();
  }
  
  protected HttpContainer getHttpContainer()
  {
    return http();
  }

  /**
   * Returns true if the client has disconnected
   */
  public boolean isConnectionClosed()
  {
    ConnectionTcp conn = connTcp();
    
    if (conn != null)
      return conn.isClosed();
    else
      return false;
  }

  /**
   * Called when the client has disconnected
   */
  public void clientDisconnect()
  {
    try {
      OutHttpApp responseStream = _responseStream;
      
      if (responseStream != null) {
        responseStream.close();
      }
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
    
    ConnectionTcp conn = connTcp();

    if (conn != null) {
      conn.clientDisconnect();
    }

    killKeepalive("client disconnect");
  }
 
  protected CharSegment getHostHeader()
  {
    return _hostHeader;
  }
  
  protected CharSegment getForwardedHostHeader()
  {
    return _xForwardedHostHeader;
  }

  protected CharSequence getHost()
  {
    return null;
  }

  /**
   * Returns the server's port.
   */
  public int getServerPort()
  {
    String host = null;

    CharSequence rawHost;
    if ((rawHost = getHost()) != null) {
      int length = rawHost.length();
      int i;

      for (i = length - 1; i >= 0; i--) {
        if (rawHost.charAt(i) == ':') {
          int port = 0;

          for (i++; i < length; i++) {
            char ch = rawHost.charAt(i);

            if ('0' <= ch && ch <= '9') {
              port = 10 * port + ch - '0';
            }
          }

          return port;
        }
      }

      // server/0521 vs server/052o
      // because of proxies, need to use the host header,
      // not the actual port
      return isSecure() ? 443 : 80;
    }

    if (host == null) {
      return connTcp().portLocal();
    }

    int p1 = host.lastIndexOf(':');

    if (p1 < 0)
      return isSecure() ? 443 : 80;
    else {
      int length = host.length();
      int port = 0;

      for (int i = p1 + 1; i < length; i++) {
        char ch = host.charAt(i);

        if ('0' <= ch && ch <= '9') {
          port = 10 * port + ch - '0';
        }
      }

      return port;
    }
  }

  /**
   * Returns the local port.
   */
  /*
  public int getLocalPort()
  {
    return _conn.portLocal();
  }
  */

  /**
   * Returns the server's address.
   */
  /*
  public String getLocalHost()
  {
    return _conn.ipLocal().getHostName();
  }

  public String getRemoteAddr()
  {
    return _conn.addressRemote();
  }

  public int printRemoteAddr(byte []buffer, int offset)
    throws IOException
  {
    int len = _conn.addressRemote(buffer, offset, buffer.length - offset);

    return offset + len;
  }
  */

  /*
  public String remoteHost()
  {
    return _conn.addressRemote();
  }
  */

  /**
   * Returns the local port.
   */
  /*
  public int getRemotePort()
  {
    return _conn.portRemote();
  }
  */

  /**
   * Returns the request's scheme.
   */
  public String scheme()
  {
    return isSecure() ? "https" : "http";
  }

  abstract public String getProtocol();

  abstract public String method();

  /**
   * Returns the named header.
   *
   * @param key the header key
   */
  abstract public String header(String key);

  protected boolean isUpgrade()
  {
    return _isUpgrade;
  }
  
  /**
   * Returns the number of headers.
   */
  public int getHeaderSize()
  {
    return -1;
  }

  /**
   * Returns the header key
   */
  public CharSegment getHeaderKey(int index)
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the header value
   */
  public CharSegment getHeaderValue(int index)
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Fills the result with the header values as
   * CharSegment values.  Most implementations will
   * implement this directly.
   *
   * @param name the header name
   */
  public CharSegment getHeaderBuffer(String name)
  {
    String value = header(name);

    if (value != null)
      return new CharBuffer(value);
    else
      return null;
  }

  /**
   * Enumerates the header keys
   */
  //abstract public Enumeration<String> getHeaderNames();

  /**
   * Sets the header.  setHeader is used for
   * Resin's caching to simulate If-None-Match.
   */
  public void setHeader(String key, String value)
  {
  }

  /**
   * Adds the header, checking for known values.
   */
  protected boolean addHeaderInt(char []keyBuf, int keyOff, int keyLen,
                                 CharSegment value)
  {
    if (keyLen < 4)
      return true;

    int key1 = keyBuf[keyOff] | 0x20 | (keyLen << 8);
    
    switch (key1) {
    case CONNECTION_KEY:
      if (match(keyBuf, keyOff, keyLen, CONNECTION)) {
        char []valueBuffer = value.buffer();
        int valueOffset = value.offset();
        int valueLength = value.length();
        boolean isKeepalive = false;

        switch (valueBuffer[valueOffset]) {
        case 'k':
        case 'K':
          if (match(valueBuffer, valueOffset, valueLength, KEEPALIVE)) {
            isKeepalive = true;
          }
          break;
          
        case 'u':
        case 'U':
          if (match(valueBuffer, valueOffset, UPGRADE.length, UPGRADE)) {
            _isUpgrade = true;
          }
          break;
        }
        
        _isKeepalive = isKeepalive;
        return true;
      }
      
    case COOKIE_KEY:
      if (match(keyBuf, keyOff, keyLen, COOKIE)) {
        fillCookie(_cookies, value);
      }
      return true;
      
    case CONTENT_LENGTH_KEY:
      if (match(keyBuf, keyOff, keyLen, CONTENT_LENGTH)) {
        contentLengthIn(value);
      }
      return true;

    case EXPECT_KEY:
      if (match(keyBuf, keyOff, keyLen, EXPECT)) {
        if (match(value.buffer(), value.offset(), value.length(),
                  CONTINUE_100)) {
          _expect100Continue = true;
          return false;
        }
      }

      return true;

    case HOST_KEY:
      if (match(keyBuf, keyOff, keyLen, HOST)) {
        _hostHeader = value;
      }
      return true;
      
    case TRANSFER_ENCODING_KEY:
      if (match(keyBuf, keyOff, keyLen, TRANSFER_ENCODING)) {
        _isChunkedIn = true;
      }
      return true;
      
    case X_FORWARDED_HOST_KEY:
      if (match(keyBuf, keyOff, keyLen, X_FORWARDED_HOST)) {
        _xForwardedHostHeader = value;
      }
      return true;
      

    default:
      return true;
    }
  }

  protected void contentLengthIn(CharSegment value)
  {
    long contentLength = 0;
    int ch;
    int i = 0;

    int length = value.length();
    for (;
         i < length && (ch = value.charAt(i)) >= '0' && ch <= '9';
         i++) {
      contentLength = 10 * contentLength + ch - '0';
    }

    if (i > 0)
      _contentLengthIn = contentLength;
  }

  /**
   * Called for a connection: close
   */
  protected void handleConnectionClose()
  {
    ConnectionTcp conn = connTcp();

    if (conn != null) {
      killKeepalive("client Connection: close");
    }
  }

  /**
   * Matches case insensitively, with the second normalized to lower case.
   */
  private boolean match(char []a, int aOff, int aLength, char []b)
  {
    int bLength = b.length;

    if (aLength != bLength)
      return false;

    for (int i = aLength - 1; i >= 0; i--) {
      char chA = a[aOff + i];
      char chB = b[i];

      if (chA != chB && chA + 'a' - 'A' != chB) {
        return false;
      }
    }

    return true;
  }

  /**
   * Returns an enumeration of the headers for the named attribute.
   *
   * @param name the header name
   */
  public Enumeration<String> getHeaders(String name)
  {
    String value = header(name);
    
    if (value == null) {
      return Collections.emptyEnumeration();
    }

    ArrayList<String> list = new ArrayList<String>();
    list.add(value);

    return Collections.enumeration(list);
  }

  /**
   * Fills the result with a list of the header values as
   * CharSegment values.  Most implementations will
   * implement this directly.
   *
   * @param name the header name
   * @param resultList the resulting buffer
   */
  public void getHeaderBuffers(String name, ArrayList<CharSegment> resultList)
  {
    String value = header(name);

    if (value != null)
      resultList.add(new CharBuffer(value));
  }

  /**
   * Returns the named header, converted to an integer.
   *
   * @param key the header key.
   *
   * @return the value of the header as an integer.
   */
  public int getIntHeader(String key)
  {
    CharSegment value = getHeaderBuffer(key);

    if (value == null)
      return -1;

    int len = value.length();
    if (len == 0)
      throw new NumberFormatException(value.toString());

    int iValue = 0;
    int i = 0;
    int ch = value.charAt(i);
    int sign = 1;
    if (ch == '+') {
      if (i + 1 < len)
        ch = value.charAt(++i);
      else
        throw new NumberFormatException(value.toString());
    } else if (ch == '-') {
      sign = -1;
      if (i + 1 < len)
        ch = value.charAt(++i);
      else
        throw new NumberFormatException(value.toString());
    }

    for (; i < len && (ch = value.charAt(i)) >= '0' && ch <= '9'; i++)
      iValue = 10 * iValue + ch - '0';

    if (i < len)
      throw new NumberFormatException(value.toString());

    return sign * iValue;
  }

  /**
   * Returns the content length of a post.
   */
  public long contentLength()
  {
    return _contentLengthIn;
  }

  /**
   * Returns the content-type of a post.
   */
  public String contentType()
  {
    return header("Content-Type");
  }

  /**
   * Returns the content-length of a post.
   */
  public CharSegment getContentTypeBuffer()
  {
    return getHeaderBuffer("Content-Type");
  }

  /**
   * Returns the character encoding of a post.
   */
  public String encoding()
  {
    if (_readEncoding != null)
      return _readEncoding;

    CharSegment value = getHeaderBuffer("Content-Type");

    if (value == null)
      return null;

    int i = value.indexOf("charset");
    if (i < 0)
      return null;

    int len = value.length();
    for (i += 7; i < len && Character.isWhitespace(value.charAt(i)); i++) {
    }

    if (i >= len || value.charAt(i) != '=')
      return null;

    for (i++; i < len && Character.isWhitespace(value.charAt(i)); i++) {
    }

    if (i >= len)
      return null;

    char end = value.charAt(i);
    if (end == '"') {
      int tail;
      for (tail = ++i; tail < len; tail++) {
        if (value.charAt(tail) == end)
          break;
      }

      _readEncoding = Encoding.getMimeName(value.substring(i, tail));

      return _readEncoding;
    }

    int tail;
    for (tail = i; tail < len; tail++) {
      if (Character.isWhitespace(value.charAt(tail))
          || value.charAt(tail) == ';')
        break;
    }

    _readEncoding = Encoding.getMimeName(value.substring(i, tail));

    return _readEncoding;
  }

  /**
   * Sets the character encoding of a post.
   */
  /*
  public void setCharacterEncoding(String encoding)
    throws UnsupportedEncodingException
  {
    // server/122k (tck)

    if (_hasReadStream)
      return;

    _readEncoding = encoding;

    try {
      // server/122d (tck)
      //if (_hasReadStream)

      _readStream.setEncoding(_readEncoding);
    } catch (UnsupportedEncodingException e) {
      throw e;
    } catch (java.nio.charset.UnsupportedCharsetException e) {
      throw new UnsupportedEncodingException(e.getMessage());
    }
  }
  */

  /**
   * Returns the cookies from the browser
   */
  public CookieWeb []cookies()
  {
    return fillCookies();

    /*
    // The page varies depending on the presense of any cookies
    setVaryCookie(null);

    if (_cookiesIn == null)
      fillCookies();

    // If any cookies actually exist, the page is not anonymous
    if (_cookiesIn != null && _cookiesIn.length > 0)
      setHasCookie();

    if (_cookiesIn == null || _cookiesIn.length == 0)
      return null;
    else
      return _cookiesIn;
    */
  }

  /**
   * Parses cookie information from the cookie headers.
   */
  CookieWeb []fillCookies()
  {
    int size = _cookies.size();

    if (size > 0) {
      CookieWeb []cookiesIn = new WebCookie[size];

      for (int i = size - 1; i >= 0; i--) {
        cookiesIn[i] = _cookies.get(i);
      }

      return cookiesIn;
    }
    else {
      return NULL_COOKIES;
    }
  }
  
  protected void addCookie(String cookie)
  {
    _cb.clear();
    _cb.append(cookie);
    
    fillCookie(_cookies, _cb);
  }

  /**
   * Parses a single cookie
   *
   * @param cookies the array of cookies read
   * @param rawCookie the input for the cookie
   */
  private void fillCookie(ArrayList<WebCookie> cookies, CharSegment rawCookie)
  {
    char []buf = rawCookie.buffer();
    int j = rawCookie.offset();
    int end = j + rawCookie.length();
    int version = 0;
    WebCookie cookie = null;

    while (j < end) {
      char ch = 0;

      CharBuffer cbName = _cbName;
      CharBuffer cbValue = _cbValue;

      cbName.clear();
      cbValue.clear();

      for (;
           j < end && ((ch = buf[j]) == ' ' || ch == ';' || ch ==',');
           j++) {
      }

      if (end <= j)
        break;

      boolean isSpecial = false;
      if (buf[j] == '$') {
        isSpecial = true;
        j++;
      }

      for (; j < end; j++) {
        ch = buf[j];
        if (ch < 128 && TOKEN[ch])
          cbName.append(ch);
        else
          break;
      }

      for (; j < end && (ch = buf[j]) == ' '; j++) {
      }

      if (end <= j)
        break;
      else if (ch == ';' || ch == ',') {
        try {
          cookie = new WebCookie(cbName.toString(), "");
          cookie.setVersion(version);
          _cookies.add(cookie);
          // some clients can send bogus cookies
        } catch (Exception e) {
          log.log(Level.FINE, e.toString(), e);
        }
        continue;
      }
      else if (ch != '=') {
        for (; j < end && (ch = buf[j]) != ';'; j++) {
        }
        continue;
      }

      j++;

      for (; j < end && (ch = buf[j]) == ' '; j++) {
      }

      if (ch == '"') {
        for (j++; j < end; j++) {
          ch = buf[j];
          if (ch == '"')
            break;
          cbValue.append(ch);
        }
        j++;
      }
      else {
        int head = j;
        int tail = j;
        
        for (; j < end; j++) {
          ch = buf[j];
          if (ch < 128 && VALUE[ch]) {
            cbValue.append(ch);
            tail = j + 1;
          }
          else if (ch == ' ') {
            cbValue.append(ch);
            // server/01ed
          }
          else {
            break;
          }
        }
        
        cbValue.length(tail - head);
      }

      if (! isSpecial) {
        if (cbName.length() == 0)
          log.warning("bad cookie: " + rawCookie);
        else {
          cookie = new WebCookie(toName(cbName), cbValue.toString());
          cookie.setVersion(version);
          _cookies.add(cookie);
        }
      }
      else if (cookie == null) {
        if (cbName.matchesIgnoreCase("Version"))
          version = cbValue.charAt(0) - '0';
      }
      else if (cbName.matchesIgnoreCase("Version"))
        cookie.setVersion(cbValue.charAt(0) - '0');
      else if (cbName.matchesIgnoreCase("Domain"))
        cookie.setDomain(cbValue.toString());
      else if (cbName.matchesIgnoreCase("Path"))
        cookie.setPath(cbValue.toString());
    }
  }
  
  private String toName(CharBuffer cb)
  {
    String value = _nameCache.get(cb);
    
    if (value == null) {
      value = cb.toString();
      
      cb = new CharBuffer(value);
      
      _nameCache.put(cb, value);
    }
    
    return value;
  }

  /**
   * For SSL connections, use the SSL identifier.
   */
  public String findSessionIdFromConnection()
  {
    return null;
  }

  /**
   * Returns true if the transport is secure.
   */
  /*
  public boolean isTransportSecure()
  {
    return _conn.isSecure();
  }
  */

  /*
  protected void initAttributes(RequestFacade facade)
  {
  }
  */

  //
  // security
  //

  /**
   * Returns true if the request is secure.
   */
  public boolean isSecure()
  {
    return connTcp().isSecure();
  }

  /**
   * Returns key-size
   */
  /*
  public int secureKeySize()
  {
    return _conn.keySize();
  }
  */

  //
  // internal methods
  //

  /**
   * Returns the date for the current request.
   */
  public final long getStartTime()
  {
    return _startTime;
  }

  /**
   * Returns the log buffer.
   */
  /*
  public final byte []getLogBuffer()
  {
    return _httpBuffer.getLogBuffer();
  }
  */
  
  public final void onAttachThread()
  {
  }
  
  public final void onDetachThread()
  {
    HttpBufferStore httpBuffer = _largeHttpBuffer;
    _largeHttpBuffer = null;
    
    if (httpBuffer != null) {
      http().freeHttpBuffer(httpBuffer);
    }
  }

  protected InvocationBaratine invocation(CharSequence host,
                                     byte []uri,
                                     int uriLength)
    throws IOException
  {
    _invocationKey.init(isSecure(),
                        host, getServerPort(),
                        uri, uriLength);

    InvocationManager<InvocationBaratine> server
      = (InvocationManager) http().getInvocationManager();

    InvocationBaratine invocation = server.getInvocation(_invocationKey);

    if (invocation != null) {
      //return invocation.getRequestInvocation(_requestFacade);
      return invocation;
    }

    invocation = server.createInvocation();
    invocation.setSecure(isSecure());

    if (host != null) {
      String hostName = host.toString().toLowerCase(Locale.ENGLISH);

      invocation.setHost(hostName);
      invocation.setPort(getServerPort());

      // Default host name if the host doesn't have a canonical
      // name
      int p = hostName.lastIndexOf(':');
      int q = hostName.lastIndexOf(']');
      
      if (p > 0 && q < p) {
        invocation.setHostName(hostName.substring(0, p));
      }
      else {
        invocation.setHostName(hostName);
      }
    }

    return buildInvocation(invocation, uri, uriLength);
  }

  protected <I extends Invocation> I buildInvocation(I invocation,
                                                     byte []uri,
                                                     int uriLength)
    throws IOException
  {
    HttpContainer http = http();
    
    InvocationManager<I> manager
      = (InvocationManager) http.getInvocationManager();
    InvocationDecoder decoder = manager.getInvocationDecoder();

    decoder.splitQueryAndUnescape(invocation, uri, uriLength);

    /*
    if (httpSystem.isModified()) {
      httpSystem.logModified(log);

      _requestFacade.setInvocation(invocation);
      invocation.setWebApp(httpSystem.getErrorWebApp());

      HttpServletResponse res = _responseFacade;
      res.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);

      httpSystem.restart();

      return null;
    }
    */

    invocation = manager.buildInvocation(_invocationKey.clone(), invocation);

    //return invocation.getRequestInvocation(_requestFacade);
    
    return invocation;
  }

  /**
   * Handles a timeout.
   */
  public void onTimeout()
  {
  }

  /**
   * Starts duplex mode.
   */
  public void startDuplex(ConnectionProtocol request)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  protected void sendRequestError(Throwable e)
    throws IOException
  {
    killKeepalive("request error: " + e);
    
    //getHttp().sendRequestError(e, request());
    if (true) throw new UnsupportedOperationException();
  }

  /**
   * Kills the keepalive.
   */
  public void killKeepalive(String reason)
  {
    ConnectionTcp conn = connTcp();

    _isKeepalive = false;
    /* XXX:
    if (conn != null) {
      conn.killKeepalive(reason);
    }
    */
  }

  /**
   * Returns true if the keepalive is active.
   */
  public boolean isKeepalive()
  {
    //ConnectionTcp conn = _conn;
    
    // return conn != null && conn.isKeepaliveAllocated();
    return _isKeepalive;
  }

  /*
  public boolean isSuspend()
  {
    // return _conn != null && (_conn.isCometActive() || _conn.isDuplex());
    return false;
  }

  public boolean isAsync()
  {
    return isSuspend();
  }

  public boolean isDuplex()
  {
    // return _conn != null && _conn.isDuplex();
    return false;
  }
  */

  /*
  protected HashMapImpl<String,String[]> getForm()
  {
    _form.clear();

    return _form;
  }

  protected Form getFormParser()
  {
    return _formParser;
  }
  */

  /**
   * Restarts the server.
   */
  /*
  protected void restartServer()
  {
  }
  */

  /**
   * Prepare the Request object for a new request.
   *
   */
  /*
  protected void startInvocation()
    throws IOException
  {
    _startTime = CurrentTime.getExactTime();
    
    ConnectionTcp tcpConn = _conn;
    
    if (tcpConn != null) {
      long requestTimeout = tcpConn.port().getRequestTimeout();
    
      if (requestTimeout > 0)
        _expireTime = _startTime + requestTimeout;
      else
        _expireTime = Long.MAX_VALUE / 2;
    }
  }
  */

  public void onCloseConnection()
  {
    try {
      finishRequest();
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);
    }
    
    HttpBufferStore httpBuffer = _largeHttpBuffer;
    _largeHttpBuffer = null;
      
    if (httpBuffer != null) {
      http().freeHttpBuffer(httpBuffer);
    }
  }

  /**
   * Cleans up at the end of the request
   */
  private void finishRequest()
    throws IOException
  {
    try {
      cleanup();
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    } finally {
      //_requestFacade = null;
    }
  }

  public void cleanup()
  {
    _cookies.clear();
  }

  /**
   * Called by server shutdown to kill any active threads
   */
  public void shutdown()
  {
  }
  
  //
  // output
  //
  
  /**
   * Returns true for closed requests.
   */
  public boolean isClosed()
  {
    return _isClosed;
  }
  
  abstract protected OutHttpApp createOut();

  /**
   * Gets the response stream.
   */
  public final OutHttpApp out()
  {
    OutHttpApp stream = _responseStream;
    
    if (stream == null) {
      stream = createOut();
      _responseStream = stream;
    }
    
    
    return stream;
  }
  
  @Override
  public void writeOut(byte []buffer, int offset, int length)
  {
    out().write(buffer, offset, length);
  }
  
  public final Writer writer(OutputStreamWithBuffer out)
  {
    Writer writer = _writer;
    
    if (writer == null) {
      String encoding = _contentEncodingOut;
      
      if (encoding == null || encoding.equals("utf-8")) {
        writer = new WriterUtf8(out, _cBuffer);
      }
      else {
        try {
          writer = new OutputStreamWriter(out, encoding);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
      
      _writer = writer;
    }
    
    return writer;
  }
  
  public void writerClose()
  {
    Writer writer = _writer;
    _writer = null;
  
    if (writer != null) {
      try {
        writer.close();
      } catch (IOException e) {
        log.log(Level.FINEST, e.toString(), e);
      }
    }
  }
  
  public final void freeResponseStream()
  {
    OutHttpApp responseStream = _responseStream;
    _responseStream = null;
    
    //_responseOutputStream.init(null);
    //_responsePrintWriter.init(null);
    
    if (responseStream != null) {
      freeResponseStream(responseStream);
    }
  }
  
  protected void freeResponseStream(OutHttpApp stream)
  {
  }

  /**
   * For a HEAD request, the response stream should write no data.
   */
  protected void setHead()
  {
    out().toHead();
  }

  /**
   * For a HEAD request, the response stream should write no data.
   */
  protected final boolean isHead()
  {
    return out().isHead();
  }
  
  //
  // status
  //
  
  public void status(int code, String message)
  {
    if (code < 100 || code >= 600) {
      throw new IllegalArgumentException(String.valueOf(code));
    }
    Objects.requireNonNull(message);
    
    _statusCode = code;
    _statusMessage = message;
  }
  
  protected int status()
  {
    return _statusCode;
  }
  
  protected String statusMessage()
  {
    return _statusMessage;
  }

  //
  // headers
  //

  /**
   * Returns true if the response already contains the named header.
   *
   * @param name name of the header to test.
   */
  public boolean containsHeaderOut(String name)
  {
    ArrayList<String> headerKeys = _headerKeysOut;
    int size = headerKeys.size();
    
    for (int i = 0; i < size; i++) {
      String oldKey = headerKeys.get(i);

      if (oldKey.equalsIgnoreCase(name)) {
        return true;
      }
    }

    if (name.equalsIgnoreCase("content-type")) {
      return _contentTypeOut != null;
    }

    if (name.equalsIgnoreCase("content-length")) {
      return _contentLengthOut >= 0;
    }

    return false;
  }

  /**
   * Returns the value of an already set output header.
   *
   * @param name name of the header to get.
   */
  public String headerOut(String name)
  {
    ArrayList<String> keys = _headerKeysOut;

    int headerSize = keys.size();
    for (int i = 0; i < headerSize; i++) {
      String oldKey = keys.get(i);

      if (oldKey.equalsIgnoreCase(name)) {
        return (String) _headerValuesOut.get(i);
      }
    }

    /*
    if (name.equalsIgnoreCase("content-type")) {
      throw new UnsupportedOperationException();
      //return request().getContentType();
    }
    */

    if (name.equalsIgnoreCase("content-length")) {
      return _contentLengthOut >= 0 ? String.valueOf(_contentLengthOut) : null;
    }
    
    if (name.equalsIgnoreCase("content-type")) {
      return _contentTypeOut;
    }

    return null;
  }

  /**
   * Sets a header, replacing an already-existing header.
   *
   * @param key the header key to set.
   * @param value the header value to set.
   */
  public void headerOut(String key, String value)
  {
    Objects.requireNonNull(value);
    
    if (isOutCommitted()) {
      return;
    }

    if (headerOutSpecial(key, value)) {
      return;
    }
    
    setHeaderOutImpl(key, value);
  }


  /**
   * Sets a header, replacing an already-existing header.
   *
   * @param key the header key to set.
   * @param value the header value to set.
   */
  protected void setHeaderOutImpl(String key, String value)
  {
    int i = 0;
    boolean hasHeader = false;
    
    ArrayList<String> keys = _headerKeysOut;
    ArrayList<String> values = _headerValuesOut;

    for (i = keys.size() - 1; i >= 0; i--) {
      String oldKey = keys.get(i);

      if (oldKey.equalsIgnoreCase(key)) {
        if (hasHeader) {
          keys.remove(i);
          values.remove(i);
        }
        else {
          hasHeader = true;

          values.set(i, value);
        }
      }
    }

    if (! hasHeader) {
      keys.add(key);
      values.add(value);
    }
  }

  /**
   * Adds a new header.  If an old header with that name exists,
   * both headers are output.
   *
   * @param key the header key.
   * @param value the header value.
   */
  public void addHeaderOut(String key, String value)
  {
    // server/05e8 (tck)
    if (isOutCommitted()) {
      return;
    }

    addHeaderOutImpl(key, value);
  }

  /**
   * Adds a new header.  If an old header with that name exists,
   * both headers are output.
   *
   * @param key the header key.
   * @param value the header value.
   */
  public void addHeaderOutImpl(String key, String value)
  {
    if (headerOutSpecial(key, value)) {
      return;
    }
    
    ArrayList<String> keys = _headerKeysOut;
    ArrayList<String> values = _headerValuesOut;
    
    int size = keys.size();
    
    // webapp/1k32
    for (int i = 0; i < size; i++) {
      if (keys.get(i).equals(key) && values.get(i).equals(value)) {
        return;
      }
    }

    keys.add(key);
    values.add(value);
  }

  protected static ContentType parseContentType(String contentType)
  {
    ContentType item = _contentTypeMap.get(contentType);

    if (item == null) {
      item = new ContentType(contentType);

      _contentTypeMap.put(contentType, item);
    }

    return item;
  }

  /**
   * Special processing for a special value.
   */
  private boolean headerOutSpecial(String key, String value)
  {
    int length = key.length();
    
    if (length == 0) {
      return false;
    }
    
    int ch = key.charAt(0);
    
    if ('A' <= ch && ch <= 'Z') {
      ch += 'a' - 'A';
    }
    
    int code = (length << 8) + ch;
    
    switch (code) {
    case 0x0d00 + 'c':
      if (CACHE_CONTROL.matchesIgnoreCase(key)) {
        // server/13d9, server/13dg
        if (value.startsWith("max-age")) {
        }
        else if (value.startsWith("s-maxage")) {
        }
        else if (value.equals("x-anonymous")) {
        }
        else {
          //request().setCacheControl(true);
          if (true) throw new UnsupportedOperationException();
        }
      }

      return false;

    case 0x0a00 + 'c':
      if (CONNECTION_CB.matchesIgnoreCase(key)) {
        if ("close".equalsIgnoreCase(value))
          killKeepalive("client connection: close");
        return true;
      }
      else {
        return false;
      }

    case 0x0c00 + 'c':
      if (CONTENT_TYPE_CB.matchesIgnoreCase(key)) {
        headerOutContentType(value);
        
        return true;
      }
      else {
        return false;
      }

    case 0x0e00 + 'c':
      if (CONTENT_LENGTH_CB.matchesIgnoreCase(key)) {
        // server/05a8
        // php/164v
        _contentLengthOut = parseLong(value);
        return true;
      }
      else {
        return false;
      }

    case 0x0400 + 'd':
      if (DATE.matchesIgnoreCase(key)) {
        return true;
      }
      else {
        return false;
      }

    case 0x0600 + 's':
      if (SERVER.matchesIgnoreCase(key)) {
        _serverHeader = value;
        return true;
      }
      else {
        return false;
      }

    default:
      return false;
    }
  }
  
  public void headerOutContentType(String value)
  {
    ContentType contentType = parseContentType(value);
    
    _contentTypeOut = contentType.contentType();
    
    if (contentType.encoding() != null) {
      headerOutContentEncoding(contentType.encoding());
    }
    else if (contentType.encodingDefault() != null
             && _contentEncodingOut == null) {
      headerOutContentEncoding(contentType.encodingDefault());
    }
  }
  
  public void headerOutContentEncoding(String encoding)
  {
    _contentEncodingOut = encoding;
  }
  
  protected String headerOutContentType()
  {
    return _contentTypeOut;
  }
  
  protected String headerOutContentEncoding()
  {
    return _contentEncodingOut;
  }
  
  private static long parseLong(String string)
  {
    int length = string.length();
   
    int i;
    int ch = 0;
    for (i = 0;
         i < length && Character.isWhitespace((ch = string.charAt(i)));
         i++) {
    }
    
    int sign = 1;
    long value = 0;
    
    if (ch == '-') {
      sign = -1;
      
      if (i < length) {
        ch = string.charAt(i++);
      }
    }
    else if (ch == '+') {
      if (i < length) {
        ch = string.charAt(i++);
      }
    }
    
    if (! ('0' <= ch && ch <= '9')) {
      throw new IllegalArgumentException(L.l("'{0}' is an invalid content-length",
                                             string));
    }
    
    for (;
         i < length && '0' <= (ch = string.charAt(i)) && ch <= '9';
         i++) {
      value = 10 * value + ch - '0';
    }

    return sign * value;
  }

  public void removeHeader(String key)
  {
    if (isOutCommitted()) {
      return;
    }
    
    ArrayList<String> keys = _headerKeysOut;
    ArrayList<String> values = _headerValuesOut;

    for (int i = keys.size() - 1; i >= 0; i--) {
      String oldKey = keys.get(i);

      if (oldKey.equalsIgnoreCase(key)) {
        keys.remove(i);
        values.remove(i);
        return;
      }
    }
  }

  public ArrayList<String> headerKeysOut()
  {
    return _headerKeysOut;
  }

  public ArrayList<String> headerValuesOut()
  {
    return _headerValuesOut;
  }

  public Collection<String> headersOut(String name)
  {
    ArrayList<String> headers = new ArrayList<String>();

    for (int i = 0; i < _headerKeysOut.size(); i++) {
      String key = _headerKeysOut.get(i);

      if (key.equals(name))
        headers.add(_headerValuesOut.get(i));
    }

    return headers;
  }

  public Collection<String> headerNamesOut()
  {
    return new HashSet<String>(_headerKeysOut);
  }

  public ArrayList<String> footerKeysOut()
  {
    return _footerKeys;
  }

  public ArrayList<String> footerValuesOut()
  {
    return _footerValues;
  }

  /**
   * Sets the content length of the result.  In general, Resin will handle
   * the content length, but for things like long downloads adding the
   * length will give a valuable hint to the browser.
   *
   * @param length the length of the content.
   */
  public void contentLengthOut(long length)
  {
    _contentLengthOut = length;
  }

  /**
   * Returns the value of the content-length header.
   */
  public final long contentLengthOut()
  {
    return _contentLengthOut;
  }
  
  public String serverHeader()
  {
    return _serverHeader;
  }

  /**
   * Sets a footer, replacing an already-existing footer
   *
   * @param key the header key to set.
   * @param value the header value to set.
   */
  public void setFooter(String key, String value)
  {
    Objects.requireNonNull(value);

    int i = 0;
    boolean hasFooter = false;

    for (i = _footerKeys.size() - 1; i >= 0; i--) {
      String oldKey = _footerKeys.get(i);

      if (oldKey.equalsIgnoreCase(key)) {
        if (hasFooter) {
          _footerKeys.remove(i);
          _footerValues.remove(i);
        }
        else {
          hasFooter = true;

          _footerValues.set(i, value);
        }
      }
    }

    if (! hasFooter) {
      _footerKeys.add(key);
      _footerValues.add(value);
    }
  }

  /**
   * Adds a new footer.  If an old footer with that name exists,
   * both footers are output.
   *
   * @param key the footer key.
   * @param value the footer value.
   */
  public void addFooter(String key, String value)
  {
    if (headerOutSpecial(key, value)) {
      return;
    }

    _footerKeys.add(key);
    _footerValues.add(value);
  }

  protected boolean hasFooter()
  {
    return _footerKeys.size() > 0;
  }

  public void cookie(CookieWeb cookie)
  {
    if (_cookiesOut == null) {
      _cookiesOut = new ArrayList<>();
    }
    
    _cookiesOut.add(cookie);
  }
  
  protected ArrayList<CookieWeb> cookiesOut()
  {
    return _cookiesOut;
  }

  /**
   * Returns true if some data has been sent to the browser.
   */
  public final boolean isOutCommitted()
  {
    OutHttpApp stream = out();
    
    if (stream.isCommitted()) {
      return true;
    }

    // server/05a7
    if (_contentLengthOut > 0 && _contentLengthOut <= stream.contentLength()) {
      return true;
    }

    return false;
  }

  protected void resetOut()
  {
    if (isOutCommitted()) {
      return;
    }
    
    _headerKeysOut.clear();
    _headerValuesOut.clear();

    _contentLengthOut = -1;
  }

  public void upgrade()
  {

  }

  /**
   * Returns the number of bytes sent to the output.
   */
  public long contentLengthSent()
  {
    OutHttpApp stream = _responseStream;
    
    // stream can be null for duplex (websocket)
    if (stream != null) {
      return stream.contentLength();
    }
    else {
      return Math.max(_contentLengthOut, 0);
    }
  }

  /**
   * Returns true if the headers have been written.
   */
  public boolean isHeaderWritten()
  {
    return _isHeaderWritten;
  }

  /**
   * Returns true if the headers have been written.
   */
  public void setHeaderWritten(boolean isWritten)
  {
    _isHeaderWritten = isWritten;
  }
  
  //
  // http writer methods
  //

  /**
   * Writes the continue
   */
  final void writeContinue()
    throws IOException
  {
    if (! isHeaderWritten()) {
      // writeContinueInt(_rawWrite);
      // _rawWrite.flush();

      writeContinueInt();
    }
  }

  /**
   * Writes the continue from the writer thread.
   */
  protected void writeContinueInt()
    throws IOException
  {
  }
  
  /*
  @Override
  public void writeFirst(OutHttpProxy out,
                         TempBuffer buffer, 
                         long length, 
                         boolean isEnd)
  {
    System.out.println("FIRST: " + this);
    
    if (! isEnd) {
      buffer.freeSelf();
    }
  }

  @Override
  public void writeNext(OutHttpProxy out,
                        TempBuffer buffer, 
                        boolean isEnd)
  {
    System.out.println("NEXT: " + this);
    
    if (! isEnd) {
      buffer.freeSelf();
    }
  }
  */
  
  @Override
  public void disconnect(WriteStream out)
  {
    try {
      out.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  protected void fillHeaders()
  {
    /*
    RequestFacade res = request();
    
    addSensorCount(res.getStatus());
    
    res.fillHeaders();
    */
  }
  
  private void addSensorCount(int statusCode)
  {
    int majorCode = statusCode / 100;

    switch (majorCode) {
    case 2:
      _status2xxSensor.start();
      switch (statusCode) {
      case 200:
        _status200Sensor.start();
        break;
      default:
        _status2xxSensor.start();
        break;
      }
      break;
    case 3:
      switch (statusCode) {
      case 304:
        _status304Sensor.start();
        break;
      default:
        _status3xxSensor.start();
        break;
      }
      break;
    case 4:
      switch (statusCode) {
      case 400:
        _status400Sensor.start();
        _status4xxSensor.start();
        break;
      case 404:
        _status404Sensor.start();
        break;
      default:
        _status4xxSensor.start();
        break;
      }
      break;
    case 5:
      /*
      if (webApp != null)
        webApp.addStatus500();
        */
      
      _status5xxSensor.start();
      
      switch (statusCode) {
      case 500:
        _status500Sensor.start();
        break;
      case 503:
        _status503Sensor.start();
        break;
      default:
        break;
      }
      break;
    default:
      _statusXxxSensor.start();
      break;
    }
  }
    
  /*
  abstract protected void writeHeaders(long length)
    throws IOException;
    */

  public void writePending()
  {
  }

  public final LogBuffer getLogBuffer()
  {
    return _logBuffer;
  }

  public final byte []fillDateBuffer(long now)
  {
    if (_lastDate / 1000 != now / 1000) {
      fillDate(now);
    }
    
    return _dateBuffer;
  }
  
  public final int getDateBufferLength()
  {
    return _dateBufferLength;
  }
  
  public final int getRawDateBufferOffset()
  {
    return 8; // "\r\nDate: "
  }
  
  public final int getRawDateBufferLength()
  {
    return 24;
  }

  private void fillDate(long now)
  {
    byte []dateBuffer = _dateBuffer;
    
    if (_lastDate / HOUR == now / HOUR) {
      int min = (int) (now / 60000 % 60);
      int sec = (int) (now / 1000 % 60);

      int m2 = '0' + (min / 10);
      int m1 = '0' + (min % 10);

      int s2 = '0' + (sec / 10);
      int s1 = '0' + (sec % 10);

      dateBuffer[28] = (byte) m2;
      dateBuffer[29] = (byte) m1;

      dateBuffer[31] = (byte) s2;
      dateBuffer[32] = (byte) s1;

      _lastDate = now;

      return;
    }
    
    Instant instant = ClockCurrent.GMT.instant();
    OffsetDateTime time = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    
    _lastDate = instant.toEpochMilli();
    
    CharBuffer dateCharBuffer = _dateCharBuffer;
    dateCharBuffer.clear();
    dateCharBuffer.append("\r\ndate: ");
    
    //DateTimeFormatter formatter = DateTimeFormatter.RFC_1123_DATE_TIME;
    _dateCharBuffer.append(_dateFormatter.format(time));
    _dateCharBuffer.append(" GMT");
    //calendar.printDate(_dateCharBuffer);
    
    //QDate.freeGmtDate(calendar);

    char []cb = dateCharBuffer.buffer();
    int len = dateCharBuffer.length();

    for (int i = len - 1; i >= 0; i--) {
      dateBuffer[i] = (byte) cb[i];
    }

    dateBuffer[len] = (byte) '\r';
    dateBuffer[len + 1] = (byte) '\n';
    dateBuffer[len + 2] = (byte) '\r';
    dateBuffer[len + 3] = (byte) '\n';

    _dateBufferLength = len + 4;
  }

  public final byte []fillLogDateBuffer(long now, 
                                        String timeFormat)
  {
    if (_lastLogDate / 1000 != now / 1000) {
      fillLogDate(now, timeFormat);
    }
    
    return _logDateBuffer;
  }
  
  public final int getLogDateBufferLength()
  {
    return _logDateBufferLength;
  }

  private void fillLogDate(long now, 
                           String timeFormat)
  {
    byte []logDateBuffer = _logDateBuffer;
    
    if (_lastLogDate / HOUR == now / HOUR) {
      int min = (int) (now / 60000 % 60);
      int sec = (int) (now / 1000 % 60);

      int m2 = '0' + (min / 10);
      int m1 = '0' + (min % 10);

      int s2 = '0' + (sec / 10);
      int s1 = '0' + (sec % 10);

      logDateBuffer[_logMinutesOffset + 0] = (byte) m2;
      logDateBuffer[_logMinutesOffset + 1] = (byte) m1;

      logDateBuffer[_logSecondsOffset + 0] = (byte) s2;
      logDateBuffer[_logSecondsOffset + 1] = (byte) s1;

      _lastLogDate = now;

      return;
    }
    
    //QDate localCalendar = QDate.allocateLocalDate();

    Instant time = ClockCurrent.GMT.instant();
    
    _lastLogDate = time.toEpochMilli();
    //localCalendar.setGMTTime(now);
    _logDateCharBuffer.clear();

    _logDateCharBuffer.append(_dateFormatter.format(time));
    //localCalendar.format(_logDateCharBuffer, timeFormat);
    
    //QDate.freeLocalDate(localCalendar);
    
    _logSecondsOffset = _logDateCharBuffer.lastIndexOf(':') + 1;
    _logMinutesOffset = _logSecondsOffset - 3;

    char []cb = _logDateCharBuffer.buffer();
    int len = _logDateCharBuffer.length();

    for (int i = len - 1; i >= 0; i--) {
      logDateBuffer[i] = (byte) cb[i];
    }

    _logDateBufferLength = len;
  }

  /**
   * Closes the request, called from web-app for early close.
   */
  public void close()
    throws IOException
  {
    /*
    // server/125i
    if (! isSuspend()) {
      finishInvocation(true);

      // finishRequest(true);
    }
      */
  }
  
  void closeResponse()
  {
    OutHttpApp outResponse = _responseStream;
    
    if (outResponse != null) {
      IoUtil.close(outResponse);
    }
  }

  protected void free()
  {
  }
  
  public void freeSelf()
  {
  }

  protected String dbgId()
  {
    return "Tcp[" + connTcp().id() + "] ";
  }

  static {
    _headerCodes = new CaseInsensitiveIntMap();
    _headerCodes.put("cache-control", HEADER_CACHE_CONTROL);
    _headerCodes.put("connection", HEADER_CONNECTION);
    _headerCodes.put("content-type", HEADER_CONTENT_TYPE);
    _headerCodes.put("content-length", HEADER_CONTENT_LENGTH);
    _headerCodes.put("date", HEADER_DATE);
    _headerCodes.put("server", HEADER_SERVER);
    
    //_headerCodes = new CaseInsensitiveIntMap();

    TOKEN = new boolean[256];
    VALUE = new boolean[256];

    for (int i = 0; i < 256; i++) {
      TOKEN[i] = true;
    }

    for (int i = 0; i < 32; i++) {
      TOKEN[i] = false;
    }

    for (int i = 127; i < 256; i++) {
      TOKEN[i] = false;
    }

    //TOKEN['('] = false;
    //TOKEN[')'] = false;
    //TOKEN['<'] = false;
    //TOKEN['>'] = false;
    //TOKEN['@'] = false;
    TOKEN[','] = false;
    TOKEN[';'] = false;
    //TOKEN[':'] = false;
    TOKEN['\\'] = false;
    TOKEN['"'] = false;
    //TOKEN['/'] = false;
    //TOKEN['['] = false;
    //TOKEN[']'] = false;
    //TOKEN['?'] = false;
    TOKEN['='] = false;
    //TOKEN['{'] = false;
    //TOKEN['}'] = false;
    TOKEN[' '] = false;

    System.arraycopy(TOKEN, 0, VALUE, 0, TOKEN.length);

    VALUE['='] = true;
    
    _dateFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy hh:mm:ss");
  }
}
