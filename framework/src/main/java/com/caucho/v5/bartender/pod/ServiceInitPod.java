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

import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.bartender.BartenderSystem;
import com.caucho.v5.util.L10N;

import io.baratine.service.ServiceInitializer;
import io.baratine.service.Services.ServicesBuilder;

/**
 * Binding the pod service to Baratine's directory.
 */
public class ServiceInitPod implements ServiceInitializer
{
  private static final L10N L = new L10N(ServiceInitPod.class);
  private static final Logger log
    = Logger.getLogger(ServiceInitPod.class.getName());
  
  @Override
  public void init(ServicesBuilder builder)
  {
    BartenderSystem bartender = BartenderSystem.current();
    
    if (bartender == null) {
      log.finest(L.l("pod: and champ: are not available in this system."));
      return;
    }
    
    if (bartender.schemeBartenderPod() == null) {
      return;
    }
    
    ServicesAmp services = (ServicesAmp) builder.get();
    
    ServiceRefAmp podScheme = new SchemePod(bartender, services);
    
    /*
    if (services instanceof AmpManagerBartender) {
      podScheme = new SchemePodSystem(bartender, ampManager);
    }
    else {
      podScheme 
    }
    */
    
    podScheme.bind(podScheme.address());
    
    PodBartender pod = bartender.getLocalPod();
    
    ServiceRefAmp schemeBartender;
    
    schemeBartender = new SchemeBartenderPodProxy(bartender, services, pod);
    
    schemeBartender.bind(schemeBartender.address());
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
