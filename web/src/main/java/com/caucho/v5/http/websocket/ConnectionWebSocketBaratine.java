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

package com.caucho.v5.http.websocket;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import com.caucho.v5.http.protocol.RequestHttpBase;
import com.caucho.v5.network.port.ConnectionProtocol;
import com.caucho.v5.network.port.StateConnection;
import com.caucho.v5.util.ModulePrivate;
import com.caucho.v5.websocket.io.InWebSocket;

import io.baratine.web.WebSocket;

/**
 * User facade for http requests.
 */
@ModulePrivate
public class ConnectionWebSocketBaratine implements ConnectionProtocol
{
  private static final Logger log = Logger.getLogger(ConnectionWebSocketBaratine.class.getName());
  
  //private final HttpServletRequestImpl _request;

  //private DuplexController _controller;
  //private final Endpoint _endpoint;

  //private FrameInputStream _is;

  private WebSocketBase _wsConn;

  private AtomicBoolean _isWriteClosed = new AtomicBoolean();
  private InWebSocket _reader;

  //private Connection _conn;
  //private ConnectionSocket _connTcp;

  private RequestHttpBase _responseHttp;

  private RequestHttpBase _requestHttp;

  public ConnectionWebSocketBaratine(WebSocketBase wsConn, 
                                     RequestHttpBase requestHttp)
  {
    Objects.requireNonNull(wsConn);
    Objects.requireNonNull(requestHttp);
    
    _wsConn = wsConn;
    _requestHttp = requestHttp;
    
    /*
    wsConn.init(requestHttp.getRawRead(),
                responseHttp.getOut());
                */
  }

  // @Override
  //@Override
  private StateConnection start()
  {
    return StateConnection.CLOSE;
  }

  //@Override
  public StateConnection service()
    throws IOException
  {
    return _wsConn.service();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
