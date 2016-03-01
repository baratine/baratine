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

package com.caucho.v5.amp.actor;

import java.util.Objects;
import java.util.function.Supplier;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.manager.ServiceConfig;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.amp.spi.ActorFactoryAmp;

/**
 * Basic method for creating actors.
 */
public class ActorFactoryWorkers implements ActorFactoryAmp
{
  private final ActorAmp _actorMain;
  private final ServiceConfig _config;
  private ServiceManagerAmp _ampManager;
  private Supplier<?> _beanSupplier;
  private ActorAmp _actorNext;

  public ActorFactoryWorkers(ServiceManagerAmp ampManager,
                             Supplier<?> beanSupplier,
                             ServiceConfig config)
  {
    _ampManager = ampManager;
    _beanSupplier = beanSupplier;
    _config = config;
    
    _actorMain = get();
    
    _actorNext = _actorMain; 
  }
  
  @Override
  public String actorName()
  {
    return _actorMain.toString();
  }
  
  @Override
  public ServiceConfig config()
  {
    return _config;
  }

  @Override
  public ActorAmp get()
  {
    ActorAmp actor = _actorNext;
    _actorNext = null;
    
    if (actor == null)  {
      Object bean = _beanSupplier.get();
      Objects.requireNonNull(bean);
      
      actor = _ampManager.createActor(bean, _config);
    }
    
    return actor;
  }
  
  @Override
  public ActorAmp mainActor()
  {
    return _actorMain;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _actorMain + "]";
  }
}
