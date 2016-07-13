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

package com.caucho.v5.websocket.client;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.amp.thread.ThreadPool;
import com.caucho.v5.http.websocket.WebSocketBase;
import com.caucho.v5.http.websocket.WebSocketManager;
import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.SocketBar;
import com.caucho.v5.io.WriteStream;
import com.caucho.v5.tcp.TcpConnection;
import com.caucho.v5.util.Base64Util;
import com.caucho.v5.util.L10N;
import com.caucho.v5.websocket.WebSocketClient;
import com.caucho.v5.websocket.io.FrameInputStream;
import com.caucho.v5.websocket.io.WebSocketConstants;
import com.caucho.v5.websocket.io.WebSocketProtocolException;

import io.baratine.io.Buffer;
import io.baratine.pipe.Pipe;
import io.baratine.web.ServiceWebSocket;

/**
 * WebSocketClient
 */
public class WebSocketClientBaratine<T,S> extends WebSocketBase<T,S>
  implements WebSocketConstants, WebSocketClient
{
  private static final Logger log
    = Logger.getLogger(WebSocketClientBaratine.class.getName());
  private static final L10N L = new L10N(WebSocketClientBaratine.class);

  private String _url;
  private URI _uri;

  private String _scheme;
  private String _host;
  private int _port;
  private String _path;

  private Map<String,List<String>> _headersOut = new HashMap<>();

  private long _connectTimeout;

  private String _virtualHost;
  private boolean _isMasked = true;

  private boolean _isClosed;

  private ThreadClientTask _threadTask;
  private ConnectionWebSocketJni _jniTask;

  private FrameInputStream _frameIs;

  private HashMap<String,String> _headers = new HashMap<String,String>();

  private List<String> _preferredSubprotocols = new ArrayList<>();

  private String _origin;

  private Pipe<Buffer> _onRead;
  private SocketBar _socket;
  private TcpConnection _conn;
  private WriteStream _os;
  private ServiceWebSocket<T,S> _service;

  /*
  public WebSocketClient(String url, WebSocketListener listener)
  {
    this(url, createAdapter(listener), createConfigAdapter(url));
  }
  */

  public WebSocketClientBaratine(String address,
                                 ServiceWebSocket<T,S> service)
  {
    this(address, new HashMap<>(), service);
  }

  public WebSocketClientBaratine(String address,
                                 Map<String,List<String>> headers,
                                 ServiceWebSocket<T,S> service)
  {
    super(new WebSocketManager());

    //Objects.requireNonNull(container);
    Objects.requireNonNull(address);
    Objects.requireNonNull(service);
    //_container = container;
    //_endpoint = endpoint;
    //_config = config;

    _headersOut = headers;

    try {
      _uri = new URI(address);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    /*
    if (config != null) {
      if (config.getPreferredSubprotocols() != null) {
        _preferredSubprotocols.addAll(config.getPreferredSubprotocols());
      }
    }
    */

    //_configurator = config.getConfigurator();

    /*
    if (config != null) {
      _origin = config.getOrigin();
    }
    */

    _scheme = _uri.getScheme();
    _host = _uri.getHost();
    _port = _uri.getPort();
    _path = _uri.getPath();

    if (_path == null) {
      _path = "/";
    }

    _service = service;

    open();
    TypeRef typeRef = TypeRef.of(service.getClass());
    TypeRef typeRefService = typeRef.to(ServiceWebSocket.class);
    TypeRef type = typeRefService.param(0);

    Class<?> valueType;

    if (type != null) {
      valueType = type.rawClass();
    }
    else {
      valueType = String.class;
    }

    readInit(valueType, (ServiceWebSocket) _service, null);
    /*
    if (Frame.class.equals(valueType)) {
      readFrameInit((ServiceWebSocket) _service);
    }
    else if (String.class.equals(valueType)) {
      readStringInit((ServiceWebSocket) _service);
    }
    else if (Buffer.class.equals(valueType)) {
      readBufferInit((ServiceWebSocket) _service);
    }
    else {
      throw new UnsupportedOperationException(valueType.toString());
    }
    */
  }

  public String getHost()
  {
    return _host;
  }

  public int getPort()
  {
    return _port;
  }

  public String getPath()
  {
    return _path;
  }

  public void setVirtualHost(String virtualHost)
  {
    _virtualHost = virtualHost;
  }

  public void setConnectTimeout(long timeout)
  {
    _connectTimeout = timeout;
  }

  public void setMasked(boolean isMasked)
  {
    _isMasked = isMasked;
  }

  /*
  public WebSocketClientBaratine onOpen(ServiceWebSocket service)
  {
    Objects.requireNonNull(service);

    _onOpen = service;

    return this;
  }
  */

  /*
  public WebSocketClientBaratine onRead(OutPipe<Buffer> onRead)
  {
    Objects.requireNonNull(onRead);

    _onRead = onRead;

    return this;
  }
  */

  /**
   * @param preferredSubprotocols
   */
  public void setPreferredSubprotocols(List<String> preferredSubprotocols)
  {
    _preferredSubprotocols = preferredSubprotocols;
  }

  public void setOrigin(String origin)
  {
    _origin = origin;
  }


  public void connect()
    throws IOException
  {
    connect(null, null);
  }

  public void connect(String userName, String password)
    throws IOException
  {
    connectImpl(userName, password);
  }

  public String getProtocolVersion()
  {
    return WebSocketConstants.VERSION;
  }

  public String getHeader(String key)
  {
    return _headers.get(key);
  }

  protected void connectImpl(String userName, String password)
    throws IOException
  {
    int connectTimeout = (int) _connectTimeout;

    /*
    InetSocketAddress addr = new InetSocketAddress(_host, _port);

    SocketSystem network = SocketSystem.current();

    QSocket s = network.connect(addr.getAddress(), addr.getPort(), connectTimeout);
    */

    _conn = TcpConnection.open(_host, _port);

    /*
    Socket s = new Socket();

    if (connectTimeout > 0) {
      s.connect(new InetSocketAddress(_host, _port), connectTimeout);
    }
    else {
      s.connect(new InetSocketAddress(_host, _port));
    }
    */

    /*
    SocketChannel chan = SocketChannel.open();

    chan.connect(new InetSocketAddress(_host, _port));

    Socket s = chan.socket();
    */

    //s.setTcpNoDelay(true);

    /*
    if ("https".equals(_scheme)) {
      s = openSsl(s);
    }
    */

    //_socket = s;
    //_socketConn = new EndpointConnectionQSocket(s);
    //_socketConn.setIdleReadTimeout(600000);

    ReadStream is = _conn.inputStream();
    WriteStream os = _conn.outputStream();
    _os = os;

    String path = _path;

    if (_uri.getQuery() != null) {
      path = path + "?" + _uri.getQuery();
    }

    os.print("GET " + path + " HTTP/1.1\r\n");

    if (_virtualHost != null) {
      os.print("Host: " + _virtualHost + "\r\n");
    }
    else if (_host != null) {
      os.print("Host: " + _host + "\r\n");
    }
    else {
      os.print("Host: localhost\r\n");
    }

    byte []clientNonce = new byte[16];

    String key = Base64Util.encode(clientNonce);

    os.print("Sec-WebSocket-Key: " + key + "\r\n");

    String version = WebSocketConstants.VERSION;

    os.print("Sec-WebSocket-Version: " + version + "\r\n");

    if (_origin != null) {
      os.print("Origin: " + _origin + "\r\n");
    }

    StringBuilder ext = new StringBuilder();

    if (! _isMasked) {
      if (ext.length() > 0)
        ext.append(", ");

      ext.append("x-unmasked");
    }

    /*
    if (ext.length() > 0) {
      os.print("Sec-WebSocket-Extensions: " + ext + "\r\n");
    }
    */

    if (_preferredSubprotocols != null && _preferredSubprotocols.size() > 0) {
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < _preferredSubprotocols.size(); i++) {
        if (i > 0) {
          sb.append(", ");
        }

        sb.append(_preferredSubprotocols.get(i));
      }

      os.print("Sec-WebSocket-Protocol: " + sb + "\r\n");
    }

    Map<String,List<String>> headers = _headersOut;//new HashMap<>();

    //_configurator.beforeRequest(headers);

    for (Map.Entry<String,List<String>> entry : headers.entrySet()) {
      List<String> values = entry.getValue();

      os.print(entry.getKey());
      os.print(": ");

      for (int i = 0; i < values.size(); i++) {
        if (i != 0) {
          os.print(", ");
        }

        os.print(values.get(i));
      }

      os.print("\r\n");
    }

    if (_origin != null) {
      os.print("Origin: " + _origin + "\r\n");
    }

    os.print("Upgrade: websocket\r\n");
    os.print("Connection: Upgrade\r\n");

    os.print("\r\n");
    os.flush();

    parseHeaders(is);

    //_frameIs = new FrameInputStream();

    os.flush();

    String []names = new String[0];

    String subprotocol = _headers.get("Sec-WebSocket-Protocol");

    //_webSocket = new WebSocketImplClient(_uri.getPath(), os);


    FrameInputStream fIs = new FrameInputStream();
    fIs.init(null, is);

    //Objects.requireNonNull(_frameIs);
    frameInput(fIs);

    _threadTask = new ThreadClientTask(this, is);

    // static callbacks must be before the open
    /*
    if (_onRead != null) {
      read(_onRead);
    }
    */

    // open before the reader in case the on open registers message handlers
    /*
    if (_onOpen != null) {
      _onOpen.open(this); // _webSocket);
    }
    */

    try {
      _service.open(this);
    } catch (Exception e) {
      throw new IOException(e);
    }

    // now can start the reader
    if (_threadTask != null) {
      ThreadPool.current().execute(_threadTask);
    }
  }

  private void parseHeaders(ReadStream in)
    throws IOException
  {
    String status = readln(in);

    if (status == null) {
      throw new WebSocketProtocolException(L.l("Unexpected connection close"));
    }
    else if (status == null || ! status.startsWith("HTTP")) {
      throw new WebSocketProtocolException(L.l("Unexpected response {0}", status));
    }

    String line;
    while ((line = readln(in)) != null && line.length() != 0) {
      int p = line.indexOf(':');

      if (p > 0) {
        String header = line.substring(0, p).trim();
        String value = line.substring(p + 1).trim();

        _headers.put(header, value);
      }
    }

    if (! status.startsWith("HTTP/1.1 101")) {
      StringBuilder sb = new StringBuilder();

      int ch;

      while (in.available() > 0 && (ch = in.read()) >= 0) {
        sb.append((char) ch);
      }

      throw new WebSocketProtocolException(L.l("Unexpected response {0}\n\n{1}",
                                               status, sb));

    }
  }

  private String readln(ReadStream in)
    throws IOException
  {
    StringBuilder sb = new StringBuilder();
    int ch;

    while ((ch = in.read()) >= 0 && ch != '\n') {
      if (ch != '\r') {
        sb.append((char) ch);
      }
    }

    return sb.toString();
  }

  @Override
  protected <X> Pipe<X> wrap(Pipe<X> handler)
  {
    return handler;
  }

  /*
  @Override
  protected boolean readFrame()
  {
    return super.readFrame();
  }
  */

  public void disconnect()
  {
  }

  public boolean isClosed()
  {
    return _isClosed;
  }

  public void close()
  {
    disconnect();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _uri + "]";
  }

  @Override
  public void send(Buffer buffer)
  {
    try {
      buffer.read(_os);
      _os.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
