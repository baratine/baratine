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

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ensure.EnsureDriverImpl;
import com.caucho.v5.amp.journal.JournalDriverImpl;
import com.caucho.v5.amp.spi.ServiceManagerBuilderAmp;
import com.caucho.v5.amp.vault.StubGeneratorVault;
import com.caucho.v5.amp.vault.StubGeneratorVaultDriver;
import com.caucho.v5.amp.vault.VaultDriver;
import com.caucho.v5.http.websocket.WebSocketManager;
import com.caucho.v5.json.JsonEngine;
import com.caucho.v5.json.JsonEngineDefault;
import com.caucho.v5.ramp.vault.VaultDriverDataImpl;
import com.caucho.v5.web.view.ViewJsonDefault;
import io.baratine.vault.Asset;
import io.baratine.web.ViewResolver;

import java.io.Serializable;
import java.util.logging.Logger;

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
  protected void init()
  {
    super.init();
  }

  @Override
  protected void initSelf()
  {
    super.initSelf();

    injectBuilder().bean(JsonEngineDefault.class).to(JsonEngine.class).priority(-1000);
    injectBuilder().bean(ViewJsonDefault.class).to(ViewResolver.class).priority(-1000);

    //injectBuilder().include(JsonEngineProviderDefault.class);
    //injectBuilder().include(ViewJsonDefault.class, Object.class, -1000);
  }

  @Override
  BodyResolver bodyResolver()
  {
    return injector().instance(BodyResolverFramework.class);
  }

  @Override
  public WebSocketManager webSocketManager()
  {
    return new WebSocketManagerFramework();
  }

  @Override
  protected void addFactories(ServiceManagerBuilderAmp builder)
  {
    builder.journalDriver(new JournalDriverImpl());
    builder.ensureDriver(new EnsureDriverImpl());
  }

  /**
   * Custom stub generator for {@code Vault} services.
   */
  @Override
  protected void addStubVault(ServiceManagerBuilderAmp builder)
  {
    try {
      StubGeneratorVault gen = new StubGeneratorVaultDriver();

      gen.driver(new ResourceDriverWebApp());

      builder.stubGenerator(gen);
    } catch (Exception e) {
      log.finer(e.toString());
    }
  }

  private class ResourceDriverWebApp
    implements VaultDriver<Object,Serializable>
  {
    @Override
    public <T,ID extends Serializable> VaultDriver<T,ID>
    driver(ServicesAmp ampManager,
           Class<?> serviceType,
           Class<T> entityType,
           Class<ID> idType,
           String address)
    {
      Asset asset;

      Class<?> t = entityType;

      do {
        asset = t.getAnnotation(Asset.class);

        Class<?>[] interfaces = t.getInterfaces();

        for (int i = 0; asset == null && i < interfaces.length; i++) {
          Class<?> face = interfaces[i];
          asset = face.getAnnotation(Asset.class);
        }

        t = t.getSuperclass();
      } while (t != Object.class && asset == null);

      if (asset != null) {
        return new VaultDriverDataImpl(ampManager, entityType, idType, address);
      }
      else {
        return null;
      }
    }
  }
}
