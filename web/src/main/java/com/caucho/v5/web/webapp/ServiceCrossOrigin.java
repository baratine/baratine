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
 * @author Alex Rojkov
 */

package com.caucho.v5.web.webapp;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.baratine.web.CrossOrigin;
import io.baratine.web.HttpMethod;
import io.baratine.web.RequestWeb;
import io.baratine.web.ServiceWeb;

/**
 * Route resulting in a not-found message.
 */
public class ServiceCrossOrigin implements ServiceWeb
{
  private static final Logger log
    = Logger.getLogger(ServiceCrossOrigin.class.getName());

  public static final String ALLOW_CREDENTIALS
    = "Access-Control-Allow-Credentials";

  public static final String ALLOW_HEADERS = "Access-Control-Allow-Headers";
  public static final String ALLOW_ORIGIN = "Access-Control-Allow-Origin";
  public static final String ALLOW_METHODS = "Access-Control-Allow-Methods";
  public static final String EXPOSE_HEADERS = "Access-Control-Expose-Headers";
  public static final String MAX_AGE = "Access-Control-Max-Age";

  private CrossOrigin _crossOrigin;

  public ServiceCrossOrigin(CrossOrigin crossOrigin)
  {
    Objects.requireNonNull(crossOrigin);
    
    _crossOrigin = crossOrigin;
  }

  /**
   * Service a request.
   *
   * @param request the http request facade
   */
  @Override
  public void handle(RequestWeb request)
  {
    try {
      if (! request.method().equals("OPTIONS")) {
        request.ok();
        return;
      }
      
      if (_crossOrigin.allowCredentials())
        request.header(ALLOW_CREDENTIALS, "true");

      //allow-origin
      String[] origin = _crossOrigin.value();

      if (origin.length == 0)
        origin = _crossOrigin.allowOrigin();

      if (origin.length > 0)
        request.header(ALLOW_ORIGIN, join(origin));

      //allow methods
      HttpMethod[] methods = _crossOrigin.allowMethods();

      if (methods.length > 0) {
        request.header(ALLOW_METHODS, join(methods));
      }
      /*
      else
        request.header(ALLOW_METHODS, _method.toString());
        */

      //allow headers
      String[] allowHeaders = _crossOrigin.allowHeaders();
      if (allowHeaders.length > 0)
        request.header(ALLOW_HEADERS, join(allowHeaders));

      //expose headers
      String[] exposeHeaders = _crossOrigin.exposeHeaders();
      if (exposeHeaders.length > 0)
        request.header(EXPOSE_HEADERS, join(exposeHeaders));

      //max age
      request.header(MAX_AGE, Long.toString(_crossOrigin.maxAge()));

      request.ok();
      request.halt();
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  private String join(Object[] values)
  {
    if (values.length == 1)
      return values[0].toString();

    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < values.length; i++) {
      Object value = values[i];
      if (i != 0)
        builder.append(", ");
      builder.append(value.toString());
    }

    return builder.toString();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _crossOrigin + "]";
  }
}
