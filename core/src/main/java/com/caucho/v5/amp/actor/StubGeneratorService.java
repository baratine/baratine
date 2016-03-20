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

import java.lang.reflect.Modifier;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.service.ActorFactoryAmp;
import com.caucho.v5.amp.service.ServiceConfig;
import com.caucho.v5.amp.spi.ActorAmp;
import com.caucho.v5.config.Priority;

import io.baratine.inject.Bean;
import io.baratine.inject.Key;

/**
 * Creates an stub factory for a service class.
 */
@Priority(-1000)
public class StubGeneratorService implements StubGenerator
{
  @Override
  public ActorFactoryAmp factory(Class<?> serviceClass,
                                 ServiceManagerAmp ampManager,
                                 ServiceConfig config)
  {
    if (! Modifier.isAbstract(serviceClass.getModifiers())) {
      return createFactory(ampManager, serviceClass, config);
    }
    
    ClassGeneratorService<?> gen
      = new ClassGeneratorService<>(ampManager, serviceClass);
    
    Class<?> fullClass = gen.generate();
    
    return createFactory(ampManager, fullClass, config);
  }
  
  private <T> ActorFactoryAmp createFactory(ServiceManagerAmp ampManager,
                                            Class<T> serviceClass,
                                            ServiceConfig config)
  {
    Key<T> key = Key.of(serviceClass, Bean.class);
    
    return new ActorFactoryImpl(()->createStub(ampManager, key, config),
                                config);
  }
  
  private static <T> ActorAmp createStub(ServiceManagerAmp ampManager,
                                         Key<T> key,
                                         ServiceConfig config)
  {
    T bean = ampManager.inject().instance(key);
    
    return ampManager.createActor(bean, config);
  }
}
