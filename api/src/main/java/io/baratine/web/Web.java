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

import java.util.Objects;
import java.util.function.Supplier;

import io.baratine.inject.Injector;
import io.baratine.service.ServiceRef;
import io.baratine.service.ServiceRef.ServiceBuilder;
import io.baratine.spi.WebServerProvider;
import io.baratine.web.WebBuilder.RouteBuilder;
import io.baratine.web.WebServerBuilder.SslBuilder;

/**
 * Web provides static methods to build a web server in a main() class.
 * <p>
 * <blockquote><pre>
 * import static io.baratine.web.Web.*;
 * public class MyMain
 * {
 *   public static void main(String []argv)
 *   {
 *     get("/test", req-&gt;req.ok("hello, world"));
 *     start();
 *   }
 * }
 * </pre></blockquote>
 *
 * @see RequestWeb
 */
public interface Web
{
  //
  // webserver
  //

  /**
   * Specifies http port (defaults to 8080)
   *
   * @param port
   * @return instance of {@code WebServerBuilder}
   */
  static WebServerBuilder port(int port)
  {
    return builder().port(port);
  }

  /**
   * Obtains SslBuilder allowing specifying SSL options for the server
   *
   * @return instance of SslBuilder
   */
  static SslBuilder ssl()
  {
    return builder().ssl();
  }

  //
  // routing
  //

  /**
   * Enlists class for automatic service discovery. An included class
   * must be annotated with one of Baratine service annotations such as
   * &#64;Service, &#64;Path, &#64;Get or &#64;Post, etc.
   *
   * @param type service to include
   * @return instance of {@code WebServerBuilder}
   */
  static WebServerBuilder include(Class<?> type)
  {
    return builder().include(type);
  }

  //
  // view
  //

  /**
   * Registers ViewRenderer that will be used to render objects for type &lt;T&gt;
   * e.g.
   * <blockquote><pre>
   *   class MyBeanRender extends ViewRender&lt;MyBean&gt;{
   *     void render(RequestWeb req, MyBean value) {
   *       req.write(value.toString());
   *       req.ok();
   *     }
   *   }
   *   Web.view(new MyBeanRender());
   * </pre></blockquote>
   *
   * @param view instance of a {@code ViewRender}
   * @param <T>  type of the supported by the renderer value
   * @return instance of the {@code WebServerBuilder}
   * @see ViewRender
   */
  static <T> WebServerBuilder view(ViewRender<T> view)
  {
    return builder().view(view);
  }

  /**
   * Registers ViewRenderer that will be used to render objects for type &lt;T&gt;
   * e.g.
   * <blockquote><pre>
   *   class MyBeanRender extends ViewRender&lt;MyBean&gt;{
   *     void render(RequestWeb req, MyBean value) {
   *       req.write(value.toString());
   *       req.ok();
   *     }
   *   }
   *
   *   Web.view(MyBeanRender.class);
   * </pre></blockquote>
   *
   * @param view class of the {@code ViewRender}
   * @param <T>  type of values supported by this renderer
   * @return instance of {@code WebServerBuilder}
   * @see #view(ViewRender)
   * @see ViewRender
   */
  static <T> WebServerBuilder view(Class<? extends ViewRender<T>> view)
  {
    return builder().view(view);
  }

  //
  // configuration
  //

  /**
   * Scans classes contained in the specified package for Services and Beans.
   * <p>
   * If an IncludeOnClass annotation is present and the specified in IncludeOnClass
   * annotation class is present the class is included.
   * <p>
   * If an Include annotation is present the class is included using {@link #include} method.
   * <p>
   * If a Service annotation is present the class is included using {@link #service} method.
   * <p>
   * If class extends {@code IncludeWeb} the class is instantiated and used to
   * generate services or includes, via a call to its build() method.
   *
   * @param pkg package to scan
   * @return instance of {@code WebServerBuilder}
   * @see IncludeWeb
   * @see io.baratine.config.Include
   */
  static WebServerBuilder scan(Package pkg)
  {
    return builder().scan(pkg);
  }

  /**
   * Auto discovers all classes in packages named *.autoconf.* and enlists
   * classes annotated with Include and @IncludeOnClass annotations for
   * deployment.
   *
   * @return instance of {@code WebServerBuilder}
   */
  static WebServerBuilder scanAutoConf()
  {
    return builder().scanAutoconf();
  }

