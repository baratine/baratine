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

package com.caucho.v5.http.protocol2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import com.caucho.v5.health.shutdown.Shutdown;
import com.caucho.v5.http.container.HttpContainer;
import com.caucho.v5.http.protocol.ConnectionHttp;
import com.caucho.v5.http.protocol.OutResponseBase;
import com.caucho.v5.http.protocol.ProtocolHttp;
import com.caucho.v5.http.protocol.RequestHttp;
import com.caucho.v5.http.protocol.RequestHttpBase;
import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.io.WriteStream;
import com.caucho.v5.network.port.StateConnection;
import com.caucho.v5.util.ByteArrayBuffer;
import com.caucho.v5.util.CharBuffer;
import com.caucho.v5.util.CharSegment;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.web.webapp.InvocationBaratine;
import com.caucho.v5.web.webapp.RequestBaratine;

import io.baratine.io.Buffer;

/**
 * Handles requests for a HTTP 2 stream request.
 */
public class RequestHttp2 
  extends RequestHttpBase
  implements InRequest, Runnable
{
  private static final Logger log
    = Logger.getLogger(RequestHttp2.class.getName());

  private static final int HEADER_CAPACITY = 256;

  static final CharBuffer _getCb = new CharBuffer("GET");
  static final CharBuffer _headCb = new CharBuffer("HEAD");
  static final CharBuffer _postCb = new CharBuffer("POST");

  private String _method;       // "GET"
  // private CharBuffer scheme;       // "http:"
  private CharBuffer _host;            // www.caucho.com
  private ByteArrayBuffer _uri;             // "/path/test.jsp/Junk"

  private CharBuffer _remoteAddr;
  private CharBuffer _remoteHost;
  private CharBuffer _serverName;
  private CharBuffer _serverPort;
  private CharBuffer _remotePort;

  private boolean _isSecure;
  private ByteArrayBuffer _clientCert;

  private CharBuffer []_headerKeys;
  private CharBuffer []_headerValues;
  private int _headerSize;

  private int _serverType;

  // private ErrorPageManager _errorManager;

  //private HttpContainer _httpContainer;

  private String _scheme;

  private InvocationBaratine _invocation;

  private AtomicReference<StateRequest> _stateRef = new AtomicReference<>(StateRequest.ACTIVE);
  
  // private OutHttp _outHttp;

  //private DuplexHandlerHttp2 _conn;

  private InStreamImpl _inStream;

  //private final ConnectionHttp2 _connHttp2 ;

  private final ChannelHttp2 _channel;

  private final StringBuilder _cb = new StringBuilder();

  //private ConnectionTcp _connTcp;

  public RequestHttp2(ProtocolHttp httpProtocol)
  {
    super(httpProtocol); //, conn, null); // XXX: null should be connHttp

    /*
    _connHttp2 = connHttp;
    _httpContainer = httpContainer;
    
    _connTcp = conn;
    */
    //_errorManager = new ErrorPageManager(httpContainer);

    _uri = new ByteArrayBuffer();

    _host = new CharBuffer();

    _headerKeys = new CharBuffer[HEADER_CAPACITY];
    _headerValues = new CharBuffer[_headerKeys.length];
    for (int i = 0; i < _headerKeys.length; i++) {
      _headerKeys[i] = new CharBuffer();
      _headerValues[i] = new CharBuffer();
    }

    _remoteHost = new CharBuffer();
    _remoteAddr = new CharBuffer();
    _serverName = new CharBuffer();
    _serverPort = new CharBuffer();
    _remotePort = new CharBuffer();

    _clientCert = new ByteArrayBuffer();
    
    _channel = new ChannelHttp2(this);
    
    _inStream = new InStreamImpl();
    
    //init(connHttp);
    
    //System.out.println("CNN: " + connHttp());
    //Thread.dumpStack();
  }
  
  @Override
  public void init(RequestBaratine request)
  {
    super.init(request);;
    //_request = request;
    
    initRequest();
  }
  
  OutHttp outHttp()
  {
    return channel().getOutChannel().getOutHttp();
  }
  
  @Override
  public ChannelHttp2 channel()
  {
    return _channel;
  }
  
  @Override
  public ChannelOutHttp2 getChannelOut()
  {
    return _channel.getOutChannel();
  }
  
  @Override
  public ChannelInHttp2 getChannelIn()
  {
    return channel().getInChannel();
  }

  public int streamId()
  {
    return channel().getId();
  }

  public void init(ConnectionHttp2 reqHttp)
  {
    //super.init(reqHttp);
    // _streamId = streamId;
    // _conn = conn;
    // _outHttp = outHttp; // reqHttp.getOut();
    
    // _channel.init(conn, streamId);
    
    initRequest(); // XXX:
    /*
    try {
    } catch (IOException e) {
      e.printStackTrace();
    }
    */
  }
  
  public void fillUpgrade(RequestHttp requestHttp)
  {
    header(":method", requestHttp.method());
    String host = requestHttp.header("Host");
    if (host != null) {
      header(":authority", host);
    }
    
    String path = new String (requestHttp.uriBuffer(), 0, requestHttp.uriLength());
    
    header(":path", path);
    header(":scheme", requestHttp.scheme());
    
    int headerSize = requestHttp.getHeaderSize();
    
    for (int i = 0; i < headerSize; i++) {
      String key = requestHttp.getHeaderKey(i).toString();
      String value = requestHttp.header(key);
      
      header(key, value);
    }
  }

  @Override
  public void header(String key, String value)
  {
    switch (key) {
    case ":method":
      _method = value;
      break;
      
    case ":authority":
      _host.clear();
      _host.append(value);
      break;
      
    case ":path":
      _uri.clear();
      _uri.add(value);
      break;
      
    case ":scheme":
      _scheme = value;
      break;
      
    case "cookie":
      addCookie(value);
      break;
      
    default:
      setHeader(key, value);
      break;
    }
  }
  
  @Override
  public void data(TempBuffer tBuf)
  {
    _inStream.data(tBuf);
  }

  @Override
  public void closeRead()
  {
    _inStream.closeRead();
  }

  @Override
  public void closeReset()
  {
    closeRead();
  }

  @Override
  public void dispatch()
  {
    /*
    if (isSecure()) {
      getClientCertificate();
    }
    */

    // setStartDate();

    HttpContainer httpContainer = http();

    if (httpContainer == null || httpContainer.isDestroyed()) {
      log.fine("server is closed (" + dbgId() + ")");
      
      // XXX: go-away

      return;
    }

    try {
      // startRequest();
      // startInvocation();
    
      _invocation = invocation(getHost(),
                                 _uri.getBuffer(), _uri.getLength());

      //System.out.println("CONN: " + connHttp2());
      //System.out.println("  PRO: " + connHttp2().protocol());
      RequestBaratine request = request();

      request.invocation(_invocation);
      //RequestBaratineImpl request = (RequestBaratineImpl) connHttp2().protocol().newRequest(connHttp2());
      
      //request.init(reqState);
      request.onAccept();
      
      //request.init(this);
      
      /*
      if (_state.isBodyComplete()) {
        _request.bodyComplete();
      }
      else if (! requestHttp().isUpgrade()) {
        _requestHttp.readBodyChunk();
      }
      */
      request.onBodyComplete();
      
      
      
      StateConnection nextState = _invocation.service(request);

      //request().setInvocation(_invocation);
      
      //// XXX: needs to be throttled
      //ThreadPool.current().execute(this);
    } catch (IOException e) {
      e.printStackTrace();
    }
    

    //  invocation.service(getRequestFacade(), getResponseFacade());
  }

  @Override
  public void run()
  {
    try {
      _stateRef.get().toActive(_stateRef);
      
      //StateConnection nextState = request().service();
      throw new UnsupportedOperationException();
    } catch (OutOfMemoryError e) {
      Shutdown.shutdownOutOfMemory("RequestHttp2");
    } catch (Throwable e) {
      e.printStackTrace();
    } finally {
      try {
        //finishInvocation();
      } catch (Exception e) {
        e.printStackTrace();
      }
      
      /*
      try {
        finishRequest();
      } catch (Exception e1) {
        e1.printStackTrace();
      }
      */
      
      closeDispatch();
    }
  }

  /**
   * Initialize the read stream from the raw stream.
   */
  //@Override
  public boolean initStream(ReadStream readStream,
                            ReadStream rawStream)
    throws IOException
  {
    readStream.init(_inStream);
    
    return true;
  }

  /*
  private void getClientCertificate()
  {
    RequestFacade request = null;//request();
    
    if (true) throw new UnsupportedOperationException();

    String cipher = getHeader("SSL_CIPHER");
    
    if (cipher == null) {
      cipher = getHeader("HTTPS_CIPHER");
    }
    
    if (cipher != null) {
      request.setCipherSuite(cipher);
    }

    String keySize = getHeader("SSL_CIPHER_USEKEYSIZE");
    if (keySize == null) {
      keySize = getHeader("SSL_SECRETKEYSIZE");
    }
    
    if (keySize != null) {
      request.setCipherKeySize(Integer.parseInt(keySize));
    }

    if (_clientCert.size() == 0)
      return;

    try {
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      InputStream is = _clientCert.createInputStream();
      X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
      is.close();
      
      request.setCipherCertificate(new X509Certificate[] { cert });
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }
  */

  protected boolean checkLogin()
  {
    return true;
  }

  /**
   * Clears variables at the start of a new request.
   */
  @Override
  protected void initRequest()
  {
    super.initRequest();

    _uri.clear();
    _host.clear();
    _headerSize = 0;

    _remoteHost.clear();
    _remoteAddr.clear();
    _serverName.clear();
    _serverPort.clear();
    _remotePort.clear();

    _clientCert.clear();
    
    // _isSecure = getConnection().isSecure();
  }

  /*
  @Override
  public void finishRequest() throws IOException
  {
    super.finishRequest();
  }
  */

  private void resizeHeaders()
  {
    CharBuffer []newKeys = new CharBuffer[_headerSize * 2];
    CharBuffer []newValues = new CharBuffer[_headerSize * 2];

    for (int i = 0; i < _headerSize; i++) {
      newKeys[i] = _headerKeys[i];
      newValues[i] = _headerValues[i];
    }

    for (int i = _headerSize; i < newKeys.length; i++) {
      newKeys[i] = new CharBuffer();
      newValues[i] = new CharBuffer();
    }

    _headerKeys = newKeys;
    _headerValues = newValues;
  }

  /**
   * Returns the header.
   */
  @Override
  public String method()
  {
    return _method;
  }

  /**
   * Returns a char buffer containing the host.
   */
  @Override
  protected CharBuffer getHost()
  {
    if (_host.length() > 0) {
      return _host;
    }

    _host.append(_serverName);
    _host.toLowerCase();

    return _host;
  }

  @Override
  public final byte []uriBuffer()
  {
    return _uri.getBuffer();
  }

  @Override
  public final int uriLength()
  {
    return _uri.getLength();
  }

  /**
   * Returns the protocol.
   */
  @Override
  public String getProtocol()
  {
    return "HTTP/2.0";
  }

  /**
   * Returns the header.
   */
  @Override
  public String header(String key)
  {
    CharSegment buf = getHeaderBuffer(key);
    if (buf != null)
      return buf.toString();
    else
      return null;
  }

  @Override
  public CharSegment getHeaderBuffer(String key)
  {
    for (int i = 0; i < _headerSize; i++) {
      CharBuffer test = _headerKeys[i];

      if (test.equalsIgnoreCase(key))
        return _headerValues[i];
    }

    return null;
  }

  public CharSegment getHeaderBuffer(char []buf, int length)
  {
    for (int i = 0; i < _headerSize; i++) {
      CharBuffer test = _headerKeys[i];

      if (test.length() != length)
        continue;

      char []keyBuf = test.buffer();
      int j;
      for (j = 0; j < length; j++) {
        char a = buf[j];
        char b = keyBuf[j];
        if (a == b)
          continue;

        if (a >= 'A' && a <= 'Z')
          a += 'a' - 'A';
        if (b >= 'A' && b <= 'Z')
          b += 'a' - 'A';
        if (a != b)
          break;
      }

      if (j == length)
        return _headerValues[i];
    }

    return null;
  }

  @Override
  public void setHeader(String key, String value)
  {
    if (_headerKeys.length <= _headerSize) {
      resizeHeaders();
    }
    
    _headerKeys[_headerSize].clear();
    _headerKeys[_headerSize].append(key);
    _headerValues[_headerSize].clear();
    _headerValues[_headerSize].append(value);
    _headerSize++;
  }

  @Override
  public void getHeaderBuffers(String key, ArrayList<CharSegment> values)
  {
    CharBuffer cb = getCharBuffer();

    cb.clear();
    cb.append(key);

    int size = _headerSize;
    for (int i = 0; i < size; i++) {
      CharBuffer test = _headerKeys[i];
      if (test.equalsIgnoreCase(cb))
        values.add(_headerValues[i]);
    }
  }

  public Enumeration<String> getHeaderNames()
  {
    HashSet<String> names = new HashSet<String>();
    for (int i = 0; i < _headerSize; i++)
      names.add(_headerKeys[i].toString());

    return Collections.enumeration(names);
  }

  /**
   * Returns the server name.
   */
  /*
  @Override
  public String getServerName()
  {
    CharBuffer host = getHost();
    if (host == null) {
      InetAddress addr = getConnection().getRemoteAddress();
      return addr.getHostName();
    }

    int p = host.indexOf(':');
    if (p >= 0)
      return host.substring(0, p);
    else
      return host.toString();
  }
  */

  /*
  @Override
  public int getServerPort()
  {
    int len = _serverPort.length();
    int port = 0;
    for (int i = 0; i < len; i++) {
      char ch = _serverPort.charAt(i);
      port = 10 * port + ch - '0';
    }

    return port;
  }
  */

  /*
  @Override
  public String getRemoteAddr()
  {
    return _remoteAddr.toString();
  }
  */

  public void getRemoteAddr(CharBuffer cb)
  {
    cb.append(_remoteAddr);
  }

  /*
  @Override
  public int printRemoteAddr(byte []buffer, int offset)
    throws IOException
  {
    char []buf = _remoteAddr.getBuffer();
    int len = _remoteAddr.getLength();

    for (int i = 0; i < len; i++)
      buffer[offset + i] = (byte) buf[i];

    return offset + len;
  }
  */

  /*
  @Override
  public String getRemoteHost()
  {
    return _remoteHost.toString();
  }
  */

  /**
   * Called for a connection: close
   */
  @Override
  protected void handleConnectionClose()
  {
    // ignore for hmux
  }

  @Override
  public boolean isSuspend()
  {
    return false;
  }

  @Override
  public boolean isDuplex()
  {
    return false;
  }

  /**
   * Returns true if a valid HTTP request has started.
   */
  /*
  @Override
  public boolean hasRequest()
  {
    return true;
  }
  */

  protected String getRequestId()
  {
    HttpContainer http = http();
    
    String id;
    
    if (http != null) {
      id = http.getServerId();
    }
    else {
      id = "";
    }

    if (id.equals(""))
      return "server-" + connTcp().id();
    else
      return "server-" + id + ":" + connTcp().id();
  }
  
  //
  // Close dispatch and close channel are in different threads, but both
  // must complete before the request can be recycled.
  //
  public void closeDispatch()
  {
    if (_stateRef.get().closeDispatch(_stateRef)) {
      // XXX:_connHttp2.freeRequest(this);
    }
  }

  @Override
  public void closeChannel()
  {
    if (_stateRef.get().closeChannel(_stateRef)) {
      // XXX:_connHttp2.freeRequest(this);
    }
  }
  @Override
  protected OutResponseBase createOut()
  {
    return new OutResponseHttp2(this);
  }

  /**
   * headersWritten cannot be undone for hmux
   */
  @Override
  public void setHeaderWritten(boolean isWritten)
  {
    // server/265a
  }
  
  /*
  @Override
  protected void writeHeaders(long length)
    throws IOException
  {
  }
  */
  
  @Override
  public ConnectionHttp2 connHttp()
  {
    return (ConnectionHttp2) super.connHttp();
  }

  @Override
  public boolean write(WriteStream os, Buffer data,
                       boolean isEnd)
  {
    try {
      writeHeaders(isEnd && data == null);
      
      if (data != null) {
        ConnectionHttp2 conn = connHttp();

        OutHttp out = conn.getOut();
        
        out.writeData(streamId(), data,
                      isEnd ? Http2Constants.END_STREAM : 0);
      }
      
      connTcp().writeStream().flush();
      
      return false;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  private void writeHeaders(boolean isEnd)
    throws IOException
  {
    ConnectionHttp2 conn = connHttp();

    OutHttp out = conn.getOut();
    
    OutHeader outHeader = out.getOutHeader();
    
    int streamId = streamId();
    int pad = 0;
    int priorityDependency = -1;
    int priorityWeight = -1;
    boolean priorityExclusive = false;
    
    FlagsHttp flags = isEnd ? FlagsHttp.END_STREAM : FlagsHttp.CONT_STREAM;
    
    outHeader.openHeaders(streamId,
                          pad,
                          priorityDependency,
                          priorityWeight,
                          priorityExclusive,
                          flags);
    
    writeHeaders(outHeader);
    
    outHeader.closeHeaders();
  }
  
  @Override
  public boolean canWrite(long sequence)
  {
    return true;
  }
  
  void writeHeaders(OutHeader out)
    throws IOException
  {
    fillHeaders();

    RequestBaratine request = request();
    
    int statusCode = 200; //response.getStatus();

    StringBuilder cb = _cb;
    
    switch (statusCode) {
    case 200:
      out.header(":status", "200");
      break;
      
    case 404:
      out.header(":status", "404");
      break;
      
    case 500:
      out.header(":status", "500");
      break;
      
    case 503:
      out.header(":status", "503");
      break;
      
    default:
      cb.setLength(0);
      cb.append((char) ((statusCode / 100) % 10 + '0'));
      cb.append((char) ((statusCode / 10) % 10 + '0'));
      cb.append((char) (statusCode % 10 + '0'));
      
      out.header(":status",  cb.toString());
      break;
    }

    if (statusCode >= 400) {
      removeHeader("ETag");
      removeHeader("Last-Modified");
    }
    /*
    else if (response.isNoCache()) {
      removeHeader("ETag");
      removeHeader("Last-Modified");

      headerOut("expires", "Thu, 01 Dec 1994 16:00:00 GMT");
      out.header("cache-control", "no-cache");
    }
    else if (response.isPrivateCache())
      out.header("cache-control", "private");
      */

    ArrayList<String> headerKeys = headerKeysOut();
    ArrayList<String> headerValues = headerValuesOut();
    
    int size = headerKeys.size();
    for (int i = 0; i < size; i++) {
      String key = headerKeys.get(i);
      String value = headerValues.get(i);

      out.header(key.toLowerCase(), value);
    }

    long contentLength = contentLengthOut();
    if (contentLength >= 0) {
      cb.setLength(0);
      cb.append(contentLength);
      out.header("content-length", cb.toString());
    }
    /*
    else if (length >= 0) {
      cb.clear();
      cb.append(length);
      out.header("content-length", cb.toString());
    }
    */

    long now = CurrentTime.currentTime();
    
    // responseFacade.fillCookies(out);

    String contentType = null;//responseFacade.getContentTypeImpl();
    String charEncoding = null;//responseFacade.getCharacterEncodingImpl();

    if (contentType != null) {
      if (charEncoding != null)
        out.header("content-type", contentType + "; charset=" + charEncoding);
      else
        out.header("content-type", contentType);
    }
    
    String server = serverHeader();
    
    Objects.requireNonNull(server);
    out.header("server", server);
    
    byte []date = fillDateBuffer(now);
    int length = getDateBufferLength() - 4;
    
    int offset = 8; // "\r\nDate: "
    
    cb.setLength(0);
    for (int i = 8; i < length; i++) {
      cb.append((char) date[i]);
    }
    
    out.headerUnique("date", cb.toString());
  }

  @Override
  public final String dbgId()
  {
    HttpContainer http = http();
    
    String id;
    
    if (http != null) {
      id = http.getServerDisplayName();
    }
    else {
      id = "";
    }
    
    int streamId = 0;

    if (id.equals(""))
      return "Http2[" + streamId + "] ";
    else
      return "Http2[" + id + ":" + streamId + "] ";
  }

  @Override
  public String toString()
  {
    return dbgId();
  }
  
  private enum StateRequest {
    ACTIVE {
      @Override
      boolean closeDispatch(AtomicReference<StateRequest> stateRef)
      {
        if (stateRef.compareAndSet(ACTIVE, CLOSE_DISPATCH)) {
          return false;
        }
        else {
          return stateRef.get().closeDispatch(stateRef);
        }
      }

      @Override
      boolean closeChannel(AtomicReference<StateRequest> stateRef)
      {
        if (stateRef.compareAndSet(ACTIVE, CLOSE_CHANNEL)) {
          return false;
        }
        else {
          return stateRef.get().closeChannel(stateRef);
        }
      }
      
    },
    
    CLOSE_DISPATCH {
      @Override
      boolean closeChannel(AtomicReference<StateRequest> stateRef)
      {
        if (stateRef.compareAndSet(CLOSE_DISPATCH, CLOSE)) {
          return true;
        }
        else {
          return stateRef.get().closeChannel(stateRef);
        }
      }
    },
    
    CLOSE_CHANNEL {
      @Override
      boolean closeDispatch(AtomicReference<StateRequest> stateRef)
      {
        if (stateRef.compareAndSet(CLOSE_CHANNEL, CLOSE)) {
          return true;
        }
        else {
          return stateRef.get().closeDispatch(stateRef);
        }
      }
    },
    
    CLOSE {
    };
    
    void toActive(AtomicReference<StateRequest> stateRef)
    {
      stateRef.set(ACTIVE);
    }
    
    boolean closeDispatch(AtomicReference<StateRequest> stateRef)
    {
      throw new UnsupportedOperationException(toString());
    }
    
    boolean closeChannel(AtomicReference<StateRequest> stateRef)
    {
      throw new UnsupportedOperationException(toString());
    }
    
  }
}
