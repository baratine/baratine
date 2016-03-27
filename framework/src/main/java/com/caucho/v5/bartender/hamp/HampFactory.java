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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.remote.ChannelServerFactory;
import com.caucho.v5.http.websocket.WebSocketHandler;

import io.baratine.web.ServiceWebSocket;

/**
 * HAMP WebSocket endpoint factory for creating instances of the websocket.
 */
class HampFactory implements Supplier<ServiceWebSocket>
  //extends EndpointConfigServerWebSocketBase
{
  // private final ServerEndpointConfig _cfg;
  private final HampManager _hampManager;
  private final ServicesAmp _ampManager;
  private ChannelServerFactory _channelFactory;
  private ArrayList<String> _subprotocols;
  private String _path;
  
  HampFactory(HampManager manager, String path)
  {
    //super(EndpointHampWebSocket.class, path);
    
    _path = path;
    
    _hampManager = manager;
    _ampManager = manager.ampManager();
    
    _subprotocols = new ArrayList<>();
    _subprotocols.add("hamp");

    init();
  }
  
  public String getPath()
  {
    return _path;
  }
  
  @Override
  public ServiceWebSocket get()
  {
    return new HampService(getAmpManager(), getChannelFactory());
  }
  
  protected void init()
  {
  }
  
  protected HampManager getHampManager()
  {
    return _hampManager;
  }
  
  public ServicesAmp getAmpManager()
  {
    return _ampManager;
  }
  
  public ChannelServerFactory getChannelFactory()
  {
    return _channelFactory;
  }
  
  protected void setChannelFactory(ChannelServerFactory factory)
  {
    _channelFactory = factory;
  }

  //@Override
  public List<String> getSubprotocols()
  {
    return _subprotocols;
  }
}
