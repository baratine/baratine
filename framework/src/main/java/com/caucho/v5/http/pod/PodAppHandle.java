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

import java.util.logging.Logger;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.deploy2.DeployHandle2;

/**
 * Handle to a pod-app.
 */
public class PodAppHandle
{
  private static final Logger log = Logger.getLogger(PodAppHandle.class.getName());
  
  private String _id;
  private DeployHandle2<? extends PodManagerApp> _deployHandle;
  private PodContainer _podContainer;
  
  PodAppHandle(String id, 
               DeployHandle2<? extends PodManagerApp> handle,
               PodContainer podContainer)
  {
    _id = id;
    _deployHandle = handle;
    _podContainer = podContainer;
  }
  
  public String getId()
  {
    return _id;
  }
  
  /*
  private DeployControllerService<? extends PodManagerApp> getService()
  {
    return _deployHandle.getService();
  }
  */

  DeployHandle2<? extends PodManagerApp> getHandle()
  {
    return _deployHandle;
  }

  public Throwable getConfigException()
  {
    return _deployHandle.getConfigException();
  }

  private PodManagerApp request()
  {
    PodManagerApp podApp = _deployHandle.request();
    
    if (podApp != null) {
      return podApp;
    }
    
    //_podContainer..getDeployService().updateNode(_id);
    
    podApp = _deployHandle.request();
    
    return podApp;
  }

  public PodManagerApp getDeployInstance()
  {
    return _deployHandle.get();
  }

  public ServicesAmp requestManager()
  {
    PodManagerApp podApp = request(); // .getPodApp();

  //podApp = controller.getDeployInstance(); // .getPodApp();

    if (podApp != null
        && podApp.waitForActive()
        && podApp.getConfigException() == null) {
      return podApp.getAmpManager();
    }

    return null;
  }

  public String getState()
  {
    return "";
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _id + "]";
  }
}
