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

package io.baratine.web;

/**
 * Enum for HTTP status codes
 */
public enum HttpStatus
{
  CONTINUE(100),
  SWITCHING_PROTOCOLS(101),
  PROCESSING(102),
  
  OK(200, "OK"),
  CREATED(201),
  ACCEPTED(202),
  NON_AUTHORITATIVE_INFORMATION(203),
  NO_CONTENT(204),
  RESET_CONTENT(205),
  PARTIAL_CONTENT(206),
  MULTI_STATUS(207),
  
  MULTIPLE_CHOICES(300),
  MOVED_PERMANENTLY(301),
  MOVED_TEMPORARILY(302),
  FOUND(302),
  SEE_OTHER(303),
  NOT_MODIFIED(304),
  USE_PROXY(305),
  TEMPORARY_REDIRECT(307),
  PERMANENT_REDIRECT(307),
  
  BAD_REQUEST(400),
  UNAUTHORIZED(401),
  PAYMENT_REQUIRED(402),
  FORBIDDEN(403),
  NOT_FOUND(404),
  METHOD_NOT_ALLOWED(405),
  NOT_ACCEPTABLE(406),
  PROXY_AUTHENTICATION_REQUIRED(407),
  REQUEST_TIMEOUT(408),
  CONFLICT(409),
  GONE(410),
  LENGTH_REQUIRED(411),
  PRECONDITION_FAILED(412),
  REQUEST_ENTITY_TOO_LARGE(413),
  REQUEST_URI_TOO_LONG(414),
  UNSUPPORTED_MEDIA_TYPE(415),
  REQUESTED_RANGE_NOT_SATISFIABLE(416),
  EXPECTATION_FAILED(417),
  MISDIRECTED_REQUEST(421),
  
  INTERNAL_SERVER_ERROR(500),
  NOT_IMPLEMENTED(501),
  BAD_GATEWAY(502),
  SERVICE_UNAVAILABLE(503),
  GATEWAY_TIMEOUT(504),
  HTTP_VERSION_NOT_SUPPORTED(505);
  
  private final int _code;
  private final String _message;
  
  HttpStatus(int code)
  {
    _code = code;
    
    StringBuilder sb = new StringBuilder();
    String name = toString();
    
    sb.append(name.charAt(0));
    
    for (int i = 1; i < name.length(); i++) {
      char ch = name.charAt(i);
      
      if (ch == '_') {
        sb.append(' ');
        sb.append(name.charAt(i + 1));
        i++;
      }
      else {
        sb.append(Character.toLowerCase(ch));
      }
    }
    
    _message = sb.toString();
  }
  
  HttpStatus(int code, String message)
  {
    _code = code;
    
    _message = message;
  }
  
  public int code()
  { 
    return _code;
  }
  
  public String message()
  { 
    return _message;
  }
}
