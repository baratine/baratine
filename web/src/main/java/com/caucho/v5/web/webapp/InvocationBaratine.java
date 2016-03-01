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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.http.dispatch.Invocation;
import com.caucho.v5.http.protocol.RequestFacade;
import com.caucho.v5.network.port.StateConnection;

import io.baratine.web.HttpStatus;
import io.baratine.web.MultiMap;

/**
 * A repository for request information parsed from the uri.
 */
public class InvocationBaratine extends Invocation
{
  private static final Logger log
    = Logger.getLogger(InvocationBaratine.class.getName());
  
  private static final RouteBaratine NOT_FOUND = new RouteNotFound();
  
  private WebApp _webApp;
  private RouteBaratine []_routes = new RouteBaratine[0];
  
  private Map<String,String> _params;
  private MultiMap<String,String> _queryMap;

  private String _path;
  private String _pathInfo = "";
  
  public InvocationBaratine()
  {
  }
  
  public String path()
  {
    String path = _path;
    
    if (path != null) {
      return path;
    }
    else {
      return getURI();
    }
  }

  public void path(String path)
  {
    Objects.requireNonNull(path);
    
    _path = path;
  }
  
  public String pathInfo()
  {
    return _pathInfo;
  }

  public void pathInfo(String pathInfo)
  {
    Objects.requireNonNull(pathInfo);
    
    _pathInfo = pathInfo;
  }

  public Map<String, String> pathMap()
  {
    return _params;
  }
  
  public void params(Map<String,String> params)
  {
    Objects.requireNonNull(params);
    
    _params = Collections.unmodifiableMap(params);
  }

  public MultiMap<String, String> queryMap()
  {
    return _queryMap;
  }
  
  public void queryMap(MultiMap<String,String> queryMap)
  {
    Objects.requireNonNull(queryMap);
    
    _queryMap = queryMap;
  }
  
  public void routes(RouteBaratine []routes)
  {
    Objects.requireNonNull(routes);
    
    _routes = routes;
  }
  
  public void webApp(WebApp webApp)
  {
    Objects.requireNonNull(webApp);
    
    _webApp = webApp;
  }

  public WebApp webApp()
  {
    return _webApp;
  }

  /**
   * Service a request.
   *
   * @param request the http request facade
   * @param response the http response facade
   */
  public StateConnection service(RequestFacade request)
  {
    RequestBaratine req = (RequestBaratine) request;
    //ResponseBaratine res = (ResponseBaratine) response;
    
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    StateConnection nextState = StateConnection.CLOSE;
    
    try {
      if (_webApp != null) {
        thread.setContextClassLoader(_webApp.classLoader());
      }
      
      for (RouteBaratine route : _routes) {
        if (route.service(req)) {
          return nextState;
        }
      }
      
      if (_routes.length > 0) {
        req.halt(HttpStatus.METHOD_NOT_ALLOWED);
      }
      else {
        req.halt(HttpStatus.NOT_FOUND);
        
      }
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
    
    return nextState;
  }
}
