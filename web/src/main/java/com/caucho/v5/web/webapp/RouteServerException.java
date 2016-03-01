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

import com.caucho.v5.config.ConfigException;

import io.baratine.web.HttpStatus;

/**
 * Route resulting in a status code error message.
 */
public class RouteServerException implements RouteBaratine
{
  private Throwable _exn;
  
  public RouteServerException(Throwable exn)
  {
    Objects.requireNonNull(exn);
    
    _exn = exn;
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
    request.status(HttpStatus.INTERNAL_SERVER_ERROR);
    request.header("content-type", "text/plain");
    
    if (_exn instanceof ConfigException) {
      request.write("ConfigException: " + _exn.getMessage());
    }
    else {
      request.write(_exn.toString());
      
      request.write("\n");
      
      for (StackTraceElement stack : _exn.getStackTrace()) {
        request.write("\n");
        request.write(String.valueOf(stack));
      }
    }
    
    return true;
  }
}
