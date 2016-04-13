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
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.caucho.v5.http.protocol2.InputStreamClient;
import com.caucho.v5.http.protocol2.OutputStreamClient;
import com.caucho.v5.io.ReadStream;
import com.caucho.v5.io.WriteStream;
import com.caucho.v5.util.CharBuffer;

/**
 * InputStreamHttp reads a single HTTP frame.
 */
public class ClientStream1
{
  private String _path;
  private String _method;
  private String _host = "localhost";
  private Map<String,String> _headers;
  private OutputStreamClient _os;
  private InputStreamClient _is;
  
  private boolean _isHeaderWritten;
  private ClientHttp1 _client;
  private CharBuffer _cb = new CharBuffer();
  private int _status;
  
  public ClientStream1(ClientHttp1 client,
                      String path)
  {
    Objects.requireNonNull(client);
    Objects.requireNonNull(path);
    
    _client = client;
    
    _path = path;
  }
  
  public ClientStream1 method(String method)
  {
    Objects.requireNonNull(method);
    
    _method = method;
    
    return this;
  }
  
  public ClientStream1 host(String host)
  {
    Objects.requireNonNull(host);
    
    _host = host;
    
    return this;
  }
  
  public void setHeader(String key, String value)
  {
    if (_headers == null) {
      _headers = new HashMap<>();
    }
    
    _headers.put(key, value);
  }

  public OutputStream getOutputStream()
  {
    if (_os == null) {
      if (_method == null) {
        _method = "POST";
      }
    }
    
    return _os;
  }

  public int getStatus()
  {
    return _status;
  }
  
  public InputStreamClient getInputStream()
  {
    if (_is == null) {
      writeData();

      _is = parseResult();
    }
    
    
    return _is;
  }

  ReadStream getIn()
  {
    return _client.getIn();
  }
  
  void writeData()
  {
    try {
      WriteStream out = _client.getOut();
    
      out.print(_method);
      out.print(" ");
      out.print(_path);
      out.print(" HTTP/1.1");
      
      out.print("\r\nhost: localhost");
      // headers
      
      out.print("\r\n\r\n");
      out.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  InputStreamClient parseResult()
  {
    try {
      ReadStream is = _client.getIn();
      
      StringBuilder sb = new StringBuilder();
      
      _status = parseStatus(is, sb);
      
      return parseHeaders(is, sb);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private int parseStatus(ReadStream is, StringBuilder sb)
    throws IOException
  {
    int ch;
    
    for (ch = is.read(); ch == ' ' || ch == '\r' || ch == '\n'; ch = is.read()) {
    }

    sb.setLength(0);
    for (; ch > 0 && ch != ' ' && ch != '\n'; ch = is.read()) {
      sb.append((char) ch);
    }

    for (; ch == ' '; ch = is.read()) {
    }

    int status = 0;
    for (; ch > 0 && ch != ' ' && ch != '\n'; ch = is.read()) {
      if ('0' <= ch && ch <= '9') {
        status = 10 * status + ch - '0';
      }
      else {
        throw new IOException("Bad status");
      }
    }

    for (; ch == ' '; ch = is.read()) {
    }

    sb.setLength(0);
    for (; ch > 0 && ch != ' ' && ch != '\n'; ch = is.read()) { 
      sb.append((char) ch);
    }

    for (; ch > 0 && ch != '\n'; ch = is.read()) {
    }
    
    return status;
  }

  private InputStreamClient parseHeaders(ReadStream is, StringBuilder keyBuf)
    throws IOException
  {
    int ch;
    
    long length = -1;
    
    StringBuilder valueBuf = new StringBuilder();

    while (true) {
      keyBuf.setLength(0);
      
      for (ch = is.read(); ch > 0 && ch != ' ' && ch != ':' && ch != '\n' && ch != '\r'; ch = is.read()) {
        if ('A' <= ch && ch <= 'Z') {
          ch += 'a' - 'A';
        }

        keyBuf.append((char) ch);
      }
      
      if (ch < 0 || keyBuf.length() == 0) {
        if (length >= 0) {
          return new InputStreamLengthClient1(this, length);
        }
        else {
          return new InputStreamChunkClient1(this);
        }
      }

      for (; ch == ' ' || ch == ':'; ch = is.read()) {
      }

      valueBuf.setLength(0);
      for (; ch > 0 && ch != '\n'; ch = is.read()) {
        if (ch == '\r') {
        }
        else {
          valueBuf.append((char) ch);
        }
      }
      
      String key = keyBuf.toString();
      String value = valueBuf.toString();
      
      switch (key) {
      case "content-length":
        length = Long.parseLong(value);
        break;
      }
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _path + "]";
  }
}
