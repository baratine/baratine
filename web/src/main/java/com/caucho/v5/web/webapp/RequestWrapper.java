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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.util.Map;

import io.baratine.config.Config;
import io.baratine.inject.Injector;
import io.baratine.io.Buffer;
import io.baratine.io.Buffers;
import io.baratine.pipe.Credits;
import io.baratine.service.Result;
import io.baratine.service.ServiceRef;
import io.baratine.service.Services;
import io.baratine.web.HttpStatus;
import io.baratine.web.MultiMap;
import io.baratine.web.RequestWeb;


/**
 * Wrapper for filter requests.
 */
public class RequestWrapper implements RequestWebSpi
{
  protected RequestWebSpi delegate() 
  { 
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public RequestWeb write(Buffer buffer)
  {
    return delegate().write(buffer);
  }

  @Override
  public RequestWeb write(byte[] buffer, int offset, int length)
  {
    return delegate().write(buffer, offset, length);
  }

  @Override
  public RequestWeb write(String value)
  {
    return delegate().write(value);
  }

  @Override
  public RequestWeb write(char[] buffer, int offset, int length)
  {
    return delegate().write(buffer, offset, length);
  }

  @Override
  public RequestWeb flush()
  {
    return delegate().flush();
  }

  @Override
  public Writer writer()
  {
    return delegate().writer();
  }

  @Override
  public OutputStream output()
  {
    return delegate().output();
  }

  @Override
  public RequestWeb push(OutFilterWeb filter)
  {
    return delegate().push(filter);
  }

  @Override
  public Credits credits()
  {
    return delegate().credits();
  }

  /*
  @Override
  public void handle(Object value, Throwable fail)
  {
    throw new IllegalStateException(getClass().getName());
  }
  */

  @Override
  public String scheme()
  {
    return delegate().scheme();
  }

  @Override
  public String version()
  {
    return delegate().version();
  }

  @Override
  public String method()
  {
    return delegate().method();
  }

  @Override
  public String uri()
  {
    return delegate().uri();
  }

  @Override
  public String path()
  {
    return delegate().path();
  }

  @Override
  public String pathInfo()
  {
    return delegate().pathInfo();
  }

  @Override
  public String path(String key)
  {
    return delegate().path(key);
  }

  @Override
  public Map<String, String> pathMap()
  {
    return delegate().pathMap();
  }

  @Override
  public String query()
  {
    return delegate().query();
  }

  @Override
  public String query(String key)
  {
    return delegate().query(key);
  }

  @Override
  public MultiMap<String,String> queryMap()
  {
    return delegate().queryMap();
  }

  @Override
  public String header(String key)
  {
    return delegate().header(key);
  }

  @Override
  public String cookie(String name)
  {
    return delegate().cookie(name);
  }

  @Override
  public String host()
  {
    return delegate().host();
  }

  @Override
  public int port()
  {
    return delegate().port();
  }

  @Override
  public InetSocketAddress ipRemote()
  {
    return delegate().ipRemote();
  }

  @Override
  public InetSocketAddress ipLocal()
  {
    return delegate().ipLocal();
  }

  @Override
  public String ip()
  {
    return delegate().ip();
  }

  @Override
  public SecureWeb secure()
  {
    return delegate().secure();
  }

  @Override
  public <X> X attribute(Class<X> key)
  {
    return delegate().attribute(key);
  }

  @Override
  public <X> void attribute(X value)
  {
    delegate().attribute(value);
  }

  @Override
  public ServiceRef session(String name)
  {
    return delegate().session(name);
  }

  @Override
  public <X> X session(Class<X> type)
  {
    return delegate().session(type);
  }

  /*
  @Override
  public <X> X body(Class<X> type)
  {
    return delegate().body(type);
  }
  */

  /*
  @Override
  public <X> void body(BodyReader<X> reader, Result<X> result)
  {
    delegate().body(reader, result);
  }
  */

  @Override
  public <X> void body(Class<X> type, Result<X> result)
  {
    delegate().body(type, result);
  }

  @Override
  public InputStream inputStream()
  {
    return delegate().inputStream();
  }

  @Override
  public Config config()
  {
    return delegate().config();
  }

  @Override
  public Injector injector()
  {
    return delegate().injector();
  }

  @Override
  public Services services()
  {
    return delegate().services();
  }

  @Override
  public ServiceRef service(String address)
  {
    return delegate().service(address);
  }

  @Override
  public <X> X service(Class<X> type)
  {
    return delegate().service(type);
  }

  @Override
  public <X> X service(Class<X> type, String id)
  {
    return delegate().service(type, id);
  }

  @Override
  public Buffers buffers()
  {
    return delegate().buffers();
  }

  @Override
  public RequestWeb status(HttpStatus status)
  {
    return delegate().status(status);
  }

  @Override
  public RequestWeb header(String key, String value)
  {
    return delegate().header(key, value);
  }

  @Override
  public CookieBuilder cookie(String key, String value)
  {
    return delegate().cookie(key, value);
  }

  @Override
  public RequestWeb length(long length)
  {
    return delegate().length(length);
  }

  @Override
  public RequestWeb type(String contentType)
  {
    return delegate().type(contentType);
  }

  @Override
  public RequestWeb encoding(String contentEncoding)
  {
    return delegate().encoding(contentEncoding);
  }

  @Override
  public void upgrade(Object service)
  {
    delegate().upgrade(service);
  }

  @Override
  public void ok()
  {
    delegate().ok();
  }

  @Override
  public void ok(Object value)
  {
    delegate().ok(value);
  }

  /*
  @Override
  public void ok(Object value, Throwable exn)
  {
    if (exn != null) {
      fail(exn);
    }
    else {
      ok(value);
    }
  }
  */

  @Override
  public void fail(Throwable exn)
  {
    delegate().fail(exn);
  }

  @Override
  public void halt()
  {
    delegate().halt();
  }

  @Override
  public void halt(HttpStatus status)
  {
    delegate().halt(status);
  }

  @Override
  public void redirect(String address)
  {
    delegate().redirect(address);
    
  }
}
