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

package com.caucho.v5.amp.session;

import java.util.function.Supplier;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.service.ServiceConfig;
import com.caucho.v5.amp.service.StubFactoryAmp;
import com.caucho.v5.amp.stub.StubAmpBean;
import com.caucho.v5.amp.stub.StubClass;
import com.caucho.v5.amp.stub.StubFactoryImpl;
import com.caucho.v5.amp.stub.StubGenerator;
import com.caucho.v5.config.Priority;

/**
 * Creates an stub supplier for sessions
 */
@Priority(-10)
public class StubGeneratorSession implements StubGenerator
{
  @Override
  public <T> StubFactoryAmp factory(Class<T> serviceClass,
                                 ServicesAmp services,
                                 Supplier<? extends T> supplier,
                                 ServiceConfig configService)
  {
    String address = configService.address();

    if (address != null && address.startsWith("session:")) {
      return factorySession(serviceClass, services, configService);
    }
    
    return null;
  }

  private <T> StubFactoryAmp factorySession(Class<T> sessionClass,
                                            ServicesAmp services,
                                            ServiceConfig configService)
  {
    StubClass stubClassVault;

    stubClassVault = new StubClassSessionVault<>(services,
                                                 sessionClass);

    stubClassVault.introspect();
    
    StubAmpBean stubVault = new StubAmpBean(stubClassVault, 
                                            new SessionVaultImpl(sessionClass),
                                            configService.name(),
                                            null,
                                            configService);

    return new StubFactoryImpl(()->stubVault, configService);
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
