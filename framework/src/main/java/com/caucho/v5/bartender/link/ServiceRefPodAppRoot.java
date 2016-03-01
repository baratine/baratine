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

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.actor.ServiceRefNull;
import com.caucho.v5.amp.actor.ServiceRefWrapper;
import com.caucho.v5.http.pod.PodApp;
import com.caucho.v5.http.pod.PodContainer;
import com.caucho.v5.util.L10N;

/**
 * Broker for hamp messages.
 */
public class ServiceRefPodAppRoot extends ServiceRefWrapper
{
  private static final L10N L = new L10N(ServiceRefPodAppRoot.class);
  
  private final ServiceManagerAmp _rampManager;
  private final PodContainer _podContainer;
  
  private final ConcurrentHashMap<String,Supplier<ServiceManagerAmp>> _podMap
    = new ConcurrentHashMap<>();
  
  public ServiceRefPodAppRoot(ServiceManagerAmp manager,
                              PodContainer podContainer)
  {
    Objects.requireNonNull(manager);
    Objects.requireNonNull(podContainer); 
    
    _rampManager = manager;
    _podContainer = podContainer;
  }
  
  @Override
  public ServiceManagerAmp manager()
  {
    return _rampManager;
  }

  @Override
  public String address()
  {
    return "pod://";
  }

  protected ServiceRefAmp lookup(ServiceManagerAmp manager, String path)
  {
    return manager.service("local://" + path);
  }
  
  @Override
  public ServiceRefAmp onLookup(String path)
  {
    int p = path.indexOf('/', 1);
    
    String podNodeName;
    String subPath;
    
    if (p > 0) {
      podNodeName = path.substring(1, p);
      subPath = path.substring(p);
    }
    else {
      podNodeName = path.substring(1);
      subPath = "";
    }
    
    return new ServiceRefPodApp(_rampManager,
                                getPodManagerSupplier(podNodeName),
                                podNodeName,
                                subPath);
  }
  
  private Supplier<ServiceManagerAmp> getPodManagerSupplier(String podNodeName)
  {
    Supplier<ServiceManagerAmp> supplier = _podMap.get(podNodeName);
    
    if (supplier == null) {
      supplier = new PodAppSupplier(_podContainer, podNodeName);
      
      if (_podMap.putIfAbsent(podNodeName, supplier) != null) {
        supplier = _podMap.get(podNodeName);
      }
    }
    
    return supplier;
  }

  @Override
  protected ServiceRefAmp delegate()
  {
    return new ServiceRefNull(manager(), address()); 
  }
}
