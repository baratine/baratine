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

import io.baratine.web.WebBuilder.RouteBuilder;

/**
 * Class IncludeWebBase provides a convenient base for extending by concrete
 * implementations.
 * <p>
 * <blockquote><pre>
 * public class MyIncludeWebHello extends IncludeWebBase
 * {
 *   &#64;Override
 *   public void build()
 *   {
 *     get("/hello").to(requestWeb -&gt; requestWeb.ok("hello world!"));
 *   }
 * }
 * </pre></blockquote>
 */
abstract public class IncludeWebBase implements IncludeWeb
{
  private WebBuilder _builder;

  /**
   * Method build should contain the actual configuration code for the WebBuilder.
   */
  abstract public void build();

  /**
   * Returns associated WebBuilder
   *
   * @return instance of WebBuilder
   */
  public WebBuilder builder()
  {
    return _builder;
  }

  /**
   * Returns RouteBuilder for specified path and 'GET' HTTP method. The returned
   * instance of RouteBuilder should be used to further define the route.
   * <p>
   * e.g.
   * <blockquote><pre>
   *   public void build() {
   *     get("/hello").to(request-&gt;request.ok("hello world"));
   *   }
   * </pre></blockquote>
   *
   * @param path path e.g. "/test"
   * @return instance of RouteBuilder
   * @see RouteBuilder
   */
  public RouteBuilder get(String path)
  {
    return builder().get(path);
  }

  /**
   * Returns RouteBuilder for specified path and 'POST' HTTP method. The returned
   * instance of RouteBuilder should be used to further define the route.
   * <p>
   * e.g.
   * <blockquote><pre>
   *   public void build() {
   *     get("/register").to(request-&gt;register(request));
   *   }
   * </pre></blockquote>
   *
   * @param path path e.g. "/register"
   * @return instance of RouteBuilder
   * @see RouteBuilder
   */
  public RouteBuilder post(String path)
  {
    return builder().post(path);
  }

  /**
   * Returns RouteBuilder for specified path and HTTP method "UNKNOWN", which is
   * a 'catch-all' method for methods other than explicitly defined methods.
   * <p>
   * The returned instance of RouteBuilder should be used to further define the
   * route.
   * <p>
   * e.g.
   * <blockquote><pre>
   *   public void build() {
   *     //define route for HTTP "GET" method
   *     get("/hello").to(request-&gt;request.ok("hello world"));
   *     //
   *     //define route for methods other than the "GET" method
   *     route("/hello").to(request-&gt;request.ok("hello world"));
   *   }
   * </pre></blockquote>
   *
   * @param path path e.g. "/hello"
   * @return instance of RouteBuilder
   * @see RouteBuilder
   */
  public RouteBuilder route(String path)
  {
    return builder().path(path);
  }

  /**
   * Returns RouteBuilder for specified path for WebSocket.
   * <p>
   * The returned instance of RouteBuilder should be used to further define the
   * route.
   *
   * @param path path e.g. "/updates"
   * @return instance of RouteBuilder
   * @see io.baratine.service.Session
   * @see ServiceWebSocket
   */
  public RouteBuilder websocket(String path)
  {
    return builder().websocket(path);
  }

  /**
   * Method build(WebBuilder) initialized the fields of IncludeWebBase and
   * calls method build().
   *
   * @param builder
   * @see #build()
   */
  @Override
  public void build(WebBuilder builder)
  {
    _builder = builder;

    build();
  }
}
