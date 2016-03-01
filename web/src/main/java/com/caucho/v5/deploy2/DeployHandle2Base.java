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

package com.caucho.v5.deploy2;

import io.baratine.service.Result;

import java.util.Objects;
import java.util.logging.Logger;

import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.lifecycle.LifecycleListener;
import com.caucho.v5.lifecycle.LifecycleState;

/**
 * DeployHandle returns the currently deployed instance
 */
public class DeployHandle2Base<I extends DeployInstance2> 
  implements DeployHandle2<I>
{
  private static final Logger log
    = Logger.getLogger(DeployHandle2Base.class.getName());
  
  // private DeployController<I> _controller;
  
  private String _id;
  private DeployService2Sync<I> _service;
  
  private I _instance;
  
  public DeployHandle2Base(String id, DeployService2Sync<I> service)
  {
    Objects.requireNonNull(id);
    Objects.requireNonNull(service);
    
    _id = id;
    _service = service;
  }
  
  private DeployService2Sync<I> service()
  {
    return _service;
  }
  
  /**
   * Returns the controller's id, typically a tag value like
   * production/webapp/default/ROOT
   */
  @Override
  public String getId()
  {
    return _id;
  }
  
  /**
   * Returns the state name.
   */
  /*
  @Override
  public LifecycleState getState()
  {
    return getService().getState();
  }
  */

  /**
   * Returns the current instance.
   */
  @Override
  public I get()
  {
    I instance = _instance;
    
    if (instance == null || instance.isModified()) {
      _instance = instance = service().get();
    }
    
    return instance;
  }

  @Override
  public I request()
  {
    I instance = _instance;
    
    if (instance == null || instance.isModified()) {
      _instance = instance = service().request();
    }
    
    return instance;
  }

  /**
   * Check for modification updates, generally from an admin command when
   * using "manual" redeployment.
   */
  @Override
  public void update()
  {
    service().update(Result.ignore());
  }

  @Override
  public void factory(DeployFactory2<I> factory)
  {
    service().factory(factory);
    
  }

  /**
   * Check for modification updates, generally from an admin command when
   * using "manual" redeployment.
   */
  @Override
  public void alarm()
  {
    // service().alarm(Result.ignore());
  }

  /**
   * Returns the instance for a top-level request
   * @return the request object or null for none.
   */
  /*
  @Override
  public I request()
  {
    I instance = _instance;
    
    if (instance == null || instance.isModified()) {
      _instance = instance = getService().requestSafe();
    }
    
    return instance;
  }
  */
  
  @Override
  public void startOnInit()
  {
    service().startOnInit(Result.ignore());
  }
  
  @Override
  public void start()
  {
    service().start();
  }
  
  @Override
  public void shutdown(ShutdownModeAmp mode, Result<Boolean> result)
  {
    service().shutdown(mode, result);
  }
  
  /*
  @Override
  public void stopAndWait(ShutdownModeAmp mode)
  {
    getService().stop(mode);
  }
  */
  
  @Override
  public Throwable getConfigException()
  {
    throw new UnsupportedOperationException(getClass().getName());
    
    //return getService().getConfigException();
  }

  @Override
  public boolean isModified()
  {
    return false;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _id + "]";
  }
}
