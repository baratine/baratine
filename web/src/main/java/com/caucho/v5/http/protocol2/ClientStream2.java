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

package com.caucho.v5.http.protocol2;

import io.baratine.service.ResultFuture;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.caucho.v5.io.TempBuffer;

/**
 * InputStreamHttp reads a single HTTP frame.
 */
public class ClientStream2
{
  private String _path;
  private String _method;
  private String _host = "localhost";
  private InRequestClient _request;
  private ResultFuture<InputStreamClient> _future;
  private Map<String,String> _headers;
  private OutputStreamClient _os;
  private InputStreamClient _is;
  
  private boolean _isHeaderWritten;
  private ClientHttp2 _client;
  private FlagsHttp _flags = FlagsHttp.END_STREAM;

  public ClientStream2(ClientHttp2 client,
                      String path, 
                      InRequestClient request,
                      ResultFuture<InputStreamClient> future)
  {
    Objects.requireNonNull(client);
    Objects.requireNonNull(path);
    Objects.requireNonNull(request);
    Objects.requireNonNull(future);
    
    _client = client;
    
    _path = path;
    _request = request;
    _future = future;
  }
  
  public ClientStream2 method(String method)
  {
    Objects.requireNonNull(method);
    
    _method = method;
    
    return this;
  }
  
  public ClientStream2 host(String host)
  {
    Objects.requireNonNull(host);
    
    _host = host;
    
    return this;
  }
  
  public void setHeader(String key, String value)
  {
    if (_headers == null) {
      _headers = new HashMap<>();
    }
    
    _headers.put(key, value);
  }

  public OutputStream getOutputStream()
  {
    if (_os == null) {
      _os = new OutputStreamClient(this);
      _flags = FlagsHttp.CONT_STREAM;
      
      if (_method == null) {
        _method = "POST";
      }
    }
    
    return _os;
  }
  
  void writeData(TempBuffer tBuf, FlagsHttp flags)
  {
    if (! _isHeaderWritten) {
      _isHeaderWritten = true;
      
      FlagsHttp flagsHeader = flags;
      
      if (tBuf != null) {
        flagsHeader = FlagsHttp.CONT_STREAM;
      }
      
      String method = _method;
      
      if (method == null) {
        method = "GET";
      }

      MessageRequestClientHttp2 request
        = new MessageRequestClientHttp2(method, _host, _path,
                                        _headers, _request, 
                                        flagsHeader);
      
      _client.offer(request);
    }
    
    if (tBuf != null) {
      MessageDataClient data = new MessageDataClient(_request, tBuf, 0, tBuf.length(), flags);

      _client.offer(data);
    }
    
    _flags = flags;
  }
  
  public InputStreamClient getInputStream()
  {
    if (_is == null) {
      writeData(null, _flags);
      
      _is = _future.get(10, TimeUnit.SECONDS);
    }
    
    return _is;
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _path + "]";
  }
}
