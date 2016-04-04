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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.loader.EnvironmentClassLoader;
import com.caucho.v5.util.L10N;
import com.caucho.v5.vfs.VfsOld;
import io.baratine.service.Api;
import io.baratine.service.Service;
import io.baratine.service.ServiceRef;
import io.baratine.service.Services;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;

public class RunnerBaratine2 extends BlockJUnit4ClassRunner
{
  private static final Logger log
    = Logger.getLogger(RunnerBaratine2.class.getName());

  private static final L10N L = new L10N(RunnerBaratine2.class);

  public RunnerBaratine2(Class<?> cl) throws InitializationError
  {
    super(cl);
  }

  @Override
  protected Object createTest() throws Exception
  {
    Object test = super.createTest();

    initialize(test);

    return test;
  }

  private ConfigurationBaratine2[] getConfiguration()
  {
    TestClass test = getTestClass();

    ConfigurationsBaratine2 config
      = test.getAnnotation(ConfigurationsBaratine2.class);

    if (config != null)
      return config.value();

    Annotation[] annotations = test.getAnnotations();

    List<ConfigurationBaratine2> list = new ArrayList<>();

    for (Annotation annotation : annotations) {
      if (ConfigurationBaratine2.class.isAssignableFrom(annotation.getClass()))
        list.add((ConfigurationBaratine2) annotation);
    }

    return list.toArray(new ConfigurationBaratine2[list.size()]);
  }

  private void initialize(Object test) throws IllegalAccessException
  {
    TestClass testClass = getTestClass();

    Services manager = Services.newManager().start();

    Map<ServiceDescriptor,ServiceRef> descriptors = new HashMap<>();

    for (ConfigurationBaratine2 config : getConfiguration()) {
      Class[] services = config.services();

      for (Class service : services) {
        ServiceDescriptor descriptor = ServiceDescriptor.of(service);

        ServiceRef ref = manager.newService(service).addressAuto().ref();

        descriptors.put(descriptor, ref);
      }
    }

    List<FrameworkField> fields
      = testClass.getAnnotatedFields(Service.class);

    for (FrameworkField field : fields) {
      Object service = findService(descriptors, field);

      Field javaField = field.getField();

      javaField.setAccessible(true);

      javaField.set(test, service);
    }
  }

  public Object findService(Map<ServiceDescriptor,ServiceRef> map,
                            FrameworkField field)
  {
    Service service = field.getAnnotation(Service.class);

    Class type = field.getType();

    Object result = null;

    for (Map.Entry<ServiceDescriptor,ServiceRef> entry : map.entrySet()) {
      ServiceDescriptor descriptor = entry.getKey();

      if (descriptor.getServiceClass().equals(type)) {
        result = entry.getValue().as(type);
        break;
      }
    }

    if (result == null) {
      for (Map.Entry<ServiceDescriptor,ServiceRef> entry : map.entrySet()) {
        ServiceDescriptor descriptor = entry.getKey();

        if (descriptor.getAddress().equals(service.value())) {
          result = entry.getValue().as(type);

          break;
        }
      }
    }

    if (result == null) {
      for (Map.Entry<ServiceDescriptor,ServiceRef> entry : map.entrySet()) {
        ServiceDescriptor descriptor = entry.getKey();

        if (type.isAssignableFrom(descriptor.getApi())) {
          result = entry.getValue().as(type);

          break;
        }
      }
    }

    return result;
  }

  static class ServiceDescriptor
  {
    private Class<?> _api;
    private Class<?> _serviceClass;
    private Service _service;

    private ServiceDescriptor(final Class serviceClass)
    {
      Objects.requireNonNull(serviceClass);

      _serviceClass = serviceClass;
      _service = (Service) serviceClass.getAnnotation(Service.class);

      Objects.requireNonNull(_service,
                             L.l("{0} must declare @Service annotation",
                                 _serviceClass));

      Api api;

      Class t = serviceClass;

      do {
        api = (Api) t.getAnnotation(Api.class);
      } while (api == null
               && (t = serviceClass.getSuperclass()) != Object.class);

      if (api != null)
        _api = api.value();
    }

    public String getAddress()
    {
      final String address;

      if (_service.value() != null) {
        address = _service.value();
      }
      else {
        String name = _serviceClass.getSimpleName();
        if (name.endsWith("Impl"))
          name = name.substring(0, name.length() - 4);

        address = '/' + name;
      }

      return address;
    }

    public static ServiceDescriptor of(Class t)
    {
      return new ServiceDescriptor(t);
    }

    @Override
    public boolean equals(Object o)
    {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ServiceDescriptor that = (ServiceDescriptor) o;

      return _serviceClass.equals(that._serviceClass);

    }

    @Override
    public int hashCode()
    {
      return _serviceClass.hashCode();
    }

    public Class<?> getServiceClass()
    {
      return _serviceClass;
    }

    public Class<?> getApi()
    {
      return _api;
    }
  }

  @Override
  protected void runChild(FrameworkMethod method, RunNotifier notifier)
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    EnvironmentClassLoader envLoader = EnvironmentClassLoader.create(oldLoader,
                                                                     "test-loader");

    try {
      thread.setContextClassLoader(envLoader);
/*
      TestAlarm.setTime(START_TIME);
      RandomUtil.setTestSeed(START_TIME);
      TestState.clear();
*/

      Logger.getLogger("").setLevel(Level.INFO);
      Logger.getLogger("javax.management").setLevel(Level.INFO);

      String user = System.getProperty("user.name");
      String baratineRoot = "/tmp/" + user + "/qa";
      System.setProperty("baratine.root", baratineRoot);

      try {
        VfsOld.lookup(baratineRoot).removeAll();
      } catch (Exception e) {
      }

      super.runChild(method, notifier);
    } finally {
      Logger.getLogger("").setLevel(Level.INFO);

      try {
        envLoader.close();
      } catch (Throwable e) {
      }

      thread.setContextClassLoader(oldLoader);
    }
  }

  public void addTime(int i, TimeUnit unit)
  {
  }
}
