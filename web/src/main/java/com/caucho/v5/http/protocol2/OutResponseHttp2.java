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

import com.caucho.v5.http.protocol.OutHttpApp;
import com.caucho.v5.io.TempBuffer;

import io.baratine.io.Buffer;

//public class OutResponseHttp2 extends OutResponseCache
public class OutResponseHttp2 extends OutHttpApp
{
  private final RequestHttp2 _request;
  //private TempBuffer _tBuf;

  OutResponseHttp2(RequestHttp2 request)
  {
    // super(response);
    
    Objects.requireNonNull(request);
    
    _request = request;
    
    start();
  }
  
  @Override
  public void start()
  {
    super.start();
    
  }
  
  @Override
  public int bufferStart()
  {
    return 0;//9;
  }
  
  @Override
  protected void fillChunkHeader(TempBuffer buf, int sublen)
  {
    
  }

  /**
   * Writes data to the output. If the headers have not been written,
   * they should be written.
   */
  @Override
  protected void flush(Buffer data, boolean isEnd)
  {
    if (isClosed()) {
      throw new IllegalStateException();
    }

    _request.outProxy().write(_request, data, isEnd);
    
    /*
    boolean isHeader = ! isCommitted();
    toCommitted();
    
    MessageResponseHttp2 message;
    
    TempBuffer next = head;
    TempBuffer tBuf = null;
    
    if (head != null) {
      int headLength = head.length();
    
      if (headLength > 0) {
        tBuf = head;
      
        next = TempBuffer.allocate();
      }
    }
    
    message = new MessageResponseHttp2(_request, tBuf, isHeader, isEnd);
    _request.getOutHttp().offer(message);
    
    return next;
    */
  }
  
  //
  // implementations
  //

  /*
  @Override
  protected void flushNextImpl()
    throws IOException
  {
    if (_tBuf != null && _tBuf.getLength() > 0) {
      _tBuf = flushData(_tBuf, false);
    }
  }

  @Override
  protected void closeNextImpl() throws IOException
  {

  }
  */
}
