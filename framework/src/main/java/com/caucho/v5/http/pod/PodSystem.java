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

package com.caucho.v5.http.pod;

import java.util.Objects;
import java.util.logging.Level;

import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.http.container.HttpContainerBuilder;
import com.caucho.v5.subsystem.SubSystemBase;
import com.caucho.v5.subsystem.SystemManager;

/**
 * The main servlet service in a Resin server. 
 */
public class PodSystem extends SubSystemBase
{
  private HttpContainerBuilder _podBuilder;
  private PodContainer _podContainer;
  
  public PodSystem(HttpContainerBuilder podBuilder)
  {
    Objects.requireNonNull(podBuilder);
    
    _podBuilder= podBuilder;
  }
  
  public static PodSystem createAndAddSystem(HttpContainerBuilder podBuilder)
  {
    SystemManager system = preCreate(PodSystem.class);
    
    PodSystem service = new PodSystem(podBuilder);
    system.addSystem(PodSystem.class, service);
    
    return service;
  }
  
  public static PodSystem getCurrent()
  {
    return SystemManager.getCurrentSystem(PodSystem.class);
  }

  @Override
  protected Level getLifecycleLogLevel()
  {
    return Level.FINE;
  }
  
  public PodContainer getPodContainer()
  {
    return _podContainer;
  }
  
  /**
   * Starts the pod engine.
   */
  @Override
  public void start()
  {
    if (true) throw new UnsupportedOperationException();
    //_podContainer = _podBuilder.buildPod();
    
    //_podContainer.start();
  }
  
  /**
   * Stops the servlet engine.
   */
  @Override
  public void stop(ShutdownModeAmp mode)
  {
    if (_podContainer != null) {
      _podContainer.stop(mode);
    }
  }
}
