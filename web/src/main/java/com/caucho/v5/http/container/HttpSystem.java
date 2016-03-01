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

package com.caucho.v5.http.container;

import java.util.Objects;
import java.util.logging.Level;

import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.subsystem.SubSystemBase;
import com.caucho.v5.subsystem.SystemManager;

/**
 * The main servlet service in a Resin server. 
 */
public class HttpSystem extends SubSystemBase
{
  private HttpContainerBuilder _httpBuilder;
  private HttpContainer _httpContainer;
  
  public HttpSystem(HttpContainerBuilder httpBuilder)
  {
    Objects.requireNonNull(httpBuilder);
    
    _httpBuilder = httpBuilder;
  }
  
  public static HttpSystem createAndAddSystem(HttpContainerBuilder httpBuilder)
  {
    SystemManager system = preCreate(HttpSystem.class);
    
    HttpSystem service = new HttpSystem(httpBuilder);
    system.addSystem(HttpSystem.class, service);
    
    return service;
  }
  
  public static HttpSystem getCurrent()
  {
    return SystemManager.getCurrentSystem(HttpSystem.class);
  }

  @Override
  protected Level getLifecycleLogLevel()
  {
    return Level.FINE;
  }
  
  public HttpContainer getHttpContainer()
  {
    return _httpContainer;
  }
  
  /**
   * Starts the servlet engine.
   */
  @Override
  public void start()
  {
    _httpContainer = _httpBuilder.build();
    
    _httpContainer.start();
  }
  
  /**
   * Stops the servlet engine.
   */
  @Override
  public void stop(ShutdownModeAmp mode)
  {
    if (_httpContainer != null) {
      _httpContainer.shutdown(mode);
    }
  }
}
