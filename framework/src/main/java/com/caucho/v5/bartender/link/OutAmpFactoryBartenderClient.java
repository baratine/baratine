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

import io.baratine.service.ServiceException;
import io.baratine.service.ServiceExceptionConnect;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.remote.ChannelAmp;
import com.caucho.v5.amp.remote.OutAmp;
import com.caucho.v5.amp.remote.OutAmpFactory;
import com.caucho.v5.bartender.hamp.EndpointHampClient;
import com.caucho.v5.bartender.heartbeat.ServerHeartbeat;
import com.caucho.v5.bartender.websocket.ClientBartenderWebSocket;
import com.caucho.v5.util.L10N;

/**
 * Endpoint for sending bartender message
 */
class OutAmpFactoryBartenderClient implements OutAmpFactory
{
  private static final L10N L = new L10N(OutAmpFactoryBartenderClient.class);
  
  private final ServiceManagerAmp _ampManager;
  private final String _selfHostName;
  private final String _uri;
  
  private String _user;
  private String _password;

  public OutAmpFactoryBartenderClient(ServiceManagerAmp ampManager,
                                      String selfHostName,
                                      String uri,
                                      String user,
                                      String password)
  {
    _ampManager = ampManager;
    _selfHostName = selfHostName;
    _uri = uri;
    _user = user;
    _password = password;
  }
/*
  public ChampClientConnectionFactory(RampManager ampManager,
                                      RampServiceRef readService,
                                      RampReadBrokerFactory brokerFactory,
                                      String uri)
  {
    this(ampManager, readService, brokerFactory, uri, null, null);
  }
  */

  @Override
  public boolean isUp()
  {
    return true;
  }
  
  @Override
  public OutAmp getOut(ChannelAmp channel)
  {
    try {
      String selfHostName = _selfHostName;
      ServerHeartbeat server = null;
      
      EndpointHampClient endpoint
        = new EndpointHampClient(_ampManager, server, selfHostName,
                                 channel);
      
      endpoint.setAuth(_user, _password);

      ClientBartenderWebSocket wsClient = new ClientBartenderWebSocket(_uri, endpoint);
      endpoint.setClient(wsClient);
      
      wsClient.connect();
      
      return endpoint;
    } catch (ServiceException e) {
      throw e;
    } catch (Exception e) {
      throw new ServiceExceptionConnect(L.l("Can't connect to {0}\n  {1}", _uri, e),
                                    e);
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _uri + "]";
  }
}
