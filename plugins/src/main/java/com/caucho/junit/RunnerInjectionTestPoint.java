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
 * @author Alex Rojkov
 */

package com.caucho.junit;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;

import io.baratine.service.Service;

class RunnerInjectionTestPoint extends InjectionTestPoint
{
  public RunnerInjectionTestPoint(Class<?> type,
                                  Annotation[] annotations,
                                  MethodHandle setter)
  {
    super(type, annotations, setter);
  }

  public boolean isMatch(RunnerBaratine.ServiceTestDescriptor descriptor)
  {
    boolean isMatch = isMatchType(descriptor) ||
                      isMatchName(descriptor) ||
                      isMatchInterface(descriptor) ||
                      isMatchSync(descriptor);

    return isMatch;
  }

  private boolean isMatchName(RunnerBaratine.ServiceTestDescriptor descriptor)
  {
    boolean isMatch = !address().isEmpty();

    isMatch &= !descriptor.getAddress().isEmpty();

    isMatch &= descriptor.getAddress().equals(address());

    return isMatch;
  }

  private boolean isMatchType(RunnerBaratine.ServiceTestDescriptor descriptor)
  {
    return descriptor.getApi().equals(getType());
  }

  private boolean isMatchInterface(RunnerBaratine.ServiceTestDescriptor descriptor)
  {
    return getType().isAssignableFrom(descriptor.getApi());
  }

  private boolean isMatchSync(RunnerBaratine.ServiceTestDescriptor descriptor)
  {
    Class[] interfaces = getType().getInterfaces();

    for (Class i : interfaces) {
      if (i.equals(descriptor.getApi()))
        return true;
    }

    return false;
  }

  public Service getService()
  {
    return getAnnotation(Service.class);
  }

  public String address()
  {
    Service service = getService();

    if (service == null)
      return null;

    String address = service.value();

    if (address == null) {
      String name = getType().getSimpleName();
      if (name.endsWith("Impl"))
        name = name.substring(0, name.length() - 4);

      address = "/" + name;
    }

    return address;
  }
}
