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
import java.util.Objects;

import com.caucho.v5.io.TempBuffer;

/**
 * Channel manages a http2 stream.
 * 
 * "channel" is used instead of "stream" to avoid naming clashes/confusion. 
 */
public class ChannelOutHttp2
{
  static final int FRAME_LENGTH = 8;
  
  private static final byte[] BYTE_EMPTY = new byte[0];
  
  private final ChannelHttp2 _channel;
  
  private long _sendLength;
  private long _sendCredits;
  
  private long _receiveLength;
  private long _receiveCredits;
  private int _streamId;
  private TempBuffer _sendQueue;
  private int _sendOffset = FRAME_LENGTH;
  private int _sendFlags;

  private OutHttp _outHttp;
  
  public ChannelOutHttp2(ChannelHttp2 channel)
  {
    Objects.requireNonNull(channel);
    
    _channel = channel;
  }
  
  public void addReceive(long length)
  {
    _receiveLength += length;
  }

  public void init(int streamId, OutHttp outHttp)
  {
    Objects.requireNonNull(outHttp);
    
    _streamId = streamId;
    _outHttp = outHttp;
    
    _sendLength = 0;
    _sendCredits = outHttp.getWindow();
    
    _receiveLength = 0;
    _receiveCredits = 0;
    
    _sendOffset = FRAME_LENGTH;
    _sendFlags = 0;
    _sendQueue = null;
  }
  
  public OutHttp getOutHttp()
  {
    return _outHttp;
  }

  public void setStreamId(int streamId)
  {
    _streamId = streamId;
  }

  public void addSend(long length)
  {
    _sendLength += length;
  }
  
  public void addSendCredit(OutHttp out, int credit)
  {
    _sendCredits += credit;
    
    if (_sendQueue != null) {
      out.dataCont(this);
    }
  }
  
  public int getSendCredits()
  {
    int credit = (int) (_sendCredits - _sendLength);
    
    return credit;
  }

  void addSendBuffer(TempBuffer next, int offset, int flags)
  {
    if (_sendQueue != null) {
      TempBuffer ptr = _sendQueue;
      
      for (; ptr.getNext() != null; ptr = ptr.getNext()) {
      }
      
      ptr.setNext(next);
      
      _sendFlags = flags;
    }
    else {
      _sendQueue = next;
      _sendOffset = offset;
      _sendFlags = flags;
    }
  }

  void writeCont(OutHttp out)
    throws IOException
  {
    TempBuffer ptr = _sendQueue;
    _sendQueue = null;
    
    int offset = _sendOffset;
    _sendOffset = FRAME_LENGTH;
    
    int tailFlags = _sendFlags;
    _sendFlags = 0;

    if (tailFlags > 0 && ptr == null) {
      out.writeData(_streamId, BYTE_EMPTY, 0, 0, tailFlags);
      return;
    }

    while (ptr != null) {
      int credit = getSendCredits();
      
      TempBuffer next = ptr.getNext();
      
      int flags = tailFlags;
      
      int length = ptr.length() - offset;
      
      if (length < 0) {
        ptr.freeSelf();
        ptr = next;
        offset = FRAME_LENGTH;
        continue;
      }
      
      int sublen = Math.min(credit, length);
      
      if (next != null || sublen < length) {
        flags = 0;
      }
      
      if (sublen > 0) {
        out.writeData(_streamId, ptr.buffer(), offset, sublen, flags);
        tailFlags -= flags;
        
        addSend(sublen);
      }
      
      if (sublen < length) {
        _sendQueue = ptr;
        _sendOffset = offset + sublen;
        _sendFlags = tailFlags;
        
        out.writeBlock(_streamId);

        return;
      }
      
      ptr.freeSelf();
      
      ptr = next;
      offset = FRAME_LENGTH;
    }
    
    if (tailFlags > 0) {
      out.writeData(_streamId, new byte[0], 0, 0, tailFlags);
      return;
    }
  }

  public void close()
  {
    _channel.closeWrite();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
