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
import java.io.OutputStream;
import java.util.Objects;

import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.util.L10N;

/**
 * InputStreamHttp reads a single HTTP frame.
 */
public class OutputStreamClient extends OutputStream 
  implements Http2Constants
{
  private static final L10N L = new L10N(OutputStreamClient.class);
  
  private ClientStream2 _stream;
  
  private byte []_tempBuffer;
  private int _offset;
  
  private TempBuffer _tBuf;
  
  private boolean _isCloseWrite;
  
  public OutputStreamClient(ClientStream2 stream)
  {
    Objects.requireNonNull(stream);
    
    _stream = stream;
  }
  
  @Override
  public void write(int value)
    throws IOException
  {
    if (_tempBuffer == null) {
      allocateBuffer();
    }
    
    _tempBuffer[_offset++] = (byte) value;
  }
  
  @Override
  public void write(byte []buffer, int offset, int length) 
    throws IOException
  {
    while (length > 0) {
      if (_tempBuffer == null) {
        allocateBuffer();
      }
      
      byte []tempBuffer = _tempBuffer;
      int tempOffset = _offset;
      
      int sublen = Math.min(tempBuffer.length - tempOffset, length);
    
      System.arraycopy(buffer, offset, tempBuffer, tempOffset, sublen);
      
      length -= sublen;
      offset += sublen;
      _offset += sublen;
      
      if (sublen == 0) {
        flush();
      }
    }
  }
  
  private void allocateBuffer()
  {
    if (_isCloseWrite) {
      throw new IllegalStateException(L.l("stream is closed"));
    }
    
    _tBuf = TempBuffer.create();
    _tempBuffer = _tBuf.buffer();
  }
  
  @Override
  public void flush()
    throws IOException
  {
    TempBuffer tBuf = _tBuf;
    
    if (tBuf != null) {
      _tBuf = null;
      _tempBuffer = null;
      tBuf.length(_offset);
      _offset = 0;
    }
    
    _stream.writeData(tBuf, _isCloseWrite ? FlagsHttp.END_STREAM : FlagsHttp.CONT_STREAM);
  }
  
  @Override
  public void close()
    throws IOException
  {
    _isCloseWrite = true;
    
    flush();
  }
}
