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
import java.util.ArrayList;
import java.util.List;

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;

public class BaseRunner extends BlockJUnit4ClassRunner
{
  public BaseRunner(Class<?> klass) throws InitializationError
  {
    super(klass);
  }

  final protected ConfigurationBaratine getConfiguration()
  {
    ConfigurationBaratine config
      = getTestClass().getAnnotation(ConfigurationBaratine.class);

    if (config == null)
      config = ConfigurationBaratineDefault.INSTNANCE;

    return config;
  }

  protected ServiceTest[] getServices()
  {
    TestClass test = getTestClass();

    ServiceTests config
      = test.getAnnotation(ServiceTests.class);

    if (config != null)
      return config.value();

    Annotation[] annotations = test.getAnnotations();

    List<ServiceTest> list = new ArrayList<>();

    for (Annotation annotation : annotations) {
      if (ServiceTest.class.isAssignableFrom(annotation.getClass()))
        list.add((ServiceTest) annotation);
    }

    return list.toArray(new ServiceTest[list.size()]);
  }
}
