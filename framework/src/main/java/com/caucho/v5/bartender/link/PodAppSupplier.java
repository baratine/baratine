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

package com.caucho.v5.bartender.link;

import io.baratine.service.ServiceExceptionIllegalState;
import io.baratine.service.ServiceExceptionUnavailable;

import java.util.Objects;
import java.util.function.Supplier;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.bartender.ServerBartender;
import com.caucho.v5.http.pod.PodApp;
import com.caucho.v5.http.pod.PodAppHandle;
import com.caucho.v5.http.pod.PodContainer;
import com.caucho.v5.util.L10N;

/**
 * Supplier of ServiceManagerAmp for a named pod.
 */
public class PodAppSupplier implements Supplier<ServicesAmp>
{
  private static final L10N L = new L10N(PodAppSupplier.class);
  
  private final PodContainer _podContainer;
  private final String _podNodeName;

  private ServicesAmp _podManager;

  private ServerBartender _serverSelf;

  private PodAppHandle _handle;

  public PodAppSupplier(PodContainer podContainer,
                        String podNodeName)
  {
    Objects.requireNonNull(podContainer);
    
    _podContainer = podContainer;
    _podNodeName = podNodeName;
    
    _serverSelf = _podContainer.getSelfServer();
    
    Objects.requireNonNull(_serverSelf);
    
    if (podNodeName.isEmpty()) {
      throw new IllegalArgumentException();
    }
    
    _handle = _podContainer.getPodAppHandle("pods/" + _podNodeName);
  }

  @Override
  public ServicesAmp get()
  {
    ServicesAmp podManager = _podManager;

    if (podManager != null && ! podManager.isClosed()) {
      return podManager;
    }

    /*
    podManager = _handle.request(); // .getPodApp();
      //podApp = controller.getDeployInstance(); // .getPodApp();
    
    if (podManager != null
        && podManager.waitForActive()
        && podManager.getConfigException() == null) {
      _podManager = podManager;

      return podManager;
    }
    */
    
    podManager = _handle.requestManager(); // .getPodApp();
    
    if (podManager != null && ! podManager.isClosed()) {
      _podManager = podManager;
      
      return podManager;
    }
    
    /*
    if (podManager != null && podManager.getConfigException() != null) {
      throw new ServiceExceptionIllegalState(L.l("pod-app {0} failed to load {1} because of\n {2}",
                                                 podManager, _podNodeName, podManager.getConfigException()),
                                                 podManager.getConfigException());
    }
    else
    */ 
    if (podManager != null) {
      throw new ServiceExceptionIllegalState(L.l("pod-app {0} failed to load {1}",
                                                 podManager, _podNodeName));
    }
    else if (_handle.getConfigException() != null) {
      throw new ServiceExceptionIllegalState(L.l("pod-app failed to load {0} because of\n {1}",
                                                 _podNodeName, _handle.getConfigException()),
                                                 _handle.getConfigException());
    }
    else {
      ServerBartender server = _serverSelf;

      String serverId = server.getDisplayName() + "[" + server.getId() + "]";
      
      throw new ServiceExceptionUnavailable(L.l("Can't find active pod-app for {0} on server {1}.  {2} {3}\n"
          + "Check log for any errors in the deployment.",
          _podNodeName, serverId, _handle, _handle.getState()));
    }
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _podNodeName + "]";
  }
}