  /**
   * Specifies configuration property. The specified property becomes available
   * via the {@code Config} instance.
   * <p>
   * e.g.
   * <blockquote><pre>
   *   Web.property("server.http", "8080");
   * </pre></blockquote>
   *
   * @param name  property name
   * @param value property value
   * @return instance of {@code WebServerBuilder}
   */
  static WebServerBuilder property(String name, String value)
  {
    Objects.requireNonNull(name);
    Objects.requireNonNull(value);

    return builder().property(name, value);
  }

  //
  // injection
  //

  /**
   * Registers a bean for injection.
   *
   * @param type instance class of the bean
   */
  static <T> Injector.BindingBuilder<T> bean(Class<T> type)
  {
    Objects.requireNonNull(type);

    return builder().bean(type);
  }

  /**
   * Registers a bean instance for injection.
   */
  static <T> Injector.BindingBuilder<T> bean(T bean)
  {
    Objects.requireNonNull(bean);

    return builder().bean(bean);
  }

  //
  // services
  //

  static <T> ServiceBuilder service(Supplier<? extends T> supplier)
  {
    Objects.requireNonNull(supplier);

    //return BaratineWebProvider.builder().service(supplier);
    return null;
  }

  /**
   * Registers class as a service. The class must be a valid
   * Baratine service class.
   *
   * @param serviceClass
   * @return
   */
  static ServiceRef.ServiceBuilder service(Class<?> serviceClass)
  {
    Objects.requireNonNull(serviceClass);

    return builder().service(serviceClass);
  }

  //
  // routes
  //

  /**
   * Configures a route corresponding to HTTP DELETE method
   *
   * @param path
   * @return
   * @see #get(String) ;
   */
  static RouteBuilder delete(String path)
  {
    return builder().delete(path);
  }

  /**
   * Configures a route corresponding ot HTTP GET method
   * <p>
   * e.g.
   * <blockquote><pre>
   *   Web.get("/hello").to(req-&gt;{ req.ok("hello world"); });
   * </pre></blockquote>
   *
   * @param path specifies path
   * @return instance of {@code RouteBuilder} for GET method at path URI
   */
  static RouteBuilder get(String path)
  {
    return builder().get(path);
  }

  /**
   * Configures a route corresponding to HTTP OPTIONS method
   *
   * @param path specifies path
   * @return instance of {@code RouteBuilder} for GET method at path URI
   */
  static RouteBuilder options(String path)
  {
    return builder().options(path);
  }

  /**
   * Configures a route corresponding to HTTP PATCH method
   *
   * @param path specifies path
   * @return instance of {@code RouteBuilder} for GET method at path URI
   */
  static RouteBuilder patch(String path)
  {
    return builder().patch(path);
  }

  /**
   * Configures a route corresponding to HTTP POST method
   *
   * @param path specifies path
   * @return instance of {@code RouteBuilder} for GET method at path URI
   */
  static RouteBuilder post(String path)
  {
    return builder().post(path);
  }

  /**
   * Configures a route corresponding to HTTP PUT method
   *
   * @param path specifies path
   * @return instance of {@code RouteBuilder} for GET method at path URI
   */
  static RouteBuilder put(String path)
  {
    return builder().put(path);
  }

  /**
   * Configures a route corresponding to HTTP trace method
   *
   * @param path specifies path
   * @return instance of {@code RouteBuilder} for GET method at path URI
   */
  static RouteBuilder trace(String path)
  {
    return builder().trace(path);
  }

  /**
   * Configures a 'catch all' route
   *
   * @param path specifies path
   * @return instance of {@code RouteBuilder} for GET method at path URI
   */
  static RouteBuilder path(String path)
  {
    return builder().path(path);
  }

  /**
   * Configures WebSocket handler for specified path
   *
   * @param path
   * @return
   */
  static RouteBuilder websocket(String path)
  {
    return builder().websocket(path);
  }

  //
  // lifecycle
  //

  /**
   * Creates an instance of a server and starts it, returning the instance.
   *
   * @param args
   * @return
   */
  static WebServer start(String... args)
  {
    return builder().start(args);
  }

  static void go(String... args)
  {
    builder().go(args);
  }

  /*
  static void join(String ...args)
  {
    builder().join();
  }
  */

  /**
   * Returns an instance of WebServerBuilder
   *
   * @return
   */
  static WebServerBuilder builder()
  {
    return WebServerProvider.current().webBuilder();
  }
}
