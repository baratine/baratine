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

package com.caucho.v5.http.protocol;

import java.io.IOException;
import java.security.cert.X509Certificate;

import com.caucho.v5.http.dispatch.Invocation;
import com.caucho.v5.http.protocol2.OutHeader;
import com.caucho.v5.io.WriteBuffer;
import com.caucho.v5.network.port.StateConnection;

import io.baratine.web.HttpStatus;


/**
 * User facade for http requests.
 */
abstract public class RequestFacadeBase
  implements RequestFacade
{
  /*
  @Override
  public ResponseFacade getResponse()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  */

  @Override
  public void invocation(Invocation invocation)
  {
  }
  
  //
  // TCP info
  //

  @Override
  public String getRemoteAddr()
  {
    return null;
  }
  
  //
  // http request
  //

  @Override
  public String getRequestURI()
  {
    return null;
  }

  @Override
  public String getQueryString()
  {
    return null;
  }

  @Override
  public String getMethod()
  {
    return null;
  }

  @Override
  public String getHeader(String string)
  {
    return null;
  }
  
  //
  // ssl
  //

  @Override
  public void setCipherSuite(String cipher)
  {
  }

  @Override
  public void setCipherKeySize(int keySize)
  {
  }

  @Override
  public void setCipherCertificate(X509Certificate []cert)
  {
  }
  
  //
  // async/comet
  //

  @Override
  public boolean isAsyncStarted()
  {
    return false;
  }

  @Override
  public boolean isAsyncComplete()
  {
    return false;
  }

  @Override
  abstract public StateConnection service();

  @Override
  public StateConnection resume() throws Exception
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public void killKeepalive(String string)
  {
  }

  @Override
  public void cleanup()
  {
  }

  @Override
  public void finishRequest() throws IOException
  {
  }
  @Override
  public void setStatus(HttpStatus status)
  {
  }

  @Override
  public int getStatus()
  {
    return 0;
  }

  @Override
  public String getStatusMessage()
  {
    return null;
  }

  @Override
  public String getContentType()
  {
    return null;
  }

  @Override
  public void setContentType(String value)
  {
  }

  @Override
  public String getContentTypeImpl()
  {
    return getContentType();
  }

  @Override
  public String getCharacterEncodingImpl()
  {
    return null;
  }
  
  //
  // caching
  //

  @Override
  public void killCache()
  {
  }

  @Override
  public boolean isCaching()
  {
    return false;
  }

  @Override
  public boolean isNoCache()
  {
    return false;
  }

  @Override
  public boolean isPrivateCache()
  {
    return false;
  }

  @Override
  public void setCacheControl(boolean isCacheControl)
  {
  }

  @Override
  public boolean isCacheControl()
  {
    return false;
  }

  @Override
  public boolean isNoCacheUnlessVary()
  {
    return false;
  }

  @Override
  public boolean handleNotModified() throws IOException
  {
    return false;
  }
  
  //
  // tail callbacks

  @Override
  public void fillHeaders()
  {
  }

  @Override
  public void sendError(HttpStatus status)
    throws IOException
  {
    setStatus(status);
  }
  
  //
  // http response
  //

  @Override
  public void writeCookies(WriteBuffer os) throws IOException
  {
  }

  @Override
  public void fillCookies(OutHeader out) throws IOException
  {
  }
}
