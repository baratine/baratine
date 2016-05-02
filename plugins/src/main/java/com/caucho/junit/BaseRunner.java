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

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.caucho.v5.util.L10N;
import org.junit.Test;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;

abstract class BaseRunner extends BlockJUnit4ClassRunner
{
  private final static Logger log
    = Logger.getLogger(BaseRunner.class.getName());

  private final L10N L = new L10N(BaseRunner.class);

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

  @Override
  protected TestClass createTestClass(Class<?> testClass)
  {
    return new BaratineTestClass(testClass);
  }

  abstract protected Object resolve(Class type, Annotation[] annotations);

  class BaratineTestClass extends TestClass
  {
    public BaratineTestClass(Class<?> clazz)
    {
      super(clazz);
    }

    @Override
    protected void scanAnnotatedMembers(Map<Class<? extends Annotation>,List<FrameworkMethod>> methodsForAnnotations,
                                        Map<Class<? extends Annotation>,List<FrameworkField>> fieldsForAnnotations)
    {
      super.scanAnnotatedMembers(methodsForAnnotations, fieldsForAnnotations);

      for (Map.Entry<Class<? extends Annotation>,List<FrameworkMethod>> entry
        : methodsForAnnotations.entrySet()) {
        if (Test.class.equals(entry.getKey())) {
          List<FrameworkMethod> methods = new ArrayList<>();
          for (FrameworkMethod method : entry.getValue()) {
            Method javaMethod = method.getMethod();
            if (javaMethod.getParameterTypes().length > 0) {
              methods.add(new BaratineFrameworkMethod(javaMethod));
            }
            else {
              methods.add(method);
            }
          }

          entry.setValue(methods);
        }
      }
    }
  }

  class BaratineFrameworkMethod extends FrameworkMethod
  {
    public BaratineFrameworkMethod(Method method)
    {
      super(method);
    }

    @Override
    public void validatePublicVoidNoArg(boolean isStatic,
                                        List<Throwable> errors)
    {
      if (!Modifier.isPublic(getModifiers()))
        errors.add(new Exception(L.l("Method {0} must be public",
                                     getMethod())));

      if (!void.class.equals(getReturnType())) {
        errors.add(new Exception(L.l("Method {0} must return void",
                                     getMethod())));
      }
    }

    @Override
    public Object invokeExplosively(Object target, Object... params)
      throws Throwable
    {
      Object[] args = fillParams(params);

      return super.invokeExplosively(target, args);
    }

    public Object[] fillParams(Object... v)
      throws IllegalAccessException, InstantiationException, IOException
    {
      Method method = getMethod();
      Class<?>[] types = method.getParameterTypes();

      Annotation[][] parameterAnnotations = method.getParameterAnnotations();

      if (v.length == types.length)
        return v;

      Object[] args = new Object[types.length];

      int vStart = types.length - v.length;

      System.arraycopy(v, 0, args, vStart, v.length);

      for (int i = 0; i < vStart; i++) {
        Class type = types[i];
        Annotation[] annotations = parameterAnnotations[i];

        Object obj = resolve(type, annotations);

        args[i] = obj;
      }

      return args;
    }
  }

}
