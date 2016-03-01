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

package com.caucho.v5.ramp.hamp;

import io.baratine.service.ServiceExceptionConnect;

import java.io.IOException;
import java.net.URI;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.remote.ChannelAmp;
import com.caucho.v5.amp.remote.OutAmp;
import com.caucho.v5.amp.remote.OutAmpFactory;
import com.caucho.v5.bartender.hamp.EndpointHampClient;
import com.caucho.v5.json.ser.JsonSerializerFactory;
import com.caucho.v5.ramp.jamp.SessionContextJamp;
import com.caucho.v5.util.L10N;

/**
 * Endpoint for receiving hamp message
 */
public class ConnectionHampFactoryClient implements OutAmpFactory
{
  private static final L10N L = new L10N(ConnectionHampFactoryClient.class);
  
  private final URI _uri;
  private final ServiceManagerAmp _ampManager;
  
  private final SessionContextJamp _channelContext = new SessionContextJamp();
  
  private String _host;
  
  private String _uid;
  private String _password;

  private JsonSerializerFactory _jsonFactory;
  
  public ConnectionHampFactoryClient(ServiceManagerAmp rampManager,
                                     String uri)
  {
    try {
      _uri = new URI(uri);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    
    _jsonFactory = new JsonSerializerFactory();
      
    _ampManager = rampManager;
  }
  
  public ConnectionHampFactoryClient(ServiceManagerAmp rampManager,
                                     String uri,
                                     String uid,
                                     String password)
  {
    this(rampManager, uri);
    
    _uid = uid;
    _password = password;
  }

  @Override
  public boolean isUp()
  {
    return true;
  }

  public void setVirtualHost(String host)
  {
    _host = host;
  }

  @Override
  public OutAmp getOut(ChannelAmp channel)
  {
    // EndpointJamp endpoint = new EndpointJamp();
    
    if (true) {
      throw new UnsupportedOperationException();
    }
    //WebSocketContainerClient client = new WebSocketContainerClient();
    
    EndpointHampClient endpoint
      = new EndpointHampClient(_ampManager, null, null,
                               channel);
    
    endpoint.setAuth(_uid, _password);

    try {
      if (true) {
        throw new UnsupportedOperationException();
      }
      /*
      EndpointJampConfigClient cfg
      = new EndpointJampConfigClient(_ampManager, _channelContext, channel, _jsonFactory, "hamp");
      
      client.connectToServer(endpoint, _host, cfg, _uri, _uid, _password);
      */

      // XXX: separate flag needed?
      if (_uid != null || _password != null || isLoginRequired()) {
        endpoint.login();
      }
    } catch (Exception e) {
      throw ServiceExceptionConnect.createAndRethrow(L.l("Can't connect to HAMP server at {0}.\n  {1}",
                                                     _uri, e.toString()),
                                                 e);
    }
    
    return endpoint;
  }
  
  protected boolean isLoginRequired()
  {
    return false;
  }
  
  public void connect()
  {
  }
  
  public void close()
    throws IOException
  {

  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _uri + "]";
  }
}
