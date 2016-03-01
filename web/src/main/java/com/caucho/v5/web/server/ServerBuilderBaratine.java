/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R)
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.v5.web.server;

import java.util.ArrayList;
import java.util.Objects;

import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.http.container.HttpContainerBuilder;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.web.builder.ServiceBuilderWebImpl;
import com.caucho.v5.web.webapp.HttpBaratineBuilder;

import io.baratine.config.Config;
import io.baratine.inject.InjectManager.IncludeInject;
import io.baratine.web.IncludeWeb;

/**
 * Builds a baratine server.
 */
public class ServerBuilderBaratine extends ServerBuilder
{
  private final ArrayList<IncludeInject> _bindings = new ArrayList<>();
  private final ArrayList<IncludeWeb> _includes = new ArrayList<>();
  
  /**
   * Creates a new baratine server.
   */
  public ServerBuilderBaratine(Config config)
  {
    super(config);
  }
  
  @Override
  public String getProgramName()
  {
    return "Baratine";
  }

  @Override
  protected HttpContainerBuilder createHttpBuilder(ServerBartender selfServer,
                                                   String serverHeader)
  {
    HttpBaratineBuilder builder
      = new HttpBaratineBuilder(config(), selfServer, serverHeader);
    
    for (IncludeWeb route : _includes) {
      builder.include(route);
    }
    
    return builder;
  }

  public void bind(IncludeInject binding)
  {
    Objects.requireNonNull(binding);
    
    _bindings.add(binding);
  }
  
  public Iterable<IncludeInject> bindings()
  {
    return _bindings;
  }

  public void service(ServiceBuilderWebImpl service)
  {
    Objects.requireNonNull(service);
    
    throw new UnsupportedOperationException();
  }

  public void include(IncludeWeb route)
  {
    Objects.requireNonNull(route);
    
    _includes.add(route);
  }
  
  public Iterable<IncludeWeb> routes()
  {
    return _includes;
  }
  
  @Override
  protected ServerBase build(SystemManager systemManager,
                             ServerBartender serverSelf)
    throws Exception
  {
    return new ServerBaratine(this, systemManager, serverSelf);
  }
  
  @Override
  public ServerBaratine build()
  {
    return (ServerBaratine) super.build();
  }
}
