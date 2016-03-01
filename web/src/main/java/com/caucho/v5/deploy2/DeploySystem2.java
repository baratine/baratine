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

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.caucho.v5.amp.AmpSystem;
import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.subsystem.SubSystemBase;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.util.L10N;


/**
 * Baratine master system registered in the Resin network.
 */
public class DeploySystem2 extends SubSystemBase
{
  private static final Logger log = Logger.getLogger(DeploySystem2.class.getName());
  private static final L10N L = new L10N(DeploySystem2.class);
  
  // priority must be before network so it's available to handle incoming
  // messages
  public static final int START_PRIORITY = START_PRIORITY_ENV_SYSTEM;
  
  private ServiceManagerAmp _ampManager;

  private final ConcurrentHashMap<String,DeployHandle2<?>> _handleMap
    = new ConcurrentHashMap<>();

  public DeploySystem2(SystemManager systemManager)
  {
    Objects.requireNonNull(systemManager);
    
    _ampManager = AmpSystem.currentManager();
    Objects.requireNonNull(_ampManager);
  }
  
  public static DeploySystem2 createAndAddSystem()
  {
    SystemManager system = preCreate(DeploySystem2.class);
      
    DeploySystem2 subSystem = new DeploySystem2(system);
    system.addSystem(subSystem);
    
    return subSystem;
  }
  
  public static DeploySystem2 current()
  {
    return SystemManager.getCurrentSystem(DeploySystem2.class);
  }
  
  public <I extends DeployInstance2> DeployHandle2<I> 
    createHandle(String id, Logger log)
  {
    DeployHandle2<I> handle = (DeployHandle2<I>) _handleMap.get(id);
    
    if (handle == null) {
      ServiceManagerAmp ampManager = _ampManager;
      Objects.requireNonNull(ampManager);
      
      DeployService2Impl<I> serviceImpl
        = new DeployService2Impl<>(id, log);
      
      DeployService2Sync<I> service
        = ampManager.newService(serviceImpl).as(DeployService2Sync.class);
      
      handle = new DeployHandle2Base<>(id, service);
      
      _handleMap.putIfAbsent(id, handle);
      
      handle = (DeployHandle2<I>) _handleMap.get(id);
    }
    
    return handle;
  }
  
  public <I extends DeployInstance2> DeployHandle2<I> 
    getHandle(String id)
  {
    return (DeployHandle2<I>) _handleMap.get(id);
  }

  @Override
  public int getStartPriority()
  {
    return START_PRIORITY;
  }
}

