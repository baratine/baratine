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

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import io.baratine.config.Config;
import io.baratine.inject.Injector;
import io.baratine.io.Buffer;
import io.baratine.service.Result;
import io.baratine.service.ServiceRef;

public interface RequestWeb extends OutWeb<Buffer>, Result<Object>
{
  String protocol();
  String version();
  
  String method();
  
  String uri();
  String path();
  String pathInfo();
  String path(String string);
  Map<String,String> pathMap();
  
  String query();
  String query(String key);
  Map<String,List<String>> queryMap();
  
  String header(String string);
  String cookie(String name);
  
  String host();
  int port();
  
  InetSocketAddress ipRemote();
  InetSocketAddress ipLocal();
  String ip();

  SecureWeb secure();
  
  <X> X attribute(Class<X> key);
  <X> void attribute(X value);
  
  ServiceRef session(String name);
  <X> X session(Class<X> type);
  
  <X> X body(Class<X> type);

  <X> void body(BodyReader<X> reader, Result<X> result);

  <X> void body(Class<X> type,
                Result<X> result);

  InputStream inputStream();

  //
  // response
  //
  
  RequestWeb status(HttpStatus status);
  
  RequestWeb header(String key, String value);
  CookieBuilder cookie(String key, String value);
  RequestWeb length(long length);
  RequestWeb type(String contentType);
  RequestWeb encoding(String contentType);
  
  void upgrade(Object service);

  void ok();
  @Override
  void ok(Object value);
  void ok(Object result, Throwable exn);
  
  @Override
  void fail(Throwable exn);
  
  void halt();
  void halt(HttpStatus status);
  
  void redirect(String address);

  @Override
  default void handle(Object value, Throwable exn)
  {
    if (exn != null) {
      fail(exn);
    }
    else {
      ok(value);
    }
  }
  
  //
  // resources
  //
  
  // configuration
  
  Config config();
  
  // injection
  
  Injector injector();
  // <X> X instance(Class<X> type, Annotation ...anns);

  //ServiceManager services();
  
  ServiceRef service(String address);
  <X> X service(Class<X> type);
  <X> X service(Class<X> type, String id);
  
  public interface SecureWeb
  {
    String protocol();
    String cipherSuite();
  }
  
  public interface CookieBuilder
  {
    CookieBuilder httpOnly(boolean isHttpOnly);
    CookieBuilder secure(boolean isSecure);
    CookieBuilder path(String path);
    CookieBuilder domain(String domain);
  }
}
