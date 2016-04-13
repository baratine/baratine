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

package com.caucho.v5.bartender.websocket;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.thread.ThreadPool;
import com.caucho.v5.io.IoUtil;
import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.SocketBar;
import com.caucho.v5.io.SocketSystem;
import com.caucho.v5.io.WriteStream;
import com.caucho.v5.util.L10N;
import com.caucho.v5.websocket.client.WebSocketImplClient;
import com.caucho.v5.websocket.io.CloseReason;
import com.caucho.v5.websocket.io.FrameInputStream;
import com.caucho.v5.websocket.io.FrameListener;
import com.caucho.v5.websocket.io.WebSocketConstants;

import io.baratine.service.ServiceExceptionConnect;
import io.baratine.web.ServiceWebSocket;

/**
 * WebSocketClient
 */
public class ClientBartenderWebSocket
  implements FrameListener, WebSocketConstants, Closeable
{
  private static final Logger log
    = Logger.getLogger(ClientBartenderWebSocket.class.getName());
  private static final L10N L = new L10N(ClientBartenderWebSocket.class);
  
  private URI _uri;
  
  private long _connectTimeout;
  private boolean _isSSL;

  //private Endpoint _endpoint;

  private boolean _isClosed;

  private ClientContext _context;
  
  private FrameInputStream _frameIs;
  //private ClientEndpointConfig _config;
  
  private final AtomicLong _connId = new AtomicLong();
  private ServiceWebSocket _service;
  private WebSocketImplClient _webSocket;
  
  public ClientBartenderWebSocket(String url, Object endpoint)
  {
    Objects.requireNonNull(endpoint);
    
    _service = (ServiceWebSocket) endpoint;
    
    try {
      _uri = new URI(url);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    
    if (url == null) {
      throw new IllegalArgumentException();
    }
    
    if (_uri.getPort() <= 0) {
      throw new IllegalArgumentException();
    }
    
    String scheme = _uri.getScheme();
    
    _isSSL = scheme.endsWith("s") && ! scheme.equals("ws");
    
    //ClientEndpointConfig.Builder builder = ClientEndpointConfig.Builder.create();
    
    //_config = builder.build();
    
    String []names = new String[0];
  }
  
  /*
  protected EndpointConfig getConfiguration()
  {
    return _config;
  }
  */
  
  public void setConnectTimeout(long timeout)
  {
    _connectTimeout = timeout;
  }

  public void connect()
    throws IOException
  {
    /*
    if (_socketConn != null) {
      return;
    }
    */
    
    connectImpl();
  }

  protected void connectImpl()
    throws IOException
  {
    /*
    if (_endpoint == null) {
      throw new IllegalStateException(L.l("Missing websocket listener (in {0})",
                                          this));
    }
    */

    int connectTimeout = (int) _connectTimeout;
    boolean isSSL = _isSSL;
    SocketSystem network = SocketSystem.current();
    
    int port = _uri.getPort();
    
    if (port <= 0) {
      port = 80;
    }
    
    SocketBar s = network.connect(new InetSocketAddress(_uri.getHost(), _uri.getPort()),
                                connectTimeout, isSSL);
    
    //_socketConn = new EndpointConnectionQSocket(s);
    
    boolean isValid = false;
    
    long timeout = s.getSoTimeout();
    
    try {
      s.setSoTimeout(60 * 1000);
      
      ReadStream is = s.getInputStream();
      WriteStream os = s.getOutputStream();

      os.print("HAMP *\n");

      String protocolHeader = ProtocolBartender.HEADER;

      os.println(protocolHeader);
      os.println(_uri.getPath());
      os.println(getFlags());
      os.println();
      os.flush();
      
      String protocol = IoUtil.readln(is);
      String line;
      String firstLine = "";
      
      while ((line = IoUtil.readln(is)) != null && ! "".equals(line)) {
        if (line.equals("status: 200")) {
          isValid = true;
        }
        else if (line.startsWith("status:")) {
          throw new ServiceExceptionConnect(L.l("invalid status '{0}' when connecting to {1}",
                                                line, _uri));
        }
        else if (! "".equals(firstLine)) {
          firstLine = line;
        }
      }

      if (! isValid) {
        throw new ServiceExceptionConnect(L.l("no status returned when connecting to {0}. protocol='{1}' line='{1}'",
                                              _uri, protocol, firstLine));
      }
      
      
      _webSocket = new WebSocketImplClient(_uri.getPath(), os, _service);

      /*
      // static callbacks must be before the open
      if (_onRead != null) {
        _webSocket.read(_onRead);
      }

      // open before the reader in case the on open registers message handlers
      if (_onOpen != null) {
        _onOpen.accept(_webSocket);
      }
      */
      
      _service.open(_webSocket);
      
      _context = new ClientContext();

      Objects.requireNonNull(_context);
      ThreadPool.current().schedule(_context);
    } finally {
      try {
        s.setSoTimeout(timeout);
      } catch (Exception e) {
      }
      
      /*
      if (! isValid) {
        EndpointConnectionQSocket socketConn = _socketConn;
        _socketConn = null;
        
        socketConn.disconnect();
      }
      */
    }
  }
  
  protected String getFlags()
  {
    return "";
  }
  
  /*
  protected ConnectionWebSocketBase 
  createConnection(ContainerWebSocketBase container, 
                   String uri, 
                   Endpoint endpoint, 
                   ClientEndpointConfig config, 
                   FrameInputStream fis)
  {
    long connId = _connId.incrementAndGet();
    
    //return new ConnectionPlain(connId, container, uri, endpoint, config, fis);
    return null;
  }
  */

  /*
  public Session getSession()
  {
    return _conn.getSession();
  }
  
  public void disconnect()
  {
    _isClosed = true;
    
    // socketConn is not disconnected because the session disconnects it
    //QSocketEndpointConnection socketConn = _socketConn;
    _socketConn = null;

    //_endpoint.onClose(_session);
  }
  */

  public boolean isClosed()
  {
    return _isClosed;
  }
  
  @Override
  public void close()
  {
    /*
    if (log.isLoggable(Level.FINEST)) {
      log.finest("close-write " + _conn);
    }
    */
    //_conn.onDisconnectRead();
    // close(1000, "ok");
    try {
      //getSession().close();
      //disconnect();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void onPing(byte[] buffer, int offset, int length)
  {
  }

  @Override
  public void onPong(byte[] buffer, int offset, int length)
  {
  }

  @Override
  public void onClose(CloseReason reason)
  {
    close();
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _uri + "]";
  }
  
  class ClientContext implements Runnable
  {
    @Override
    public void run()
    {
      Thread thread = Thread.currentThread();
      String name = thread.getName();
      
      try {
        thread.setName("web-socket-client-" + thread.getId());
        /*
        InWebSocket wsEndpointReader = _webSocket.getReader();
        
        while (wsEndpointReader.onRead()) {
          ServiceRef.flushOutbox();
        }
        */
      } catch (Exception e) {
         if (_isClosed)
          log.log(Level.FINEST, e.toString(), e);
        else
          log.log(Level.WARNING, e.toString(), e);
      } finally {
        /*
        if (log.isLoggable(Level.FINEST)) {
          log.finest("on-close-read " + _conn);
        }
        
        _conn.onDisconnectRead();
          
        thread.setName(name);
        */
      }
    }
  }
}
