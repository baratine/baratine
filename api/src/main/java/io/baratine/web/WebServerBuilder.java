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

import io.baratine.inject.Injector.InjectorBuilder;

public interface WebServerBuilder extends WebBuilder
{
  WebServerBuilder args(String []args);
  
  WebServerBuilder port(int port);
  
  SslBuilder ssl();
  
  @Override
  WebServerBuilder include(Class<?> type);

  WebServerBuilder scan(Class<?> type);
  
  WebServerBuilder scanAutoconf();
  
  //@Override
  WebServerBuilder property(String name, String value);

  @Override
  <T> WebServerBuilder view(ViewRender<T> view);

  @Override
  <T> WebServerBuilder view(Class<? extends ViewRender<T>> view);
  
  //
  // injection
  //
  
  
  
  //
  // services
  //
  
  //<U> ServiceBuilder<U> service(Supplier<?> supplier);
  
  //
  // routes
  //
  
  WebServer start(String ...args);
  
  //void join(String ...args);
  
  void go(String ...args);
  
  public interface SslBuilder {
    
  }
}
