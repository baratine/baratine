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

package com.caucho.v5.web.builder;

import java.util.function.Supplier;

import javax.inject.Provider;

import io.baratine.convert.Convert;
import io.baratine.inject.Injector;
import io.baratine.inject.Injector.BindingBuilder;
import io.baratine.inject.Injector.InjectAutoBind;
import io.baratine.inject.Injector.InjectorBuilder;
import io.baratine.inject.Key;
import io.baratine.service.ServiceRef;
import io.baratine.web.HttpMethod;
import io.baratine.web.ViewRender;
import io.baratine.web.WebServer;
import io.baratine.web.WebServerBuilder;

class WebServerBuilderBase implements WebServerBuilder
{
  protected WebServerBuilder delegate()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  //
  // config env
  //

  @Override
  public WebServerBuilder property(String key, String value)
  {
    return delegate().property(key, value);
  }

  @Override
  public WebServerBuilder scan(Package pkg)
  {
    return delegate().scan(pkg);
  }

  @Override
  public <S,T> Convert<S,T> converter(Class<S> source, Class<T> target)
  {
    return delegate().converter(source, target);
  }
  
  //
  // injection
  //

  @Override
  public Injector injector()
  {
    return delegate().injector();
  }

  @Override
  public <T> BindingBuilder<T> bean(Class<T> type)
  {
    return delegate().bean(type);
  }

  @Override
  public <T> BindingBuilder<T> bean(T bean)
  {
    return delegate().bean(bean);
  }

  @Override
  public <T> BindingBuilder<T> beanProvider(Provider<T> provider)
  {
    return delegate().beanProvider(provider);
  }

  /*
  @Override
  public <T,X> BindingBuilder<T> beanFunction(Function<X,T> function)
  {
    return delegate().beanFunction(function);
  }
  */

  /*
  @Override
  public <T,U> BindingBuilder<T> provider(Key<U> parent, Method m)
  {
    return delegate().provider(parent, m);
  }
  */

  @Override
  public InjectorBuilder autoBind(InjectAutoBind autoBind)
  {
    return delegate().autoBind(autoBind);
  }
  
  //
  // services
  //

  @Override
  public <T> ServiceRef.ServiceBuilder service(Class<T> api)
  {
    return delegate().service(api);
  }
  
  @Override
  public <T> ServiceRef.ServiceBuilder service(Class<T> serviceClass,
                                               Supplier<? extends T> supplier)
  {
    return delegate().service(serviceClass, supplier);
  }

  @Override
  public ServiceRef.ServiceBuilder service(Key<?> key, Class<?> api)
  {
    return delegate().service(key, api);
  }
  
  //
  // router
  //

  @Override
  public RouteBuilder route(HttpMethod method, String path)
  {
    return delegate().route(method, path);
  }

  @Override
  public RouteBuilder websocket(String path)
  {
    return delegate().websocket(path);
  }

  @Override
  public WebServerBuilder include(Class<?> type)
  {
    return delegate().include(type);
  }

  @Override
  public WebServerBuilder args(String []args)
  {
    return delegate().args(args);
  }

  @Override
  public WebServerBuilder scanAutoconf()
  {
    return delegate().scanAutoconf();
  }

  @Override
  public <T> WebServerBuilder view(ViewRender<T> view)
  {
    return delegate().view(view);
  }

  @Override
  public <T> WebServerBuilder view(Class<? extends ViewRender<T>> view)
  {
    return delegate().view(view);
  }
  
  //
  // web server
  //

  @Override
  public WebServerBuilder port(int port)
  {
    return delegate().port(port);
  }

  @Override
  public SslBuilder ssl()
  {
    return delegate().ssl();
  }

  @Override
  public WebServer start(String ...args)
  {
    throw new IllegalStateException(getClass().getName());
  }

  /*
  @Override
  public void join()
  {
    throw new IllegalStateException(getClass().getName());
  }
  */

  @Override
  public void go(String ...args)
  {
    throw new IllegalStateException(getClass().getName());
  }
}
