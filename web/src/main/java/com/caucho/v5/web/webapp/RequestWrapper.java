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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.http.protocol.ConnectionHttp;
import com.caucho.v5.http.protocol.OutResponseBase;
import com.caucho.v5.http.protocol.RequestFacadeBase;
import com.caucho.v5.http.protocol.RequestHttpBase;
import com.caucho.v5.http.protocol.RequestHttpState;
import com.caucho.v5.http.protocol.RequestUpgrade;
import com.caucho.v5.http.protocol.WebCookie;
import com.caucho.v5.http.protocol2.OutHeader;
import com.caucho.v5.http.websocket.WebSocketBaratineImpl;
import com.caucho.v5.inject.InjectorAmp;
import com.caucho.v5.inject.type.TypeRef;
import com.caucho.v5.io.TempBuffer;
import com.caucho.v5.io.TempInputStream;
import com.caucho.v5.io.WriteBuffer;
import com.caucho.v5.network.port.ConnectionProtocol;
import com.caucho.v5.network.port.StateConnection;
import com.caucho.v5.util.Base64Util;
import com.caucho.v5.util.CurrentTime;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.RandomUtil;
import com.caucho.v5.util.Utf8Util;
import com.caucho.v5.web.CookieWeb;

import io.baratine.config.Config;
import io.baratine.inject.Injector;
import io.baratine.io.Buffer;
import io.baratine.pipe.Pipe;
import io.baratine.service.Result;
import io.baratine.service.ServiceException;
import io.baratine.service.ServiceRef;
import io.baratine.web.BodyReader;
import io.baratine.web.Form;
import io.baratine.web.HttpStatus;
import io.baratine.web.MultiMap;
import io.baratine.web.OutWeb;
import io.baratine.web.RequestWeb;
import io.baratine.web.ServiceWebSocket;


/**
 * Wrapper for filter requests.
 */
public class RequestWrapper implements RequestWeb
{
  protected RequestWeb delegate() 
  { 
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public OutWeb<Buffer> push(Pipe<Buffer> out)
  {
    return delegate().push(out);
  }

  @Override
  public OutWeb<Buffer> write(Buffer buffer)
  {
    return delegate().write(buffer);
  }

  @Override
  public OutWeb<Buffer> write(byte[] buffer, int offset, int length)
  {
    return delegate().write(buffer, offset, length);
  }

  @Override
  public OutWeb<Buffer> write(String value)
  {
    return delegate().write(value);
  }

  @Override
  public OutWeb<Buffer> write(String value, String enc)
  {
    return delegate().write(value, enc);
  }

  @Override
  public OutWeb<Buffer> write(char[] buffer, int offset, int length)
  {
    return delegate().write(buffer, offset, length);
  }

  @Override
  public OutWeb<Buffer> write(char[] buffer, int offset, int length, String enc)
  {
    return delegate().write(buffer, offset, length, enc);
  }

  @Override
  public OutWeb<Buffer> flush()
  {
    return delegate().flush();
  }

  @Override
  public Writer writer()
  {
    return delegate().writer();
  }

  @Override
  public Writer writer(String enc)
  {
    return delegate().writer(enc);
  }

  @Override
  public OutputStream output()
  {
    return delegate().output();
  }

  @Override
  public void handle(Object value, Throwable fail)
  {
    throw new IllegalStateException(getClass().getName());
  }

  @Override
  public String protocol()
  {
    return delegate().protocol();
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
  public Map<String, List<String>> queryMap()
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
  public boolean secure()
  {
    return delegate().secure();
  }

  @Override
  public X509Certificate[] certs()
  {
    return delegate().certs();
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

  @Override
  public <X> X body(Class<X> type)
  {
    return delegate().body(type);
  }

  @Override
  public <X> void body(BodyReader<X> reader, Result<X> result)
  {
    delegate().body(reader, result);
  }

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
  public RequestWeb cookie(String key, String value)
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
