/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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

package com.caucho.v5.subsystem;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.lifecycle.Lifecycle;
import com.caucho.v5.util.L10N;


/**
 * Interface for a service registered with the Server.
 */
public class SubSystemBase implements SubSystem
{
  private static final L10N L = new L10N(SubSystemBase.class);
  private static final Logger log
    = Logger.getLogger(SubSystemBase.class.getName());
  
  private Lifecycle _lifecycle;
  
  protected SubSystemBase()
  {
  }

  protected Level getLifecycleLogLevel()
  {
    return Level.FINEST;
  }
  
  @Override
  public int getStartPriority()
  {
    return START_PRIORITY_DEFAULT;
  }
  
  public boolean isActive()
  {
    Lifecycle lifecycle = _lifecycle;
    
    return lifecycle != null && lifecycle.isActive();
  }

  @Override
  public void start()
    throws Exception
  {
    if (_lifecycle == null) {
      _lifecycle = new Lifecycle(log, toString(), getLifecycleLogLevel());
    }
    
    _lifecycle.toActive();
  }

  @Override
  public int getStopPriority()
  {
    return getStartPriority();
  }

  @Override
  public void stop(ShutdownModeAmp mode) 
    throws Exception
  {
    Lifecycle lifecycle = _lifecycle;
    
    if (lifecycle != null) {
      lifecycle.toStop();
    }
  }

  @Override
  public void destroy()
  {
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }

  // convenience method for subclass's create methods
  protected static <E extends SubSystemBase> SystemManager
    preCreate(Class<E> serviceClass)
  {
    SystemManager system = SystemManager.getCurrent();
    if (system == null)
      throw new IllegalStateException(L.l("{0} must be created before {1}",
                                          SystemManager.class.getSimpleName(),
                                          serviceClass.getSimpleName()));
    
    if (system.getSystem(serviceClass) != null)
      throw new IllegalStateException(L.l("{0} was previously created",
                                          serviceClass.getSimpleName()));
    
    return system;
  }
}
