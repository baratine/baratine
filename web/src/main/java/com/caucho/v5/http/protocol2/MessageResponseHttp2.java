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

import java.io.IOException;

import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.io.WriteStream;

/**
 * InputStreamHttp reads a single HTTP frame.
 */
public class MessageResponseHttp2 extends MessageHttp
{
  private RequestHttp2 _request;
  private boolean _isHeaders;
  private TempBuffer _head;
  private FlagsHttp _flags;

  public MessageResponseHttp2(RequestHttp2 request,
                              TempBuffer head,
                              boolean isHeaders,
                              boolean isEnd)
  {
    _request = request;
    _head = head;
    _isHeaders = isHeaders;
    
    if (isEnd) {
      _flags = FlagsHttp.END_STREAM;
    }
    else {
      _flags = FlagsHttp.CONT_STREAM;
    }
  }
  
  protected FlagsHttp getFlagsHttp()
  {
    return _flags;
  }
  
  /**
   * Deliver the message
   * 
   * @param os the physical output stream
   * @param writerHttp the writer context
   */
  @Override
  public void deliver(WriteStream os, OutHttp outHttp)
    throws IOException
  {
    ChannelOutHttp2 stream = _request.getChannelOut();
    int streamId = _request.channel().getId();
    
    if (_isHeaders) {
      writeHeaders(outHttp);
    }
    
    int tailFlags;

    switch (getFlagsHttp()) {
    case END_STREAM:
      tailFlags = Http2Constants.END_STREAM;
      break;
      
    default:
      tailFlags = 0;
      break;
    }

    if (_head != null || tailFlags != 0) {
      stream.addSendBuffer(_head, ChannelOutHttp2.FRAME_LENGTH, tailFlags);
    }
    
    stream.writeCont(outHttp);
  }
  
  private void writeHeaders(OutHttp outHttp)
    throws IOException
  {
    OutHeader outHeader = outHttp.getOutHeader();
    
    int streamId = _request.streamId();
    
    FlagsHttp flags = getFlagsHttp();
    
    if (_head != null) {
      flags = FlagsHttp.CONT_STREAM;
    }
    
    outHeader.openHeaders(streamId, flags);
    
    _request.writeHeaders(outHeader);
    
    outHeader.closeHeaders();
  }
}
