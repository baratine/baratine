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
public class MessageData extends MessageHttp
{
  private final int _streamId;
  private final TempBuffer _tBuf;
  private final int _offset;
  private final int _length;
  private final FlagsHttp _flags;
  
  MessageData(int streamId,
              TempBuffer tBuf,
              int offset,
              int length,
              FlagsHttp flags)
  {
    if (streamId <= 0) {
      throw new IllegalArgumentException();
    }
    
    _streamId = streamId;
    _tBuf = tBuf;
    _offset = offset;
    _length = length;
    _flags = flags;
   }
  
  protected int getStreamId()
  {
    return _streamId;
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
    int length = _length;
    
    int flags;
    
    switch (_flags) {
    case CONT_STREAM:
      flags = 0;
      break;
    case END_STREAM:
      flags = Http2Constants.END_STREAM;
      break;
    default:
      flags = 0;
      break;
    }
    
    outHttp.writeData(getStreamId(), _tBuf.buffer(), _offset, length, flags);
    
    /*
    System.out.println("  WDS: " + length);
    
    os.write((byte) (length >> 8)); 
    os.write((byte) (length));
    os.write(HttpConstants.FRAME_DATA);
    
    switch (_flags) {
    case CONT_STREAM:
      os.write(0);
      break;
    case END_STREAM:
      os.write(HttpConstants.END_STREAM);
      break;
    default:
      os.write(0);
      break;
    }
    
    BitsUtil.writeInt(os, getStreamId());
    
    os.write(_tBuf.getBuffer(), _offset, _length);
    */
    
    _tBuf.free();
  }
}
