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

import java.util.Objects;
import java.util.function.Supplier;

import io.baratine.web.InstanceBuilder;
import io.baratine.web.ServiceWebSocket;
import io.baratine.web.WebSocketBuilder;

public class WebSocketBuilderImpl implements WebSocketBuilder
{
  private WebServerBuilderImpl _serverBuilder;
  
  private String _path;
  
  //private WebSocket<S> _webSocket;
  
  WebSocketBuilderImpl(WebServerBuilderImpl serverBuilder,
                       String path)
  {
    Objects.requireNonNull(serverBuilder);
    Objects.requireNonNull(path);
    
    _serverBuilder = serverBuilder;
    _path = path;
  }

  @Override
  public <T,S> void to(ServiceWebSocket<T,S> service)
  {
    Objects.requireNonNull(service);
    
    _serverBuilder.include(new WebSocketItem(_path, service));
  }

  @Override
  public <T,S> InstanceBuilder<ServiceWebSocket<T,S>>
  to(Class<? extends ServiceWebSocket<T,S>> type)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T,S> void to(Supplier<? extends ServiceWebSocket<T,S>> supplier)
  {
    throw new UnsupportedOperationException();
  }
}
