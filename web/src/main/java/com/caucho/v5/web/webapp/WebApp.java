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

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.deploy2.DeployInstance2;
import com.caucho.v5.http.dispatch.InvocationRouter;
import com.caucho.v5.http.websocket.WebSocketManager;
import com.caucho.v5.inject.InjectorAmp;
import com.caucho.v5.inject.InjectorAmp.InjectBuilderAmp;
import com.caucho.v5.loader.DynamicClassLoader;
import com.caucho.v5.loader.EnvironmentClassLoader;
import com.caucho.v5.util.IdentityGenerator;

import io.baratine.config.Config;
import io.baratine.io.Buffers;

/**
 * Baratine's web-app handle. 
 */
public class WebApp
  implements InvocationRouter<InvocationBaratine>, DeployInstance2
{
  private static final Logger log
    = Logger.getLogger(WebApp.class.getName());
  
  private final String _id;
  private final String _path;
    
  private final EnvironmentClassLoader _classLoader;
  
  private InvocationRouter<InvocationBaratine> _router;

  private ServicesAmp _services;
  
  private Buffers _buffers;

  private InjectorAmp _injectManager;

  private Throwable _configException;

  private Config _config;

  private BodyResolver _bodyResolver;

  private WebSocketManager _wsManager;
  
  private IdentityGenerator _idGenerator;

  /**
   * Creates the web-app instance
   */
  public WebApp(WebAppBuilder builder)
  {
    _id = builder.id();
    _path = builder.path();
    
    _classLoader = builder.classLoader();
    Objects.requireNonNull(_classLoader);
    
    if (_classLoader != Thread.currentThread().getContextClassLoader()) {
      throw new IllegalStateException();
    }
    
    _config = builder.config();
    
    InjectBuilderAmp injectBuilder = builder.injectBuilder();
    injectBuilder.context(true);
    
    // initialize context
    injectBuilder.get();
    
    _services = builder.serviceBuilder().raw();
    
    builder.build(this);
    
    _configException = builder.configException();
    
    _config = builder.config();
    
    
    //builder.bind(injectBuilder);
    
    //_injectManager = injectBuilder.get();
    _injectManager = injectBuilder.get();
    
    try {
      //_ampManager = builder.serviceBuilder().getRaw();
      _services = builder.serviceBuilder().get();
      
      //Amp.setContextManager(_ampManager);
      
      //_ampManager.start();
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
      e.printStackTrace();
      throw e;
    }
    
    _buffers = builder.buffers();
    Objects.requireNonNull(_buffers);
    //builder.buildServices(_ampManager);
    
    _router = builder.buildRouter(this);
    Objects.requireNonNull(_router);
    
    _services = builder.serviceBuilder().start();
    
    int prime = 287093;
    _idGenerator = IdentityGenerator.newGenerator()
                                    .node(_services.node().nodeIndex())
                                    .increment(prime)
                                    .get();
                                        
    _bodyResolver = builder.bodyResolver();
    
    _wsManager = builder.webSocketManager();
  }
  
  public String id()
  {
    return _id;
  }
  
  public String getWebAppPath()
  {
    return _path;
  }

  @Override
  public DynamicClassLoader classLoader()
  {
    return _classLoader;
  }

  @Override
  public Throwable configException()
  {
    return _configException;
  }

  @Override
  public InvocationBaratine routeInvocation(InvocationBaratine invocation)
  {
    return _router.routeInvocation(invocation);
  }

  public Config config()
  {
    return _config;
  }
  
  // @Override
  public InjectorAmp inject()
  {
    return _injectManager;
  }

  // @Override
  public ServicesAmp services()
  {
    return _services;
  }

  public int node()
  {
    return services().node().nodeIndex();
  }

  public Buffers buffers()
  {
    return _buffers;
  }

  public BodyResolver bodyResolver()
  {
    return _bodyResolver;
  }

  public WebSocketManager wsManager()
  {
    return _wsManager;
  }
  
  /**
   * identifier generator.
   */
  public long nextId()
  {
    return _idGenerator.get();
  }
  
  WebApp start()
  {
    _classLoader.start();
    
    return this;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + id() + "]";
  }
}
