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

import java.io.IOException;
import java.io.OutputStream;

import com.caucho.v5.http.protocol.RequestHttpBase;

import io.baratine.web.HttpStatus;
import io.baratine.web.RequestWeb;

/**
 * User facade for baratine http requests.
 */
public final class RequestBaratineNext extends RequestWrapper
{
  private RequestBaratineImpl _delegate;
  private RequestHttpBase _http;

  RequestBaratineNext(RequestBaratineImpl delegate)
  {
    _delegate = delegate;
    _http = delegate.requestHttp();
  }
  
  private RequestHttpBase http()
  {
    return _http;
  }
  
  private OutputStream out()
  {
    return _http.out();
  }
  
  @Override
  public RequestBaratine delegate()
  {
    return _delegate;
  }
  
  @Override
  public RequestWeb status(HttpStatus status)
  {
    http().status(status.code(), status.message());
    
    return this;
  }
  
  @Override
  public RequestWeb length(long length)
  {
    http().contentLengthOut(length);
    
    return this;
  }
  
  @Override
  public RequestWeb header(String key, String value)
  {
    http().headerOut(key, value);
    
    return this;
  }
  
  @Override
  public RequestWeb type(String contentType)
  {
    http().headerOutContentType(contentType);
    
    return this;
  }
  
  @Override
  public RequestWeb write(byte []buffer, int offset, int length)
  {
    try {
      out().write(buffer, offset, length);
    
      return this;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  @Override
  public OutputStream output()
  {
    return _http.out();
  }
  
  @Override
  public void ok()
  {
    try {
      out().close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
