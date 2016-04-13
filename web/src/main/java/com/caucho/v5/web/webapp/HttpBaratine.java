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
import java.util.logging.Logger;

import com.caucho.v5.deploy2.DeployHandle2;
import com.caucho.v5.http.container.HttpContainer;
import com.caucho.v5.http.container.HttpContainerBase;
import com.caucho.v5.http.container.HttpContainerBuilder;
import com.caucho.v5.http.dispatch.InvocationManager;
import com.caucho.v5.http.dispatch.InvocationManagerBuilder;
import com.caucho.v5.http.dispatch.InvocationRouter;
import com.caucho.v5.http.protocol.ConnectionHttp;
import com.caucho.v5.io.ClientDisconnectException;
import com.caucho.v5.io.Dependency;
import com.caucho.v5.network.port.ConnectionProtocol;

import io.baratine.web.HttpStatus;


public class HttpBaratine
  extends HttpContainerBase<InvocationBaratine>
  implements InvocationRouter<InvocationBaratine>
{
  private static final Logger log
    = Logger.getLogger(HttpBaratine.class.getName());
  
  private final DeployHandle2<WebApp> _webAppHandle;

  /**
   * Creates a new http container.
   */
  public HttpBaratine(HttpBaratineBuilder builder)
  {
    super(builder);
    
    //_host = builder.buildHost(this);
    
    _webAppHandle = builder.buildWebAppHandle(this);
    Objects.requireNonNull(_webAppHandle);
  }
  
  public static HttpBaratine current()
  {
    return (HttpBaratine) HttpContainer.current();
  }

  Logger logger()
  {
    return log;
  }
  
  @Override
  public boolean start()
  {
    if (! super.start()) {
      return false;
    }
    
    _webAppHandle.start();
    
    return true;
  }

  @Override
  protected InvocationManager<InvocationBaratine> 
  createInvocationManager(HttpContainerBuilder builder)
  {
    InvocationManagerBuilder<InvocationBaratine> invocationBuilder;
    invocationBuilder = new InvocationManagerBuilder<>();
    
    invocationBuilder.router(this);
    
    return invocationBuilder.build();
  }

  @Override
  public void sendRequestError(Throwable e, 
                               RequestBaratine requestFacade)
      throws ClientDisconnectException
  {
    e.printStackTrace();
  }

  @Override
  public ConnectionProtocol newRequest(ConnectionHttp connHttp)
  {
    return new RequestBaratineImpl(connHttp);
  }

  @Override
  public InvocationBaratine createInvocation()
  {
    return new InvocationBaratine();
  }

  @Override
  public InvocationBaratine routeInvocation(InvocationBaratine invocation)
  {
    WebApp webApp = _webAppHandle.request();
    
    if (webApp == null) {
      invocation.routes(new RouteBaratine[] { new RouteStatusCode(HttpStatus.GATEWAY_TIMEOUT) });
      invocation.setDependency(Dependency.alwaysModified());
      
      return invocation;
    }
    
    return webApp.routeInvocation(invocation);
  }
}
