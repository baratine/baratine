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

package com.caucho.v5.bartender.pod;

import java.util.Objects;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.manager.ServicesAmpImpl;
import com.caucho.v5.amp.service.MethodRefNull;
import com.caucho.v5.amp.service.ServiceRefBase;
import com.caucho.v5.amp.spi.MethodRefAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.bartender.link.SchemeBartenderBase;

/**
 * Entry to the scheme system.
 */
public class SchemeBartenderPodProxy extends ServiceRefBase
{
  private final BartenderSystem _bartender;
  private final ServicesAmp _manager;
  private final SchemeBartenderBase _schemeBartenderPod;
  private final PodBartender _podCaller;
  private final PodRef _podRef;
  
  public SchemeBartenderPodProxy(BartenderSystem bartender,
                                 ServicesAmp manager,
                                 PodBartender pod)
  {
    _bartender = bartender;
    _manager = manager;
    _podCaller = pod;

    if (pod != null) {
      _podRef = new PodRefImpl(pod, manager);
    }
    else {
      _podRef = null;
    }
    
    _schemeBartenderPod = bartender.schemeBartenderPod();
    
    Objects.requireNonNull(_schemeBartenderPod);
  }
  
  @Override
  public String address()
  {
    return "bartender-pod:";
  }
  
  @Override
  public ServicesAmp manager()
  {
    return _manager;
  }
  
  @Override
  public ServiceRefAmp onLookup(String path)
  {
    ServiceRefAmp serviceRef
      = (ServiceRefAmp) _schemeBartenderPod.onLookup(path, _podRef);
    
    return serviceRef;
  }

  @Override
  public ServiceRefAmp bind(String address)
  {
    address = ServicesAmpImpl.toCanonical(address);

    manager().bind(this, address);

    return this;
  }

  @Override
  public MethodRefAmp getMethod(String methodName)
  {
    return new MethodRefNull(this, methodName);
  }
  
  @Override
  public void shutdown(ShutdownModeAmp mode)
  {
  }
}
