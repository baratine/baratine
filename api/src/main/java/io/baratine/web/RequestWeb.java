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

import java.io.OutputStream;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import io.baratine.config.Config;
import io.baratine.inject.Injector;
import io.baratine.io.Buffer;
import io.baratine.io.Buffers;
import io.baratine.pipe.Credits;
import io.baratine.service.Result;
import io.baratine.service.ResultChain;
import io.baratine.service.ServiceRef;
import io.baratine.service.Services;

/**
 * Interface RequestWeb provides methods to access information in http request.
 * <p>
 * e.g.
 * <blockquote><pre>
 *   &#64;Service
 *   public class MyService {
 *     &#64;Get
 *     public void now(RequestWeb request) {
 *      request.ok(new java.util.Date());
 *     }
 *   }
 * </pre></blockquote>
 */
public interface RequestWeb extends ResultChain<Object> // OutWeb
{
  /**
   * Returns protocol scheme (http vs. https)
   *
   * @return scheme used for request
   */
  String scheme();

  /**
   * Returns protocol version HTTP/1.1, HTTP/1.0 or HTTP/2.0
   *
   * @return protocol version used for request
   */
  String version();

  /**
   * Return HTTP method e.g. POST
   *
   * @return http method
   */
  String method();

  /**
   * Returns request URI
   *
   * @return request uri
   */
  String uri();

  /**
   * Returns raw request URI
   *
   * @return raw request uri
   */
  String uriRaw();

  /**
   * Returns the matching part of the URI. i.e. the part that matches the path
   * mapping
   *
   * @return
   */
  String path();

  String pathInfo();

  /**
   * Returns value of parametrized URI path
   * e.g. request /get/foo to service mapped at /get/{id} will return 'foo' for
   * path 'id'
   * <p>
   * <blockquote><pre>
   *  public static class TestService
   *  {
   *    &#64;Get("/get/{id}")
   *    public void get(RequestWeb r)
   *   {
   *     r.ok(r.path("id"));
   *   }
   * }
   * </pre></blockquote>
   *
   * @param string
   * @return value matching named pattern
   */
  String path(String string);

  /**
   * Returns Map which path name parts and their corresponding values parsed from
   * the URI.
   * <p>
   * e.g.
   * <blockquote><pre>
   * &#64;Get("/map/{x}/{y}/{z}")
   * public void map(RequestWeb request)
   * {
   *   // curl http://localhost:8080/map/XV/YV/ZV
   *   request.ok(request.pathMap()); // {x=XV, y=XV, z=ZV}
   * }
   * </pre></blockquote>
   *
   * @return Map of part names to part values
   */
  Map<String,String> pathMap();

  /**
   * Returns query string
   *
   * @return query string
   */
  String query();

  /**
   * Returns query parameter value for a specified query parameter name
   *
   * @param key query parameter name
   * @return query parameter value
   */
  String query(String key);

  /**
   * Returns map of query parameter name to parameter values
   *
   * @return parsed query as a {@code MultiMap}
   * @see MultiMap
   */
  MultiMap<String,String> queryMap();

  /**
   * Returns value of a specified header
   *
   * @param key header name
   * @return header value
   */
  String header(String key);

  /**
   * Returns map of header name to header values
   *
   * @return headers' names and values as a {@code MultiMap}
   */
  MultiMap<String,String> headerMap();

  /**
   * Returns cookie value of a matching cookie
   *
   * @param name cookie name
   * @return cookie value
   */
  String cookie(String name);

  /**
   * Returns map of cookie name to cookie values
   *
   * @return cookies's names a values as a {@code MultiMap}
   */
  Map<String,String> cookieMap();

  /**
   * Returns value of a 'Host' header
   *
   * @return Host header value
   */
  String host();

  /**
   * Returns server port
   *
   * @return server port
   */
  int port();

  /**
   * Returns remote address (client ip)
   *
   * @return
   */
  InetSocketAddress ipRemote();

  /**
   * Returns local address (server ip)
   *
   * @return server's {@code InetSocketAddress}
   */
  InetSocketAddress ipLocal();

  /**
   * Returns remote address as a string
   *
   * @return unresolved client ip
   */
  String ip();

  /**
   * Returns SSL information for a request or null if request is not secure
   *
   * @return client's SSL
   */
  SecureWeb secure();

  /**
   * Retrieves attribute from the request. Attributes are set using
   * overloaded attribute method.
   *
   * @param key class of the attribute to retrieve
   * @param <X> type
   * @return value of the attribute or null if no value is set
   */
  <X> X attribute(Class<X> key);

  /**
   * Sets a request attribute. RequestWeb keeps attributes in a map
   * keyed by attribute's class.
   *
   * @param value an attribute object
   * @param <X>   type
   */
  <X> void attribute(X value);

