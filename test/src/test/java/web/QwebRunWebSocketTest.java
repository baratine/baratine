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
 * @author Alex Rojkov
 */

package web;

import java.io.IOException;

import io.baratine.service.Session;
import io.baratine.web.Path;
import io.baratine.web.RequestWeb;
import io.baratine.web.ServiceWebSocket;
import io.baratine.web.WebSocket;
import io.baratine.web.WebSocketPath;

import com.caucho.junit.ServiceTest;
import com.caucho.junit.State;
import com.caucho.junit.WebRunnerBaratine;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(WebRunnerBaratine.class)
@ServiceTest(QwebRunWebSocketTest.Q_basicService.class)
public class QwebRunWebSocketTest
{
  @Test
  public void testSend(@Path("/test") Q_client client) throws Throwable
  {
    client.send("hello");

    Thread.sleep(10);

    Assert.assertEquals("  server open"
                        + "\n  client open"
                        + "\n  server receive: hello"
                        + "\n  client receive: world", State.state());

    client.close();

    Thread.sleep(100);

    Assert.assertEquals("\n  server close", State.state());
  }

  public static class Q_client implements ServiceWebSocket<String,String>
  {
    private WebSocket<String> _webSocket;

    @Override
    public void open(WebSocket<String> webSocket)
    {
      _webSocket = webSocket;
      State.add("\n  client open");
    }

    public void send(String value)
    {
      _webSocket.write(value);
    }

    @Override
    public void next(String value, WebSocket<String> webSocket)
      throws IOException
    {
      State.add("\n  client receive: " + value);
    }

    @Override
    public void close(WebSocket<String> webSocket)
    {
      State.add("\n  client close");
    }

    public void close()
    {
      _webSocket.close();
    }
  }

  @Session
  public static class Q_basicService
  {
    @WebSocketPath("/test")
    public void update(RequestWeb request)
    {
      request.upgrade(new Q_basicWebsocket());
    }
  }

  public static class Q_basicWebsocket
    implements ServiceWebSocket<String,String>
  {
    @Override
    public void open(WebSocket<String> webSocket)
    {
      State.add("  server open");
    }

    @Override
    public void next(String value, WebSocket<String> webSocket)
      throws IOException
    {
      State.add("\n  server receive: " + value);
      webSocket.next("world");
    }

    @Override
    public void close(WebSocket<String> webSocket)
    {
      State.add("\n  server close");
    }
  }
}
