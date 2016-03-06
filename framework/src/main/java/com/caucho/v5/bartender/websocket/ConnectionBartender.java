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

import java.io.IOException;
import java.net.InetAddress;
import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.http.protocol.ConnectionHttp;
import com.caucho.v5.io.IoUtil;
import com.caucho.v5.io.ReadBuffer;
import com.caucho.v5.io.WriteBuffer;
import com.caucho.v5.network.NetworkSystemBartender;
import com.caucho.v5.network.port.ConnectionProtocol;
import com.caucho.v5.network.port.ConnectionTcp;
import com.caucho.v5.network.port.StateConnection;
import com.caucho.v5.util.L10N;
import com.caucho.v5.web.webapp.RequestBaratineImpl;

import io.baratine.web.ServiceWebSocket;

/**
 * Custom serialization for the cache
 */
public class ConnectionBartender implements ConnectionProtocol
{
  private static final L10N L = new L10N(ConnectionBartender.class);
  private static final Logger log
    = Logger.getLogger(ConnectionBartender.class.getName());
  
  public static final String PROTOCOL_HEADER = "WEBSOCKET";
  
  private final ProtocolBartender _protocol;
  private final ConnectionTcp _conn;
  private final ConnectionHttp _requestProtocol;
  private String _uri;
  
  ConnectionBartender(ProtocolBartender protocol,
                      ConnectionTcp conn,
                      ConnectionHttp requestProtocol)
  {
    Objects.requireNonNull(protocol);
    Objects.requireNonNull(conn);
    
    _protocol = protocol;
    _conn = conn;
    _requestProtocol = requestProtocol;
  }
  
  public ConnectionBartender(ProtocolBartender protocol)
  {
    Objects.requireNonNull(protocol);
    
    _protocol = protocol;
    _conn = null;
    _requestProtocol = null;
  }
  
  @Override
  public String url()
  {
    return _protocol.name() + ":";
  }
  
  ConnectionTcp getConnection()
  {
    return _conn;
  }
  
  ReadBuffer getReadStream()
  {
    return _conn.readStream();
  }
  
  WriteBuffer getWriteStream()
  {
    return _conn.writeStream();
  }

  @Override
  public StateConnection service() throws IOException
  {
    throw new UnsupportedOperationException();
  }

  public StateConnection upgrade(RequestBaratineImpl req) throws IOException
  {
    ConnectionTcp connTcp = req.connHttp().connTcp();
    
    if (! isLocalAddress(connTcp)) {
      log.warning(L.l("Attemped connection from foreign address {0}",
                      getConnection().getRemoteAddress()));
      
      return StateConnection.CLOSE;
    }
    
    ReadBuffer is = connTcp.readStream();

    if (! readHeader(is)) {
      log.fine("No cluster websocket uri found");
      
      return StateConnection.CLOSE;
    }
    
    ServiceWebSocket service = _protocol.getService(_uri.toString());

    // Endpoint endpoint = _protocol.createEndpoint(_uri.toString());
    
    if (service == null) {
      log.fine(L.l("cluster websocket uri '{0}' is an unknown endpoint", _uri));
      
      //getWriteStream().close();
      
      return StateConnection.CLOSE;
    }
    
    WriteBuffer os = connTcp.writeStream();
    os.println(PROTOCOL_HEADER);
    os.println("status: 200");
    os.println();
    os.flush();
    
    //ContainerWebSocketBase container = new WebSocketContainerClient();

    //EndpointConfig config = factory.getConfig();

    //FrameInputStream fIs = new FrameInputStreamUnmasked();
    
    //ConnectionWebSocketBase connWs;
    
    String path = _uri.toString();
    
    //long connId = _protocol.newId();
    
    WebSocketBartender ws = new WebSocketBartender(service);
    
    ws.connect(req);
    
    //connWs = new ConnectionPlain(connId, container, path, endpoint, config, fIs);
    
    //connWs = null;
    
    //RequestWebSocketServer subrequest = new RequestWebSocketServer(connWs, getConnection());

    //_requestProtocol.setSubrequest(null);
    //_requestProtocol.setSubrequest(subrequest);
    
    //subrequest.onStart();
    // _link.startDuplex(new DuplexListenerServerWebSocket(conn));
    
    //connWs.open();

    return StateConnection.READ;
  }
  
  private boolean readHeader(ReadBuffer is)
    throws IOException
  {
    String header = IoUtil.readln(is);

    if (! header.equalsIgnoreCase("HAMP *")) {
      log.warning(L.l("hamp connect is invalid (in {0})", this));
      
      return false;
    }
    
    header = IoUtil.readln(is);

    // XXX: socket local network check
    if (! header.equals(PROTOCOL_HEADER)) {
      log.warning(L.l("hamp websocket is invalid (in {0})", this));
      
      return false;
    }
    
    _uri = IoUtil.readln(is);
    
    String flags = IoUtil.readln(is);
    
    String line;
    
    while ((line = IoUtil.readln(is)) != null && ! "".equals(line)) {
    }
    
    return true;
  }
  
  private boolean isLocalAddress(ConnectionTcp conn)
  {
    //ConnectionTcp conn = getConnection();
    
    InetAddress remote = conn.getRemoteAddress();
    InetAddress local  = conn.getLocalAddress();
    
    if (remote == null || local == null) {
      return false;
    }
    
    else if (isSubnetMatch(remote, local)) {
      return true;
    }
    else if (isLocalSubnet(remote)) {
      return true;
    }
    else {
      NetworkSystemBartender network = NetworkSystemBartender.current();
      
      if (network != null && network.isAllowForeignIp()) {
        return true;
      }
      else {
        return false;
      }
    }
  }
  
  private boolean isSubnetMatch(InetAddress remote, InetAddress local)
  {
    byte []remoteAddress = remote.getAddress();
    byte []localAddress = local.getAddress();
    
    if (remoteAddress.length != localAddress.length) {
      return false;
    }
    
    int len = remoteAddress.length;

    for (int i = 0; i < len - 1; i++) {
      if (remoteAddress[i] != localAddress[i]) {
        return false;
      }
    }
    
    return true;
  }
  
  private boolean isLocalSubnet(InetAddress remote)
  {
    byte []remoteAddress = remote.getAddress();
    
    if ((remoteAddress[0] & 0xff) == 127) {
      return true;
    }
    else if ((remoteAddress[0] & 0xff) == 10) {
      return true;
    }
    else if ((remoteAddress[0] & 0xff) == 192
             && (remoteAddress[1] & 0xff) == 168) {
      return true;
    }
    else {
      return false;
    }
  }

  @Override
  public boolean isWaitForRead()
  {
    return false;
  }
}
