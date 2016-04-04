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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.deploy2.DeployFactory2;
import com.caucho.v5.util.L10N;
import com.caucho.v5.web.builder.IncludeWebAmp;

import io.baratine.config.Config;

/**
 * Baratine's web-app instance builder
 */
public class WebAppFactory implements DeployFactory2<WebApp>
{
  private static final L10N L = new L10N(WebAppFactory.class);
  private static final Logger log
    = Logger.getLogger(WebAppFactory.class.getName());
  
  private static final Constructor<?> _builderCtor;
  
  
  //private final WebAppBaratineHttp _webAppHttp;

  private final HttpBaratine _http;

  private final String _id;
  private final String _path;

  private ArrayList<String> _indexFileList = new ArrayList<>();
  private ArrayList<IncludeWebAmp> _includes = new ArrayList<>();
  
  private Throwable _configException;
  private Config _config;


  /**
   * Creates the host with its environment loader.
   * @param builder 
   */
  public WebAppFactory(HttpBaratineBuilder httpBuilder,
                               HttpBaratine http)
  {
    Objects.requireNonNull(httpBuilder);
    Objects.requireNonNull(http);
    
    _id = "webapp";
    _path = "";
    
    _http = http;
    
    //_webAppHttp = webAppHttp;
    
    _config = httpBuilder.config();
    
    for (IncludeWebAmp webModule : httpBuilder.include()) {
      _includes.add(webModule);
    }
  }
  
  @Override
  public String id()
  {
    return _id;
  }
  
  // @Override
  String path()
  {
    return _path;
  }
  
  HttpBaratine http()
  {
    return _http;
  }

  public Config config()
  {
    return _config;
  }
  
  /*
  public WebAppBaratineHttp webAppHttp()
  {
    return _webAppHttp;
  }
  */
  
  //
  // configuration
  //
  
  /**
   * index-file: directory index file, such as index.html 
   */
  /*
  @Configurable
  public void addIndexFile(String fileName)
  {
    if (fileName == null || fileName.isEmpty()) {
      return;
    }
    
    if (fileName.indexOf('/') >= 0) {
      throw new ConfigException(L.l("index-file '{0}' is not a valid file"));
    }
    
    _indexFileList.add(fileName);
  }
  */
  
  /**
   * route: url to route mapping
   */
  /*
  @Configurable
  public void addRoute(RouteConfig route)
  {
    _routes.add(route);
  }
  */

  /*
  //@Override
  public EnvironmentClassLoader getClassLoader()
  {
    return _classLoader;
  }
  */

  //@Override
  public void setConfigException(Throwable e)
  {
    _configException = e;
  }

  //@Override
  public Throwable configException()
  {
    return _configException;
  }

  /**
   * Builds the web-app services
   */
  /*
  public void bind(InjectManager.InjectBuilder builder)
  {
    for (InjectBuilderWebImpl<?> binding : _webAppHttp.bindings()) {
      binding.build(builder);
    }
  }
  */

  /**
   * Builds the web-app services
   */
  /*
  public void buildServices(ServiceManagerAmp ampManager)
  {
    for (ServiceBuilderWebImpl service : _webAppHttp.services()) {
      service.build(ampManager);
    }
  }
  */

  /*
  public RouteItem []routes()
  {
    return _webAppHttp.routes();
  }
  */

  public Iterable<IncludeWebAmp> includes()
  {
    return _includes;
  }
  
  /**
   * Builds the web-app's router
   */
  /*
  public InvocationRouter<InvocationBaratine> buildRouter(WebAppBaratine webApp)
  {
    ArrayList<RouteMap> mapList = new ArrayList<>();
    
    ServiceManagerAmp manager = webApp.serviceManager();
    ServiceRefAmp serviceRef = manager.service(new RouteService()).ref();
    
    for (RouteGenerator<?> route : _routes) {
      RouteApply routeApply = new RouteApply(routeItem.getRoute(), serviceRef);
      
      mapList.add(new RouteMap(routeItem.getPath(), routeApply));
                                       
    }

    for (RouteConfig config : _routes) {
      RouteBaratine route = config.buildRoute(); 
      
      mapList.add(new RouteMap("", route));
    }
    
    RouteMap []routeArray = new RouteMap[mapList.size()];
    
    mapList.toArray(routeArray);
    
    return new InvocationRouterWebApp(webApp, routeArray);
    
    return null;

  }
    */
  
  //
  // deployment
  //

  @Override
  public WebApp get()
  {
    try {
      WebAppBuilder builder;
      
      if (_builderCtor != null) {
        builder = (WebAppBuilder) _builderCtor.newInstance(this);
      }
      else {
        builder = new WebAppBuilder(this);
      }
    
      return builder.get();
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
    
      log.log(Level.WARNING, cause.toString(), cause);
      
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      else {
        throw new RuntimeException(cause);
      }
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
      
      throw new RuntimeException(e);
    }
    
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + id() + "]";
  }
  
  public static class RouteService
  {
  }
  
  static {
    Constructor<?> builderCtor = null;
    
    try {
      String className = WebAppBuilder.class.getName() + "Framework";
      
      Class<?> cl = Class.forName(className);
      
      builderCtor = cl.getConstructor(WebAppFactory.class);
    } catch (Exception e) {
      log.finest(e.toString());
    }

    builderCtor.setAccessible(true);
    
    _builderCtor = builderCtor;
  }
}
