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

import javax.inject.Inject;

import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.io.Vfs;
import com.caucho.v5.loader.EnvironmentClassLoader;
import com.caucho.v5.subsystem.RootDirectorySystem;
import com.caucho.v5.subsystem.SystemManager;
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

public class RunnerBaratine extends BlockJUnit4ClassRunner
{
  private static final Logger log
    = Logger.getLogger(RunnerBaratine.class.getName());

  private static final L10N L = new L10N(RunnerBaratine.class);

  public RunnerBaratine(Class<?> cl) throws InitializationError
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

  private ConfigurationBaratine[] getConfiguration()
  {
    TestClass test = getTestClass();

    ConfigurationsBaratine config
      = test.getAnnotation(ConfigurationsBaratine.class);

    if (config != null)
      return config.value();

    Annotation[] annotations = test.getAnnotations();

    List<ConfigurationBaratine> list = new ArrayList<>();

    for (Annotation annotation : annotations) {
      if (ConfigurationBaratine.class.isAssignableFrom(annotation.getClass()))
        list.add((ConfigurationBaratine) annotation);
    }

    return list.toArray(new ConfigurationBaratine[list.size()]);
  }

  private void initialize(Object test) throws IllegalAccessException
  {
    TestClass testClass = getTestClass();

    Services manager = Services.newManager().start();

    Map<ServiceDescriptor,ServiceRef> descriptors = deployServices(manager);

    bindFields(test, testClass, manager, descriptors);
  }

  private void bindFields(Object test,
                          TestClass testClass,
                          Services manager,
                          Map<ServiceDescriptor,ServiceRef> descriptors)
    throws IllegalAccessException
  {
    List<FrameworkField> fields = testClass.getAnnotatedFields();

    for (FrameworkField field : fields) {
      Object inject;

      if (field.getAnnotation(Service.class) != null) {
        inject = findService(descriptors, field);
      }
      else if (field.getAnnotation(Inject.class) != null) {
        inject = findInject(manager, field);
      }
      else {
        continue;
      }

      Field javaField = field.getField();

      javaField.setAccessible(true);

      javaField.set(test, inject);
    }
  }

  private Map<ServiceDescriptor,ServiceRef> deployServices(
    Services manager)
  {
    Map<ServiceDescriptor,ServiceRef> descriptors = new HashMap<>();

    for (ConfigurationBaratine config : getConfiguration()) {
      Class[] services = config.services();

      for (Class service : services) {
        ServiceDescriptor descriptor = ServiceDescriptor.of(service);

        ServiceRef ref = manager.newService(service).addressAuto().ref();

        descriptors.put(descriptor, ref);
      }
    }
    return descriptors;
  }

  public Object findService(Map<ServiceDescriptor,ServiceRef> map,
                            FrameworkField field)
  {
    final Service binding = field.getAnnotation(Service.class);

    final Class type = field.getType();

    ServiceRef service = null;

    service = matchServiceByImpl(map, type, service);

    if (service == null)
      service = matchServiceByAddress(map, binding, service);

    if (service == null)
      service = matchServiceByApi(map, type, service);

    if (service == null)
      throw new IllegalStateException(L.l(
        "unable to bind field {0}, make sure corresponding service is deployed.",
        field.getField()));

    if (ServiceRef.class == type)
      return service;
    else
      return service.as(type);
  }

  private ServiceRef matchServiceByApi(Map<ServiceDescriptor,ServiceRef> map,
                                       Class type, ServiceRef service)
  {
    for (Map.Entry<ServiceDescriptor,ServiceRef> entry : map.entrySet()) {
      ServiceDescriptor descriptor = entry.getKey();
      Class api = descriptor.getApi();

      if (api != null && type.isAssignableFrom(descriptor.getApi())) {
        service = entry.getValue();

        break;
      }
    }
    return service;
  }

  private ServiceRef matchServiceByAddress(Map<ServiceDescriptor,ServiceRef> map,
                                           Service binding, ServiceRef service)
  {
    for (Map.Entry<ServiceDescriptor,ServiceRef> entry : map.entrySet()) {
      ServiceDescriptor descriptor = entry.getKey();

      if (descriptor.getAddress().equals(binding.value())) {
        service = entry.getValue();

        break;
      }
    }
    return service;
  }

  private ServiceRef matchServiceByImpl(Map<ServiceDescriptor,ServiceRef> map,
                                        Class type, ServiceRef service)
  {
    for (Map.Entry<ServiceDescriptor,ServiceRef> entry : map.entrySet()) {
      ServiceDescriptor descriptor = entry.getKey();

      if (descriptor.getServiceClass().equals(type)) {
        service = entry.getValue();

        break;
      }
    }

    return service;
  }

  private Object findInject(Services manager, FrameworkField field)
  {
    final Class type = field.getType();

    Object inject = null;

    if (type == Services.class) {
      inject = manager;
    }

    if (inject == null)
      throw new IllegalStateException(L.l("unable to bind field {0}",
                                          field.getField()));

    return inject;
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

    EnvironmentClassLoader envLoader
      = EnvironmentClassLoader.create(oldLoader, "test-loader");

    String baratineRoot = getWorkDir();
    System.setProperty("baratine.root", baratineRoot);

    RootDirectorySystem rootDir = null;

    SystemManager manager = null;

    try {
      thread.setContextClassLoader(envLoader);
/*
      TestAlarm.setTime(START_TIME);
      RandomUtil.setTestSeed(START_TIME);
      TestState.clear();
*/

      Logger.getLogger("").setLevel(Level.INFO);
      Logger.getLogger("javax.management").setLevel(Level.INFO);

      try {
        VfsOld.lookup(baratineRoot).removeAll();
      } catch (Exception e) {
      }

      manager = new SystemManager("test-manager");

      rootDir = RootDirectorySystem.createAndAddSystem(Vfs.path(baratineRoot));

      rootDir.start();

      super.runChild(method, notifier);
    } catch (Throwable t) {
      t.printStackTrace();
    } finally {
      Logger.getLogger("").setLevel(Level.INFO);

      try {
        rootDir.stop(ShutdownModeAmp.GRACEFUL);
      } catch (Throwable t) {
        t.printStackTrace();
      }

      try {
        envLoader.close();
      } catch (Throwable t) {
        t.printStackTrace();
      }

      try {
        manager.close();
      } catch (Throwable t) {
        t.printStackTrace();
      }

      thread.setContextClassLoader(oldLoader);
    }
  }

  private String getWorkDir()
  {
    final ConfigurationBaratine config = getConfiguration()[0];

    String workDir = config.workDir();

    if (workDir.charAt(0) == '{') {
      workDir = eval(workDir);
    }

    return workDir;
  }

  private String eval(String expr)
  {
    if (expr.charAt(0) != '{' || expr.charAt(expr.length() - 1) != '}')
      throw new IllegalArgumentException(L.l(
        "property {0} does not match expected format of {property}",
        expr));

    return System.getProperty(expr.substring(1, expr.length() - 1));
  }

  public void addTime(int i, TimeUnit unit)
  {
  }
}
