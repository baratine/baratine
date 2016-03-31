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

package com.caucho.v5.web.webapp;

import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.http.websocket.WebSocketBaratineImpl;
import com.caucho.v5.http.websocket.WebSocketManager;

import io.baratine.web.RequestWeb;
import io.baratine.web.ServiceWebSocket;

/**
 * Route resulting in a not-found message.
 */
public class WebSocketApply<T,S> implements RouteBaratine
{
  private static final Logger log
    = Logger.getLogger(WebSocketApply.class.getName());
  
  private final Function<RequestWeb,ServiceWebSocket<T,S>> _factory;
  private final Class<T> _type;
  
  //private ServiceWebSocket<T,S> _service;

  //private Supplier<? extends ServiceWebSocket<T,S>> _supplier;
  
  WebSocketApply(Function<RequestWeb,ServiceWebSocket<T,S>> factory,
                 Class<T> typeService)
  {
    Objects.requireNonNull(factory);
    Objects.requireNonNull(typeService);
    
    _factory = factory;
    _type = typeService;
  }
  
  /**
   * Service a request.
   *
   * @param request the http request facade
   * @param response the http response facade
   */
  @Override
  public boolean service(RequestBaratine request)
  {
    /*
    try {
      _service.open((WebRequest) request);
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
    */

    ServiceWebSocket<T, S> service = _factory.apply(request);

    WebSocketManager wsManager = request.webApp().wsManager();
    WebSocketBaratineImpl<T,S> ws
      = new WebSocketBaratineImpl<>(wsManager, service, _type);
    
    try {
      if (ws.handshake(request)) {
        return true;
      }
      else {
        log.fine("WebSocket handshake failed for " + request);
        return false;
      }
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
      e.printStackTrace();
      
      request.fail(e);
      
      return true;
    }
    //request.flush();
  }
}
