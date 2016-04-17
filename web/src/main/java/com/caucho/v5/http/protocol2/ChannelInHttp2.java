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

import java.util.Objects;

import com.caucho.v5.io.TempBuffer;

/**
 * InputChannelHttp manages the flow for a single HTTP stream.
 */
public class ChannelInHttp2 extends ChannelFlowHttp2
{
  private final ChannelHttp2 _channel;
  private final InRequest _request;
  
  private int _streamId;

  ChannelInHttp2(ChannelHttp2 channel, InRequest request)
  {
    Objects.requireNonNull(channel);
    Objects.requireNonNull(request);

    _channel = channel;
    _request = request;
  }

  public int getId()
  {
    return _streamId;
  }

  void init(ConnectionHttp2Int conn, int streamId)
  {
    Objects.requireNonNull(conn);
    
    super.init();
    
    _streamId = streamId;
  }

  public InRequest getRequest()
  {
    return _request;
  }

  public void onData(TempBuffer tBuf)
  {
    _request.data(tBuf);
  }
  
  public void resetStream(int errorCode)
  {
    _request.closeReset();
    close();
  }

  public void close()
  {
    _channel.closeRead();
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _streamId + "]";
  }
}
