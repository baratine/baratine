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

package com.caucho.v5.bartender.link;

import io.baratine.service.ServiceExceptionConnect;

import java.net.ConnectException;
import java.util.Objects;

import com.caucho.v5.amp.AmpException;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.remote.ChannelAmp;
import com.caucho.v5.amp.remote.OutAmp;
import com.caucho.v5.amp.remote.OutAmpFactory;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.bartender.hamp.EndpointHampClient;
import com.caucho.v5.bartender.heartbeat.ServerHeartbeat;
import com.caucho.v5.bartender.websocket.ClientBartenderWebSocket;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;

/**
 * Endpoint for sending hamp message
 */
class OutAmpFactoryBartender implements OutAmpFactory
{
  private static final L10N L = new L10N(OutAmpFactoryBartender.class);
  
  private final ServerBartender _server;
  private final ServerBartender _selfServer;
  private final ServiceManagerAmp _ampManager;

  private final String _path;
  
  // private String _uri;
  
  public OutAmpFactoryBartender(ServiceManagerAmp ampManager,
                                ServerBartender server,
                                ServerBartender selfServer,
                                String path)
  {
    Objects.requireNonNull(server);
    Objects.requireNonNull(selfServer);
    
    _ampManager = ampManager;
    
    _server = server;
    _selfServer = selfServer;
    _path = path;
    
    // XXX:
    // _readService = RampClientReadActor.create(ampManager);
    /*
    _sender = new ChampSender("hamp://" + server.getAddress()
                                    + ":" + server.getPort()
                                    + "/bartender",
                                    selfHostName);
                                    */
  }

  @Override
  public boolean isUp()
  {
    return _server.isUp();
  }
  
  @Override
  public OutAmp getOut(ChannelAmp channel)
  {
    ServerBartender server = _server;

    if (server == null || server.port() <= 0) {
      return null;
    }
      
    return connect(server, channel);
  }
  
  private EndpointHampClient connect(ServerBartender serverBar,
                                     ChannelAmp channel)
  {
    ServerHeartbeat server = (ServerHeartbeat) serverBar;
    
    if (server.getRack() == null) {
      throw new ServiceExceptionConnect(L.l("{0} is not a known server in the network", server));
    }
    
    String address = server.getAddress();
    int port = server.getPortBartender();
    
    if (port <= 0) {
      // if the bartender port is unknown, the server is missing its heartbeat
      throw new ServiceExceptionConnect(L.l("{0} is not an active server in the network", server));
    }
        
    String uri;
    
    uri = ("bartender://" + address + ":" + port + _path);
    
    long connectionFailTime = server.getConnectionFailTime();
    
    try {
      long timeout = 1000L;
      // long timeout = 100L;
      
      if (! server.isUp()
          && CurrentTime.getCurrentTime() < connectionFailTime + timeout) {
        throw new ServiceExceptionConnect(L.l("Reconnect attempted before fail-timeout {0}ms to champ server {1}.\n",
                                              timeout,
                                              uri));
      }
      /*
      RampReadBrokerFactory readBrokerFactory
        = new HampClientReadBrokerFactory(_ampManager);
        */
    
      EndpointHampClient endpoint
        = new EndpointHampClient(_ampManager, server, _selfServer.getId(),
                                 channel);

      ClientBartenderWebSocket wsClient = new ClientBartenderWebSocket(uri, endpoint);
      
      endpoint.setClient(wsClient);
      
      wsClient.connect();
      
      /*
      endpoint.getReadBroker().login(endpoint);
      endpoint.getReadBroker().waitForLogin();
      */
      
      return endpoint;
    } catch (ConnectException e) {
      server.compareAndSetConnectionFailTime(connectionFailTime,
                                             CurrentTime.getCurrentTime());
      
      throw new ServiceExceptionConnect(L.l("Can't connect to champ server {0}\n  uri:  {1}.\n  {2}",
                                            server,
                                            uri, e.toString()),
                                        e);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new AmpException(e);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _server.getId() + "]";
  }

  /*
  @Override
  public RampServiceRef getReadService()
  {
    // TODO Auto-generated method stub
    return null;
  }
  */
}
