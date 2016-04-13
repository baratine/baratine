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

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;

import io.baratine.web.RequestWeb;
import io.baratine.web.ServiceWeb;

/**
 * Route resulting in a not-found message.
 */
public class RouteApply implements RouteBaratine
{
  private static final Logger log
    = Logger.getLogger(RouteApply.class.getName());
  
  private Predicate<RequestWeb> _predicate;
  
  private ServiceWeb _service;
  private RequestProxy _proxy;

  private ServiceWeb []_services;
  private ArrayList<ViewRef<?>> _views;
  
  RouteApply(ServiceWeb service,
             ArrayList<ServiceWeb> filtersBefore,
             ArrayList<ServiceWeb> filtersAfter,
             ServiceRefAmp serviceRef,
             Predicate<RequestWeb> predicate,
             ArrayList<ViewRef<?>> views)
  {
    Objects.requireNonNull(service);
    Objects.requireNonNull(predicate);
    Objects.requireNonNull(views);
    
    _views = views;
    _predicate = predicate;
    
    //_service = serviceRef.pin(new WebServiceWrapper(service)).as(ServiceWeb.class);
    _service = serviceRef.pin(new ServiceWebStub(service))
                         .as(ServiceWeb.class);
    _proxy = serviceRef.pin(new RequestProxyImpl()).as(RequestProxy.class);
    
    ArrayList<ServiceWeb> services = new ArrayList<ServiceWeb>();
    
    for (ServiceWeb filter : filtersBefore) {
      // XXX: filter might be proxy
      filter = serviceRef.pin(new ServiceWebStub(filter)).as(ServiceWeb.class);
      
      services.add(filter);
    }

    // XXX: service might be proxy(?)
    services.add(_service);
    
    for (ServiceWeb filter : filtersAfter) {
      // XXX: filter might be proxy
      filter = serviceRef.pin(filter).as(ServiceWeb.class);
      
      services.add(filter);
    }
    
    _services = new ServiceWeb[services.size()];
    services.toArray(_services);
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
    try {
      if (! _predicate.test(request)) {
        return false;
      }
      
      request.requestProxy(_proxy);
      
      request.views(_views);

      if (_services.length > 1) {
        RequestFilter requestChain = new RequestFilter(request, _services);
        requestChain.ok();
        
        return true;
      }
      else {
        _service.service((RequestWeb) request);
      }
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
    
    return true;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _service + "]";
  }
  
  static final class WebServiceWrapper implements ServiceWeb
  {
    private final ServiceWeb _route;
    
    WebServiceWrapper(ServiceWeb route)
    {
      _route = route;
    }

    @Override
    public void service(RequestWeb request)
    {
      try {
        _route.service((RequestWeb) request); 
      } catch (Throwable e) {
        System.out.println("FAIL: " + e + " " + request);
        e.printStackTrace();;
        request.fail(e);
      }
    }
  }
  
  private static class RequestProxyImpl implements RequestProxy
  {
    @Override
    public void bodyComplete(RequestBaratineImpl requestBaratineImpl)
    {
      requestBaratineImpl.bodyComplete();
    }
  }
}
