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

package io.baratine.web;

import java.io.IOException;

/**
 * Interface ServiceWebSocket should be implemented by Services that communicate
 * with the client's via WebSocket.
 * <p>
 * e.g.
 * <p>
 * <blockquote><pre>
 *   &#64;Path("/stock-updates")
 *   public class StockTicker implements ServiceWebSocket&lt;String, Quote&gt; {
 *     private WebSocket&lt;Quote&gt; _ws;
 *
 *     &#64;Override
 *      public void open(WebSocket&lt;Quote&gt; ws)
 *      {
 *        _ws = ws;
 *      }
 *
 *      &#64;Override
 *      public void next(String value, WebSocket&lt;Quote&gt; webSocket)
 *        throws IOException {}
 *
 *      public void update(Quote quote)
 *      {
 *        _ws.next(quote);
 *      }
 *   }
 * </pre></blockquote>
 *
 * Registering such a service can be done in two following ways.
 * a) using Web.include(Class) call
 * b) using upgrade call from an enclosing Service.
 *
 * e.g.
 *
 * <blockquote><pre>
 *   &#64;Session
 *   public class UserSession {
 *     StockTicker _ticker;
 *
 *     &#64;WebSocketPath("/stock-updates")
 *     public void upgrade(RequestWeb request) {
 *       _ticker = new StockTicker();
 *       request.upgrade(_ticker);
 *     }
 *   }
 * </pre></blockquote>
 *
 * Service can be registered as a stand alone
 *
 * @param <T> type of incoming values
 * @param <S> type of outgoing values
 * @see io.baratine.service.Session
 * @see WebSocketPath
 *
 */
@FunctionalInterface
public interface ServiceWebSocket<T, S>
{
  /**
   * Called when WebSocket is established
   * @param webSocket WebSocket to the client
   */
  default void open(WebSocket<S> webSocket)
    throws Exception
  {
  }

  /**
   * Called when new message arrives via WebSocket
   * @param value value from the client
   * @param webSocket WebSocket to the client
   * @throws Exception
   */
  void next(T value, WebSocket<S> webSocket) throws Exception;

  /**
   * WebSocket ping
   *
   * @param value ping value from the client
   * @param webSocket WebSocket to the client
   * @throws Exception
   */
  default void ping(String value, WebSocket<S> webSocket)
    throws Exception
  {
    webSocket.pong(value);
  }

  default void pong(String value, WebSocket<S> webSocket)
    throws Exception
  {
  }

  /**
   * Called when WebSocket closes
   *
   * @param code reason for closing
   * @param msg closing message
   * @param webSocket WebSocket to the client
   * @throws IOException
   */
  default void close(WebSocketClose code,
                     String msg,
                     WebSocket<S> webSocket)
    throws Exception
  {
    close(webSocket);
  }

  default void close(WebSocket<S> webSocket)
  {
    webSocket.close();
  }
}
