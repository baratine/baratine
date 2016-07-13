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
import java.io.StringReader;
import java.io.Writer;

import com.caucho.v5.http.websocket.WebSocketManager;
import com.caucho.v5.json.io.JsonReaderImpl;
import com.caucho.v5.json.io.JsonWriterImpl;
import com.caucho.v5.json.ser.SerializerJson;
import com.caucho.v5.json.ser.JsonFactory;
import com.caucho.v5.vfs.TempCharBuffer;

import io.baratine.web.ServiceWebSocket;
import io.baratine.web.WebSocket;

/**
 * Reads and writes websockets.
 */
public class WebSocketManagerFramework extends WebSocketManager
{
  private JsonFactory _serializer = new JsonFactory();
  
  @Override
  public <S> void serialize(WebSocket<S> ws, S value)
    throws IOException
  {
    if (value instanceof String) {
      ws.write((String) value);
      return;
    }
    
    try (WriterWs writer = new WriterWs(ws)) {
      SerializerJson<S> ser = (SerializerJson) _serializer.serializer(value.getClass());
      
      try (JsonWriterImpl jsOut = _serializer.out(writer)) {
        ser.writeTop(jsOut, value);
      }
    }
  }

  public <T,S> ServiceWebSocket<String, S>
  createSerializer(Class<T> type,
                   ServiceWebSocket<T, S> service)
  {
    SerializerJson ser = _serializer.serializer(type);
    
    return new ServiceWebSocketJson<T,S>(ser, service); 
  }
  
  private class ServiceWebSocketJson<T,S> implements ServiceWebSocket<String,S>
  {
    private ServiceWebSocket<T,S> _service;
    private SerializerJson _ser;
    
    ServiceWebSocketJson(SerializerJson ser,
                         ServiceWebSocket<T,S> service)
    {
      _ser = ser;
      _service = service;
    }

    @Override
    public void next(String data, WebSocket<S> webSocket) throws Exception
    {
      try (StringReader reader = new StringReader(data)) {
        try (JsonReaderImpl in = _serializer.in(reader)) {
          T value = (T) _ser.read(in);
          
          _service.next(value, webSocket);
        }
      }
    }
  }
  
  private class WriterWs extends Writer
  {
    private WebSocket<?> _webSocket;

    private TempCharBuffer _tBuf;
    private char []_buffer;
    private int _offset;
    private boolean _isClose;
    
    WriterWs(WebSocket<?> webSocket)
    {
      _webSocket = webSocket;
      
      _tBuf = TempCharBuffer.allocate();
      _buffer = _tBuf.buffer();
    }

    @Override
    public void write(int ch) throws IOException
    {
      char []buffer = _buffer;
      int offset = _offset;
      
      if (buffer.length <= offset) {
        _webSocket.writePart(new String(_buffer, 0, _offset));
        offset = 0;
      }
      
      buffer[offset++] = (char) ch;
      
      _offset = offset;
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException
    {
      while (len > 0) {
        char []tBuffer = _buffer;
        int tOffset = _offset;
        
        int sublen = Math.min(tBuffer.length - tOffset, len);
        System.arraycopy(cbuf, off, tBuffer, tOffset, sublen);
        
        len -= sublen;
        off += sublen;
        _offset = tOffset + sublen;
      
        if (len > 0) {
          _webSocket.writePart(new String(_buffer, 0, _offset));
          _offset = 0;
        }
      }
    }

    @Override
    public void flush() throws IOException
    {
      if (_isClose) {
        _webSocket.write(new String(_buffer, 0, _offset));
        _offset = 0;
      }
      else if (_offset > 0) {
        _webSocket.writePart(new String(_buffer, 0, _offset));
        _offset = 0;
      }
    }

    @Override
    public void close() throws IOException
    {
      _isClose = true;
      flush();
      
      TempCharBuffer tBuf = _tBuf;
      _tBuf = null;
      _buffer = null;
      
      tBuf.freeSelf();
    }
    
  }
}
