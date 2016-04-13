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

import com.caucho.v5.io.TempBuffer;

import io.baratine.io.Buffer;

public class OutResponseHttp
  extends OutResponseBase
{
  private static final int CHUNK_HEADER = 8;

  private RequestHttp _request;

  private boolean _isChunked;
  private boolean _isHeaders;

  OutResponseHttp(RequestHttp request)
  {
    //super(request);

    _request = request;
  }

  @Override
  public boolean isClosed()
  {
    return super.isClosed() || _request.isClosed();
  }

  /**
   * initializes the Response stream at the beginning of a request.
   */
  @Override
  public void start()
  {
    _isChunked = false;
    _isHeaders = false;
    
    super.start();
  }

  //
  // implementations
  //
  
  @Override
  protected int bufferStart()
  {
    if (_isChunked) {
      return CHUNK_HEADER;
    }
    else {
      return 0;
    }
  }
  
  @Override
  public boolean isChunked()
  {
    return _isChunked;
  }
  
  @Override
  public void upgrade()
  {
    if (_isChunked) {
      _isChunked = false;
      System.out.println("Upgrade disables chunked encoding");
    }
  }

  @Override
  protected final void flush(Buffer data, boolean isEnd)
  {
    if (data == null || data.length() == 0) {
      data = null;
    }
    
    if (_isChunked) {
      // writeChunkHeader(ptr.buffer(), CHUNK_HEADER, ptr.length() - CHUNK_HEADER);
    }
    
    if (! _isHeaders) {
      _isHeaders = true;

      if (! isEnd) {
        _isChunked = _request.calculateChunkedEncoding();
      }
    }
      
    _request.outProxy().write(_request, data, isEnd);
  }

  /**
   * Fills the chunk header.
   */
  @Override
  protected void fillChunkHeader(TempBuffer tBuf, int length)
  {
    if (length == 0)
      throw new IllegalStateException();
    
    byte []buffer = tBuf.buffer();

    buffer[0] = (byte) '\r';
    buffer[1] = (byte) '\n';
    buffer[2] = hexDigit(length >> 12);
    buffer[3] = hexDigit(length >> 8);
    buffer[4] = hexDigit(length >> 4);
    buffer[5] = hexDigit(length);
    buffer[6] = (byte) '\r';
    buffer[7] = (byte) '\n';
  }

  /**
   * Returns the hex digit for the value.
   */
  private static byte hexDigit(int value)
  {
    value &= 0xf;

    if (value <= 9) {
      return (byte) ('0' + value);
    }
    else {
      return (byte) ('a' + value - 10);
    }
  }
}
