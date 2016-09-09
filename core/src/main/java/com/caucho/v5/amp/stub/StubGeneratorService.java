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

package com.caucho.v5.amp.stub;

import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.function.Supplier;

import io.baratine.inject.Key;
import io.baratine.inject.Priority;

import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.service.ServiceConfig;
import com.caucho.v5.amp.service.StubFactoryAmp;
import com.caucho.v5.inject.impl.ServiceImpl;
import com.caucho.v5.util.L10N;

/**
 * Creates an stub factory for a service class.
 */
@Priority(-1000)
public class StubGeneratorService implements StubGenerator
{
  private static final L10N L = new L10N(StubGeneratorService.class);

  @Override
  public <T> StubFactoryAmp factory(Class<T> serviceClass,
                                 ServicesAmp ampManager,
                                 Supplier<? extends T> supplier,
                                 ServiceConfig config)
  {
    if (Modifier.isAbstract(serviceClass.getModifiers())) {
      return null;
      /*
      throw new IllegalArgumentException(L.l("'{0}' is an invalid service because it's abstract",
                                             serviceClass.getSimpleName()));
                                             */
    }

    return createFactory(ampManager, serviceClass, supplier, config);
  }

  private <T> StubFactoryAmp createFactory(ServicesAmp ampManager,
                                            Class<T> serviceClass,
                                            Supplier<? extends T> supplier,
                                            ServiceConfig config)
  {
    if (supplier != null) {
      return new StubFactoryImpl(()->createStub(ampManager, supplier, config),
                                 config);

    }
    // XXX: clean up
    Key<T> key = Key.of(serviceClass, ServiceImpl.class);

    return new StubFactoryImpl(()->createStub(ampManager, key, config),
                               config);
  }

  private static <T> StubAmp createStub(ServicesAmp ampManager,
                                         Key<T> key,
                                         ServiceConfig config)
  {
    T bean = ampManager.injector().instance(key);

    Objects.requireNonNull(bean);

    return ampManager.stubFactory().stub(bean, config);
  }

  private static <T> StubAmp createStub(ServicesAmp ampManager,
                                         Supplier<? extends T> supplier,
                                         ServiceConfig config)
  {
    T bean = supplier.get();

    Objects.requireNonNull(bean);

    return ampManager.stubFactory().stub(bean, config);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
