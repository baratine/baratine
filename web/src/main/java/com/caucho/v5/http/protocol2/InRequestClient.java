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

import io.baratine.service.Result;

import java.io.InputStream;
import java.util.Objects;

import com.caucho.v5.io.TempBuffer;

/**
 * Handler for http parsing.
 */
public class InRequestClient implements InRequest
{
  private ClientHttp2 _client;
  private int _streamId;

  private InputStreamClientImpl _is;
  private Result<InputStreamClient> _result;
  private ChannelHttp2 _channel;
  
  InRequestClient(ClientHttp2 client,
                  Result<InputStreamClient> result)
  {
    Objects.requireNonNull(client);
    // Objects.requireNonNull(result);
    
    _client = client;
    _result = result;
    
    _channel = new ChannelHttp2(this);
    _is = new InputStreamClientImpl();
  }
  
  public int getStreamId()
  {
    return _streamId;
  }

  public ChannelHttp2 channel()
  {
    return _channel;
  }
  
  @Override
  public ChannelOutHttp2 getChannelOut()
  {
    return _channel.getOutChannel();
  }
  
  @Override
  public ChannelInHttp2 getChannelIn()
  {
    return _channel.getInChannel();
  }
  
  public void init(int streamId)
  {
    _streamId = streamId;
    
    _channel.init(_client.getConnection(), streamId);
    
    _client.registerRequest(this);
  }
  
  /**
   * Adds a header to the request.
   */
  @Override
  public void header(String key, String value)
  {
    _is.header(key, value);
  }
  
  /**
   * Adds data to the request.
   */
  @Override
  public void data(TempBuffer tBuf)
  {
    _is.data(tBuf);
  }
  
  /**
   * dispatch a request after the headers are read. 
   */
  @Override
  public void dispatch()
  {
    Result<InputStreamClient> result = _result;
    
    if (result != null) {
      result.ok(_is);
    }
  }
  
  /**
   * Called to end the request stream.
   */
  @Override
  public void closeRead()
  {
    _is.closeRead();
    
    _client.closeRequest(_streamId);
  }
  
  /**
   * Called to end the request stream.
   */
  @Override
  public void closeReset()
  {
    closeRead();
  }
  
  @Override
  public void closeChannel()
  {
  }

  public InputStream getInputStream()
  {
    return _is;
  }
}
