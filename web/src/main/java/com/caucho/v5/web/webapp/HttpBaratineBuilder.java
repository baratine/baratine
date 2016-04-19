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

import java.util.ArrayList;
import java.util.Objects;

import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.deploy2.DeployHandle2;
import com.caucho.v5.deploy2.DeploySystem2;
import com.caucho.v5.http.container.HttpContainerBuilder;
import com.caucho.v5.web.builder.IncludeWebAmp;

import io.baratine.config.Config;

/**
 * Configuration for the <cluster> and <server> tags.
 */
//@Configurable
public class HttpBaratineBuilder extends HttpContainerBuilder
{
  private ArrayList<IncludeWebAmp> _includes = new ArrayList<>();

  private HttpBaratine _http;
  //private Config _config;
  
  public HttpBaratineBuilder(Config config,
                             ServerBartender selfServer, 
                             String serverHeader)
  {
    super(selfServer, config, serverHeader);
  }
  
  HttpBaratine http()
  {
    return _http;
  }
  
  /*
  public Config config()
  {
    return _config;
  }
  */

  public void include(IncludeWebAmp include)
  {
    _includes.add(include);
  }
  
  public Iterable<IncludeWebAmp> include()
  {
    return _includes;
  }

  public DeployHandle2<WebApp> buildWebAppHandle(HttpBaratine http)
  {
    Objects.requireNonNull(http);
    
    String id = "webapp";
    
    DeploySystem2 deploySystem = DeploySystem2.current();
    
    DeployHandle2<WebApp> webAppHandle
      = deploySystem.createHandle(id, http.logger()); 
    
    WebAppFactory factory = new WebAppFactory(this, http);
    
    webAppHandle.factory(factory);
    
    return webAppHandle;
  }

  @Override
  public HttpBaratine build()
  {
    return new HttpBaratine(this);
  }
}
