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

package com.caucho.v5.web.webapp;

import java.io.Serializable;
import java.util.logging.Logger;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.spi.ServiceManagerBuilderAmp;
import com.caucho.v5.amp.vault.ActorGeneratorVault;
import com.caucho.v5.amp.vault.VaultDriver;
import com.caucho.v5.data.VaultDriverDataImpl;
import com.caucho.v5.http.websocket.WebSocketManager;

import io.baratine.service.Asset;

/**
 * Baratine's web-app instance builder
 */
public class WebAppBuilderFramework extends WebAppBuilder
{
  private static final Logger log
    = Logger.getLogger(WebAppBuilderFramework.class.getName());
  
  /**
   * Creates the host with its environment loader.
   */
  public WebAppBuilderFramework(WebAppFactory factory)
  {
    super(factory);
  }

  @Override
  BodyResolver bodyResolver()
  {
    return new BodyResolverFramework();
  }

  @Override
  public WebSocketManager webSocketManager()
  {
    return new WebSocketManagerFramework();
  }

  /**
   * Custom service handling for {@code Resource} services.
   */
  @Override
  protected void addActorResources(ServiceManagerBuilderAmp builder)
  {
    try {
      ActorGeneratorVault gen = new ActorGeneratorVault();
      
      gen.driver(new ResourceDriverWebApp());
      
      builder.actorGenerator(gen);
    } catch (Exception e) {
      log.finer(e.toString());
    }
  }
  
  private class ResourceDriverWebApp
    implements VaultDriver<Object,Serializable>
  {
    @Override
    public <T,ID extends Serializable> VaultDriver<T,ID>
    driver(ServiceManagerAmp ampManager,
           Class<?> serviceType,
           Class<T> entityType, 
           Class<ID> idType,
           String address)
    {
      if (entityType.isAnnotationPresent(Asset.class)) {
        return new VaultDriverDataImpl(ampManager, entityType, idType, address);
      }
      else {
        return null;
      }
    }
  }
}
