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

package com.caucho.v5.websocket.client;

import java.io.InputStream;
import java.io.Reader;
import java.util.Objects;

import com.caucho.v5.http.websocket.WebSocketBase;
import com.caucho.v5.http.websocket.WebSocketManager;
import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.io.WriteStream;
import com.caucho.v5.websocket.io.Frame;

import io.baratine.io.Buffer;
import io.baratine.pipe.Pipe;
import io.baratine.web.ServiceWebSocket;
import io.baratine.web.WebSocket;

/**
 * WebSocketClient
 */
public class WebSocketImplClient<T,S> extends WebSocketBase<T,S>
  implements WebSocket<S>
{
  private String _uri;
  private WriteStream _os;
  private Class<?> _type;
  private ServiceWebSocket<T,S> _service;

  public WebSocketImplClient(String uri,
                             WriteStream os,
                             ServiceWebSocket<T,S> service)
  {
    super(new WebSocketManager());

    Objects.requireNonNull(os);
    Objects.requireNonNull(service);

    _uri = uri;
    _os = os;

    _service = service;

    TypeRef typeRef = TypeRef.of(service.getClass());
    TypeRef typeRefService = typeRef.to(ServiceWebSocket.class);
    TypeRef type = typeRefService.param(0);

    Class<?> valueType;

    if (type != null) {
      valueType = type.rawClass();
    }
    else {
      valueType = String.class;
    }

    _type = valueType;
  }

  @Override
  public void open()
  {
    super.open();

    readInit(_type, (ServiceWebSocket) _service, null);
  }

  @Override
  public String uri()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String path()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String pathInfo()
  {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void write(Buffer data)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public void writePart(Buffer data)
  {
    // TODO Auto-generated method stub

  }

  /*
  @Override
  public void write(byte[] buffer, int offset, int length)
  {
    System.out.println("WRITE_TAIL: " + new String(buffer, offset, length));
  }
  */

  @Override
  public void writePart(byte[] buffer, int offset, int length)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public void write(String data)
  {
    // TODO Auto-generated method stub

  }

  @Override
  public void writePart(String data)
  {
    // TODO Auto-generated method stub

  }

  /*
  @Override
  public void read(OutPipe<Buffer> handler)
  {
    assertBeforeOpen();
  }
  */

  /*
  @Override
  public void read(OutPipe<Buffer> reader, int prefetch)
  {
    assertBeforeOpen();
  }
  */

/*
  @Override
  public void readString(OutPipe<String> handler)
  {
    assertBeforeOpen();
  }
  */

  /*
  @Override
  public void readInputStream(Pipe<InputStream> handler)
  {
    assertBeforeOpen();
  }

  @Override
  public void readReader(Pipe<Reader> handler)
  {
    assertBeforeOpen();
  }
  */

  /*
  @Override
  public void readFrame(OutPipe<Frame> handler)
  {
    assertBeforeOpen();
  }
  */

  /*
  @Override
  public boolean isClosed()
  {
    return true;
  }
  */

  /*
  @Override
  public void close(WebSocketClose reason, String text)
  {
    // TODO Auto-generated method stub
  }
  */

  private void assertBeforeOpen()
  {
  }

  @Override
  protected <X> Pipe<X> wrap(Pipe<X> handler)
  {
    /*
    ServiceRefAmp selfRef = ServiceRefAmp.current();

    // XXX: calling pinned lambdas is an issue
    handler = new OutPipeWrapper<>(handler);

    OutPipe<String> wrappedHandler = selfRef.pin(handler).as(OutPipe.class);
    */

    return handler;
  }

  @Override
  public void flush()
  {
    System.out.println("FLUSH:");
  }

  @Override
  public void send(Buffer buffer)
  {
    System.out.println("SEND: " + buffer);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _uri + "]";
  }
}
