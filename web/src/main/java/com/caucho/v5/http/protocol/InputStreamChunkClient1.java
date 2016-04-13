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

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.caucho.v5.http.protocol2.InputStreamClient;
import com.caucho.v5.io.ReadStream;

/**
 * InputStreamHttp reads a single HTTP frame.
 */
public class InputStreamChunkClient1 extends InputStreamClient
{
  private ClientStream1 _stream;
  private ReadStream _is;
  
  private int _length;
  private int _pos;
  private boolean _isClosed;
  
  public InputStreamChunkClient1(ClientStream1 stream)
  {
    _stream = stream;
    _is = stream.getIn();

  }
  
  @Override
  public int getStatus()
  {
    return _stream.getStatus();
  }
  
  @Override
  public String getHeader(String key)
  {
    return null;
  }
  
  @Override
  public Map<String,String> getHeaders()
  {
    return null;
  }

  @Override
  public int read() throws IOException
  {
    if (_pos < _length) {
      _pos++;
      
      return _is.read();
    }
    else if (_isClosed) {
      return -1;
    }
    else {
      _length = readChunkSize();
      _pos = 0;
      
      if (_length <= 0) {
        _isClosed = true;
        
        return -1;
      }
      
      _pos++;
      
      return _is.read();
    }
  }
  
  private int readChunkSize()
    throws IOException
  {
    int ch;
    InputStream is = _is;
    
    for (ch = is.read(); ch == '\r' || ch == '\n'; ch = is.read()) {
    }
    
    int size = 0;
    for (; ch > 0 && ch != '\r'; ch = is.read()) { 
      if ('0' <= ch && ch <= '9') {
        size = 16 * size + ch - '0';
      }
      else if ('a' <= ch && ch <= 'f') {
        size = 16 * size + 10 + ch - 'a';
      }
      else if ('A' <= ch && ch <= 'F') {
        size = 16 * size + 10 + ch - 'A';
      }
      else {
        throw new IOException("invalid chunk");
      }
    }
    
    if (ch <= 0) {
      return -1;
    }
    
    if (ch == '\r') {
      ch = is.read();
      
      if (ch == '\n') {
        return size;
      }
    }

    throw new IOException("invalid chunk tail");
  }
}
