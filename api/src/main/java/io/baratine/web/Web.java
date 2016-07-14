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
import io.baratine.inject.Key;
import io.baratine.service.ServiceRef;
import io.baratine.service.ServiceRef.ServiceBuilder;
import io.baratine.spi.WebServerProvider;
import io.baratine.web.WebServerBuilder.SslBuilder;

/**
 * Web provides static methods to build a web server in a main() class.
 * 
 * <pre><code>
 * import static io.baratine.web.Web.*;
 * 
 * public class MyMain
 * {
 *   public static void main(String []argv)
 *   {
 *     get("/test", req-&gt;req.ok("hello, world"));
 *     
 *     start();
 *   }
 * }
 * </code></pre>
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
   * @return
   */
  static WebServerBuilder port(int port)
  {
    return builder().port(port);
  }

  /** Obtains SslBuilder allowing specifying SSL options.
   *
   * @return
   */
  static SslBuilder ssl()
  {
    return builder().ssl();
  }
  
  //
  // routing
  //

  /**
   * Enlists class for automatic service discovery. An inlcuded class
   * must be annotated with one of Baratine service annotations such as
   * @Service, @Path, @Get or @Post, etc.
   *
   * @param type
   * @return
   */
  static WebServerBuilder include(Class<?> type)
  {
    return builder().include(type);
  }
  
  //
  // view
  //

  /**
   * Registers ViewRenderer that will be used to render objects for type &lth;T>
   * e.g.
   * <pre>
   *   <code>
   *   class MyBeanRender extends ViewRender&lth;MyBean>{
   *     void render(RequestWeb req, MyBean value) {
   *       req.write(value.toString());
   *       req.ok();
   *     }
   *   }
   *
   *   Web.view(new MyBeanRender());
   *   </code>
   * </pre>
   * @param view
   * @param <T>
   * @return
   *
   * @see ViewRender
   */
  static <T> WebServerBuilder view(ViewRender<T> view)
  {
    return builder().view(view);
  }

  /**
   * Registers ViewRenderer that will be used to render objects for type &lth;T>
   * e.g.
   * <pre>
   *   <code>
   *   class MyBeanRender extends ViewRender&lth;MyBean>{
   *     void render(RequestWeb req, MyBean value) {
   *       req.write(value.toString());
   *       req.ok();
   *     }
   *   }
   *
   *   Web.view(MyBeanRender.class);
   *   </code>
   * </pre>
   * @param view
   * @param <T>
   * @return
   *
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
   * Adds class to WebServerBuilder in one of the following ways.
   *
   * If an @IncludeOn annotation is present and the specified in @IncludeOn
   * annotation class is present the class is tested for @Include annotation.
   *
   * If an @Include annotation is present the class is included using #include method.
   *
   * If a @Service annotation is present the class is included using #service method.
   *
   * If type extends {@code IncludeWeb} the class is instantiated and used to
   * generate services or includes.
   *
   * @param type
   * @return
   */
  static WebServerBuilder scan(Class<?> type)
  {
    return builder().scan(type);
  }

  /**
   * Auto discovers all classes in packages named *.autoconf.* and enlists
   * all classes annotated ith @Include and @IncludeOn annotations.
   *
   * @return
   */
  static WebServerBuilder scanAutoConf()
  {
    return builder().scanAutoconf();
  }

  /*
  static WebServerBuilder args(String []args)
  {
    return builder().args(args);
  }
  */

  /**
   * Specifies server property.
   *
   * e.g.
   * <pre>
   *   <code>
   *     Web.property("server.http", "8080");
   *   </code>
   * </pre>
   *
   * @param name
   * @param value
   * @return
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
   * @see #get;
   *
   */
  static RouteBuilder delete(String path)
  {
    return builder().delete(path);
  }

  /**
   * Configures a route corresponding ot HTTP GET method
   *
   * e.g.
   * <pre>
   *   <code>
   *     Web.get("/hello").to(req->{ req.ok("hello world"); });
   *   </code>
   * </pre>
   *
   * @param path
   * @return
   */
  static RouteBuilder get(String path)
  {
    return builder().get(path);
  }

  /**
   * Configures a route corresponding to HTTP OPTIONS method
   *
   * @param path
   * @return
   */
  static RouteBuilder options(String path)
  {
    return builder().options(path);
  }

  /**
   * Configures a route corresponding to HTTP PATCH method
   *
   * @param path
   * @return
   */
  static RouteBuilder patch(String path)
  {
    return builder().patch(path);
  }

  /**
   * Configures a route corresponding to HTTP POST method
   *
   * @param path
   * @return
   */
  static RouteBuilder post(String path)
  {
    return builder().post(path);
  }

  /**
   * Configures a route corresponding to HTTP PUT method
   *
   * @param path
   * @return
   */
  static RouteBuilder put(String path)
  {
    return builder().put(path);
  }

  /**
   * Configures a route corresponding to HTTP trace method
   * @param path
   * @return
   */
  static RouteBuilder trace(String path)
  {
    return builder().trace(path);
  }

  /**
   * Configures a 'catch all' route
   *
   * @param path
   * @return
   */
  static RouteBuilder route(String path)
  {
    return builder().route(path);
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
   * @param args
   * @return
   */
  static WebServer start(String ...args)
  {
    return builder().start(args);
  }

  static void go(String ...args)
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