  /**
   * Returns ServiceRef handle of an associated session
   *
   * @param name session service name
   * @return SessionRef handle or null
   */
  ServiceRef session(String name);

  /**
   * Instantiates session of specified and binds it to the request.
   * Subsequent requests share the instantiated session.
   *
   * @param type class specifying the session service
   * @param <X>  type
   * @return session
   */
  <X> X session(Class<X> type);

  /**
   * Converts request body to requested in 'type' parameter object.
   * The supported types are InputStream, Reader, Form and a JavaBean(POJO).
   * Form requires that the content be encoded using 'application/x-www-form-urlencoded'.
   * JavaBean convertion is supported for Content type 'application/json'
   *
   * @param type   target type for conversion
   * @param result async holder for conversion result
   * @param <X>    type
   */
  <X> void body(Class<X> type,
                Result<X> result);

  /**
   * Wraps BiConsumer specified in the 'after' parameter into a Result
   * and calls body(type, result).
   *
   * @param type  target type for conversion
   * @param after conversion consumer
   * @param <X>   type
   */
  default <X> void bodyThen(Class<X> type,
                            BiConsumer<X,RequestWeb> after)
  {
    body(type, then(after));
  }

  //
  // response
  //

  /**
   * Sets response HTTP status
   *
   * @param status
   * @return
   */
  RequestWeb status(HttpStatus status);

  /**
   * Sets response header
   *
   * @param key
   * @param value
   * @return
   */
  RequestWeb header(String key, String value);

  /**
   * Adds a cookie to response
   *
   * @param key
   * @param value
   * @return
   */
  CookieBuilder cookie(String key, String value);

  /**
   * Sets response length
   *
   * @param length
   * @return
   */
  RequestWeb length(long length);

  /**
   * Sets response 'Content-Type' response header
   *
   * @param contentType
   * @return
   */
  RequestWeb type(String contentType);

  /**
   * Sets response encoding e.g. 'UTF-8'
   *
   * @param encoding
   * @return
   */
  RequestWeb encoding(String encoding);

  /**
   * Upgrades protocol to a higher level protocol such as WebSocket. Currently
   * only WebSocket is supported.
   * <p>
   * Parameter service must be an instance of {@code ServiceWebSocket}
   *
   * @param service instance of ServiceWebSocket
   * @see ServiceWebSocket
   */
  void upgrade(Object service);

  /**
   * Completes processing with empty result
   */
  void ok();

  /**
   * Completes processing with result specified in a value
   *
   * @param value
   */
  @Override
  void ok(Object value);

  /**
   * Completes processing with a fail status and exception
   *
   * @param exn Throwable instance
   */
  @Override
  void fail(Throwable exn);

  /**
   * Halts response
   */
  void halt();

  /**
   * Halts response with specified status
   */
  void halt(HttpStatus status);

  /**
   * Sends an redirect i.e. new location with a 302 HTTP status code
   *
   * @param address
   */
  void redirect(String address);

  //
  // resources
  //

  // configuration

  /**
   * Returns configuration object for web app serving this request
   *
   * @return Config instance
   */
  Config config();

  // injection

  /**
   * Returns an injector instance for web app serving this request
   *
   * @return {@code Injector} instance
   */
  Injector injector();

  // services

  /**
   * Returns Services (service manager) instance for web app serving this request
   *
   * @return {@code Services} manager
   */
  Services services();

  /**
   * Returns ServiceRef instance for service at specified address
   *
   * @param address service address
   * @return ServiceRef instance
   */
  ServiceRef service(String address);

  /**
   * Looks up service instance for specified type
   *
   * @param type type of the Service.
   * @param <X> type
   * @return Service instance
   */
  <X> X service(Class<X> type);

  /**
   * Looks up Asset instance for specified type and id
   *
   * @param type Asset type
   * @param id Asset id
   * @param <X> type
   * @return Asset instance
   */
  <X> X service(Class<X> type, String id);

  // buffers

  /**
   * Returns Buffers factory
   *
   * @return instance of Buffers
   */
  Buffers buffers();

  /**
   * Writes contents of buffer into response
   *
   * @param buffer buffer with data
   * @return instance of RequestWeb for chaining calls
   */
  RequestWeb write(Buffer buffer);

  /**
   * Writes contents of buffer into response
   *
   * @param buffer buffer with data
   * @param offset data offset
   * @param length number of bytes to write
   * @return instance of RequestWeb for chaining calls
   */
  RequestWeb write(byte []buffer, int offset, int length);

  /**
   * Writes string into response
   *
   * @param value string to output
   * @return instance of RequestWeb for chaining calls
   */
  RequestWeb write(String value);

