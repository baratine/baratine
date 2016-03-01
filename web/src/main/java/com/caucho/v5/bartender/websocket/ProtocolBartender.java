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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import com.caucho.v5.http.protocol.ConnectionHttp;
import com.caucho.v5.network.port.ConnectionProtocol;
import com.caucho.v5.network.port.ConnectionTcp;
import com.caucho.v5.network.port.ProtocolBase;
import com.caucho.v5.network.port.StateConnection;
import com.caucho.v5.web.webapp.RequestBaratineImpl;

import io.baratine.web.ServiceWebSocket;

/**
 * general websocket launching protocol
 */
public class ProtocolBartender extends ProtocolBase
{
  public static final String HEADER = "WEBSOCKET";
  
  private ConcurrentHashMap<String,Supplier<ServiceWebSocket>> _serviceMap
    = new ConcurrentHashMap<>();
    
  private final AtomicLong _connId = new AtomicLong();
  
  public ProtocolBartender()
  {
  }
    
  public <T> void publish(String path, Supplier<ServiceWebSocket> supplier)
  {
    _serviceMap.put(path, supplier);
  }
  
  @Override
  public ConnectionProtocol newConnection(ConnectionTcp conn)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  public ConnectionProtocol newRequest(ConnectionHttp request,
                                       ConnectionTcp conn)
  {
    throw new UnsupportedOperationException();
    //return new ConnectionBartender(this, conn, request);
  }

  public StateConnection upgrade(RequestBaratineImpl req)
  {
    
    //ConnectionBartender conn = new ConnectionBartender(wsProtocol);
    
     // _requestProtocol.setSubrequest(conn);
    
    //return conn.upgrade(req);
    
    if (true) throw new UnsupportedOperationException();
 
    return null;
  }

  @Override
  public String name()
  {
    return "ws";
  }

  /*
  public Object getProtocolClass()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */

  public ServiceWebSocket getService(String path)
  {
    Supplier<ServiceWebSocket> supplier = _serviceMap.get(path);
    
    if (supplier != null) {
      return supplier.get();
    }
    else {
      return null;
    }
  }

  public long newId()
  {
    return _connId.incrementAndGet();
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
