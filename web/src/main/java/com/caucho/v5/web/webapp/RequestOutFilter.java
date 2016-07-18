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

package com.caucho.v5.web.webapp;

import java.io.IOException;
import java.util.Objects;

import com.caucho.v5.http.protocol.RequestOut;
import com.caucho.v5.io.TempBuffer;

import io.baratine.web.RequestWeb.OutFilterWeb;

/**
 * User facade for baratine http requests.
 */
public class RequestOutFilter extends RequestOutputStream
{
  private RequestWebSpi _delegate;
  private RequestOut _outDelegate;
  private OutFilterWeb _outFilter;
  
  private TempBuffer _tBuf = TempBuffer.create();

  protected RequestOutFilter(RequestWebSpi delegate,
                             OutFilterWeb outFilter)
  {
    Objects.requireNonNull(delegate);
//    Objects.requireNonNull(outDelegate);
    Objects.requireNonNull(outFilter);
    
    _delegate = delegate;
  //  _outDelegate = outDelegate;
    _outFilter = outFilter;
  }
  
  //@Override
  public RequestWebSpi delegate()
  {
    return _delegate;
  }
  
  public RequestOut out()
  {
    return _outDelegate;
  }
  
  public OutFilterWeb filter()
  {
    return _outFilter;
  }
  
  @Override
  public void length(long length)
  {
    filter().length(delegate(), length);
  }
  
  @Override
  public void type(String contentType)
  {
    filter().type(delegate(), contentType);
  }
  
  @Override
  public void header(String key, String value)
  {
    filter().header(delegate(), key, value);
  }
  
  @Override
  public void write(byte[] buffer, int offset, int length)
  {
    while (length > 0) {
      int sublen = Math.min(length, _tBuf.available());
        
      if (sublen > 0) {
        _tBuf.write(buffer, offset, length);
          
        length -= sublen;
        offset += sublen;
      }
      else {
        filter().write(delegate(), _tBuf);
        _tBuf.clear();
      }
    }
  }

  @Override
  public byte[] buffer() throws IOException
  {
    return _tBuf.buffer();
  }

  @Override
  public int offset() throws IOException
  {
    return _tBuf.length();
  }

  @Override
  public void offset(int offset) throws IOException
  {
    _tBuf.length(offset);
    
    if (_tBuf.available() == 0) {
      filter().write(delegate(), _tBuf);
      _tBuf.clear();
    }
    
  }

  @Override
  public byte[] nextBuffer(int offset) throws IOException
  {
    offset(offset);

    return _tBuf.buffer();
  }

  @Override
  public boolean isClosed()
  {
    return false;
  }

  @Override
  public void write(int value) throws IOException
  {
    System.out.println("WR: " + value);
    Thread.dumpStack();
  }
  
  @Override
  public void close()
    throws IOException
  {
    if (_tBuf == null) {
      return;
    }
    
    if (_tBuf.length() > 0) {
      filter().write(delegate(), _tBuf);
    }
    
    _tBuf.free();
    _tBuf = null;
    
    filter().ok(delegate());
    
    delegate().ok();
  }
}