  /**
   * Writes char buffer into response
   *
   * @param buffer buffer with data
   * @param offset data offset
   * @param length number of chars to write
   * @return instance of RequestWeb for chaining calls
   */
  RequestWeb write(char []buffer, int offset, int length);

  /**
   * Flushes response to the client
   *
   * @return instance of RequestWeb for chaining calls
   */
  RequestWeb flush();

  /**
   * Obtains response Writer
   *
   * @return instance of response writer
   * @see java.io.Writer
   */
  Writer writer();

  /**
   * Obtains response output
   * @return instance of response output stream
   * @see java.io.OutputStream
   */
  OutputStream output();

  /**
   * Obtains credits used for finer flow control of data from server to client.
   *
   * @return instance of Credits
   */
  Credits credits();

  /**
   * Wraps response into an instance of a Filter for finer control on response
   * data. E.g. GzipFilter might be used to compress the output data and set
   * appropriate headers
   *
   * @param outFilter instance of a filter
   * @return
   */
  RequestWeb push(OutFilterWeb outFilter);

  /**
   * Interface OutFilterWeb defines contract for response filters.
   *
   * Response filters can be used to modify response headers and body.
   *
   * For example, response filter could be used to compress the response.
   */
  interface OutFilterWeb
  {
    /**
     * Calls header method on the supplied in 'request' parameter instance
     * of RequestWeb.
     *
     * Method header sets response header.
     *
     * @param request request object
     * @param key header name
     * @param value header value
     */
    default void header(RequestWeb request, String key, String value)
    {
      request.header(key, value);
    }

    /**
     * Delegates to RequestWeb.type(String) method of the supplied in
     * 'request' parameter instance of RequestWeb.
     *
     * Method type sets "Content-Type" header.
     *
     * @param request request object
     * @param type content type of the response
     */
    default void type(RequestWeb request, String type)
    {
      request.type(type);
    }

    /**
     * Delegates to RequestWeb.length(long) method of the supplied in
     * 'request' parameter instance of RequestWeb.
     *
     * Method length sets "Content-Length" header.
     *
     * @param request request object
     * @param length content length
     */
    default void length(RequestWeb request, long length)
    {
      request.length(length);
    }

    /**
     * Intercepts response's write method.
     *
     * At this point the custom method should
     * process the 'buffer' and write to RequestWeb specified as 'out' parameter
     * results of its processing. E.g. gzip compressed buffer
     *
     * @param out request object
     * @param buffer data
     */
    void write(RequestWeb out, Buffer buffer);

    /**
     * Intercepts ok call to RequestWeb.
     *
     * Custom filter might make use of this method to do additional logging
     * or modify the response.
     *
     * @param out request object
     */
    void ok(RequestWeb out);

    /**
     * Delegates to RequestWeb.credits() method returning instance of Credits.
     *
     * @param out request object
     * @return credits
     */
    default Credits credits(RequestWeb out)
    {
      return out.credits();
    }
  }

  //
  // chaining
  //

  default <U> Result<U> then(BiConsumer<U,RequestWeb> after)
  {
    return ResultChain.then(this, after);
  }

  /**
   * Interface SecureWeb provides access to secure protocol configuration of the
   * connection established with the client.
   */
  interface SecureWeb
  {
    /**
     * Returns protocol name. E.g. 'TLS1.2'
     *
     * @return protocol name
     */
    String protocol();

    /**
     * Cipher suite used for the secure connection
     *
     * @return cipher suite name
     */
    String cipherSuite();
  }

  /**
   * Interface CookieBuilder provides methods to build a cookie.
   *
   * E.g.
   * <blockquote><pre>
   *   requestWeb.cookie("preferences", "value").secure(true).domain("baratine.io");
   * </pre></blockquote>
   */
  interface CookieBuilder
  {
    /**
     * Makes cookie HttpOnly
     *
     * @param isHttpOnly true if cookie should be HttpOnly false otherwise
     * @return builder instance for call chaining
     */
    CookieBuilder httpOnly(boolean isHttpOnly);

    /**
     * Makes cookie Secure
     *
     * @param isSecure true if cookie should be secure, false otherwise
     * @return builder instance for call chaining
     */
    CookieBuilder secure(boolean isSecure);

    /**
     * Sets cookie's path attribute
     *
     * @param path cookie path E.g. "/MyService"
     * @return builder instance for call chaining
     */
    CookieBuilder path(String path);

    /**
     * Sets cookie's domain
     *
     * @param domain cookie domain value e.g. "baratine.io"
     * @return builder instance for call chaining
     */
    CookieBuilder domain(String domain);

    /**
     * Sets cookie expiry
     *
     * @param time desired expiry value e.g. 30
     * @param unit using specified TimeUnit e.g. TimeUnit.MINUTES
     * @return builder instance for call chaining
     */
    CookieBuilder maxAge(long time, TimeUnit unit);
  }
}
