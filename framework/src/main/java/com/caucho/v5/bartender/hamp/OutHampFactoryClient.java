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

package com.caucho.v5.bartender.hamp;

import io.baratine.service.ServiceExceptionConnect;

import java.io.IOException;
import java.net.URI;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.remote.ChannelAmp;
import com.caucho.v5.amp.remote.OutAmp;
import com.caucho.v5.amp.remote.OutAmpFactory;
import com.caucho.v5.bartender.heartbeat.ServerHeartbeat;
import com.caucho.v5.util.L10N;

/**
 * Endpoint for receiving hamp message
 */
public class OutHampFactoryClient implements OutAmpFactory
{
  private static final L10N L = new L10N(OutHampFactoryClient.class);
  
  private final URI _uri;
  private final ServiceManagerAmp _rampManager;
  
  private String _host = "admin.resin";
  
  private String _uid;
  private String _password;
  
  public OutHampFactoryClient(ServiceManagerAmp rampManager,
                                     String uri)
  {
    try {
      _uri = new URI(uri);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
      
    _rampManager = rampManager;
  }
  
  public OutHampFactoryClient(ServiceManagerAmp rampManager,
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

  /*
  @Override
  public RampServiceRef getReadService()
  {
    return _readService;
  }
  */

  public void setVirtualHost(String host)
  {
    _host = host;
  }

  @Override
  public OutAmp getOut(ChannelAmp channel)
  {
    String host = "localhost";
    
    ServerHeartbeat server = null;
    
    EndpointHampClient endpoint
      = new EndpointHampClient(_rampManager, server, host,
                               channel);

    endpoint.setAuth(_uid, _password);
    
    if (true) {
      throw new UnsupportedOperationException();
    }
    
    //WebSocketContainerClient client = new WebSocketContainerClient();

    try {
      //client.connectToServer(endpoint, _host, _uri, _uid, _password);
    } catch (Exception e) {
      throw ServiceExceptionConnect.createAndRethrow(L.l("Can't connect to HAMP server at {0}.\n  {1}",
                                                     _uri, e.toString()),
                                                 e);
    }
    
    return endpoint;
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
