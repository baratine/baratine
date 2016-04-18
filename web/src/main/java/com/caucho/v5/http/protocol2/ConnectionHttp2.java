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
import java.util.Objects;

import com.caucho.v5.http.container.HttpContainer;
import com.caucho.v5.http.protocol.ConnectionHttp;
import com.caucho.v5.http.protocol.ProtocolHttp;
import com.caucho.v5.http.protocol.RequestHttp;
import com.caucho.v5.http.protocol.RequestHttpWeb;
import com.caucho.v5.io.SocketBar;
import com.caucho.v5.network.port.ConnectionTcp;
import com.caucho.v5.network.port.StateConnection;
import com.caucho.v5.util.FreeRing;
import com.caucho.v5.web.webapp.RequestBaratineImpl;


/**
 * Duplex connection handler for HTTP.
 */
public class ConnectionHttp2 extends ConnectionHttp
  implements InHttpHandler
{
  private final ProtocolHttp _httpProtocol;
  private final HttpContainer _httpContainer;
  private final ConnectionHttp2Int _conn;
  private ConnectionTcp _connTcp;
  
  private FreeRing<RequestHttp2> _freeRequest = new FreeRing<>(8);
  //private ConnectionHttp _connHttp;

  public ConnectionHttp2(ProtocolHttp protocolHttp,
                         HttpContainer httpContainer,
                         ConnectionTcp connTcp)
  {
    super(protocolHttp, connTcp, 0);
    
    Objects.requireNonNull(protocolHttp);
    Objects.requireNonNull(httpContainer);
    Objects.requireNonNull(connTcp);
    
    _httpProtocol = protocolHttp;
    _httpContainer = httpContainer;
    _connTcp = connTcp;
    
    _conn = new ConnectionHttp2Int(this, PeerHttp.SERVER);
  }
  
  OutHttp getOut()
  {
    return _conn.outHttp();
  }

  // @Override
  public void onStart() throws IOException
  {
    _conn.init(_connTcp.readStream(), _connTcp.writeStream());
    
    InHttp inHttp = _conn.inHttp();
    OutHttp outHttp = _conn.outHttp();
    
    inHttp.readSettings();
    outHttp.updateSettings(inHttp.peerSettings());
    outHttp.writeSettings(inHttp.getSettings());
    outHttp.flush();
  }

  public void onStartUpgrade(RequestHttp requestHttp)
    throws IOException
  {
    InHttp inHttp = _conn.inHttp();
    OutHttp outHttp = _conn.outHttp();

    System.out.println("START-UPGRADE: " + this);
    if (true) throw new UnsupportedOperationException();
    //inHttp.init(_connTcp.getReadStream());
    outHttp.init(_connTcp.writeStream());
    
    // _inHttp.readSettings(); // settings from request
    
    outHttp.updateSettings(inHttp.peerSettings());
    outHttp.writeSettings(inHttp.getSettings());
    outHttp.flush();

    boolean isEndStream = true;
    
    InRequest inRequest = inHttp.openInitialHeader(isEndStream);
    
    RequestHttp2 reqHttp2 = (RequestHttp2) inRequest;
    
    reqHttp2.fillUpgrade(requestHttp);
    
    reqHttp2.dispatch();
  }
  
  @Override
  public ConnectionTcp connTcp()
  {
    return _connTcp;
  }
  
  public SocketBar socket()
  {
    return connTcp().socket();
  }

  @Override
  public StateConnection service() throws IOException
  {
    if (! _conn.inHttp().onDataAvailable()) {
      return StateConnection.CLOSE;
    }
    else if (_conn.isClosed()) {
      return StateConnection.CLOSE;
    }
    else {
      return StateConnection.READ;
    }
  }

  /*
  @Override
  public void onCloseRead()
  {
    super.onCloseRead();

    _conn.closeRead();
  }
  
  @Override
  public void onCloseConnection()
  {
    super.onCloseConnection();
    
  }

  @Override
  public void onTimeout()
  {
    // System.out.println("TIMEOUT: " + context);
  }
  */

  @Override
  public InRequest newInRequest()
  {
    RequestHttp2 request = _freeRequest.allocate();
    
    if (request == null) {
      request = new RequestHttp2(_httpProtocol);
      /*
                                 connTcp(),
                                 _httpContainer,
                                 this);
                                 */
    }
    
    RequestBaratineImpl requestWeb = new RequestBaratineImpl(this,
                                                             request);
    //OutChannelHttp2 stream = request.getStreamOut();
    //stream.init(streamId, _outHttp);
    
    //request.init(this); // , _outHttp);
    
    return request;
  }
  
  @Override
  public void requestComplete(RequestHttpWeb request, boolean isKeepalive)
  {
    //super.requestComplete(request, true);
  }
  
  void freeRequest(RequestHttp2 request)
  {
    _freeRequest.free(request);
  }

  /*
  public OutChannelHttp2 getStream()
  {
    return _stream;
  }
  */
  
  @Override
  public void onGoAway()
  {
    // try { Thread.sleep(1000); } catch (Exception e) {}
  }
}
