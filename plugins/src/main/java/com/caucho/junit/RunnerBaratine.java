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

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import com.caucho.v5.amp.Amp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.manager.InjectAutoBindService;
import com.caucho.v5.amp.spi.ServiceManagerBuilderAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.vault.StubGeneratorVault;
import com.caucho.v5.amp.vault.StubGeneratorVaultDriver;
import com.caucho.v5.amp.vault.VaultDriver;
import com.caucho.v5.config.Configs;
import com.caucho.v5.config.inject.BaratineProducer;
import com.caucho.v5.inject.AnnotationLiteral;
import com.caucho.v5.inject.InjectorAmp;
import com.caucho.v5.io.Vfs;
import com.caucho.v5.loader.EnvironmentClassLoader;
import com.caucho.v5.ramp.vault.VaultDriverDataImpl;
import com.caucho.v5.subsystem.RootDirectorySystem;
import com.caucho.v5.subsystem.SystemManager;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.RandomUtil;
import com.caucho.v5.vfs.VfsOld;
import io.baratine.config.Config;
import io.baratine.service.Api;
import io.baratine.service.Service;
import io.baratine.service.ServiceRef;
import io.baratine.service.Services;
import io.baratine.vault.Asset;
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

  private ServiceTest[] getServices()
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

  private class ResourceDriver
    implements VaultDriver<Object,Serializable>
  {
    @Override
    public <T, ID extends Serializable> VaultDriver<T,ID>
    driver(ServicesAmp ampManager,
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

  private void initialize(Object test) throws IllegalAccessException
  {
    TestClass testClass = getTestClass();

    InjectorAmp injectorAmp = InjectorAmp.create();

    InjectorAmp.InjectBuilderAmp injectBuilder;

    ServiceManagerBuilderAmp serviceBuilder;

    ClassLoader cl = Thread.currentThread().getContextClassLoader();

    injectBuilder = InjectorAmp.manager(cl);

    injectBuilder.context(true);

    injectBuilder.include(BaratineProducer.class);

    Config.ConfigBuilder configBuilder = Configs.config();

    injectBuilder.bean(configBuilder.get()).to(Config.class);

    serviceBuilder = ServicesAmp.newManager();
    serviceBuilder.name("junit-test");
    serviceBuilder.autoServices(true);
    serviceBuilder.injectManager(() -> injectBuilder.get());

    StubGeneratorVault gen = new StubGeneratorVaultDriver();

    gen.driver(new ResourceDriver());

    serviceBuilder.stubGenerator(gen);

    serviceBuilder.contextManager(true);

    ServicesAmp serviceManager = serviceBuilder.get();

    Amp.contextManager(serviceManager);

    injectBuilder.autoBind(new InjectAutoBindService(serviceManager));

    injectBuilder.get();

    serviceBuilder.start();

    Map<ServiceDescriptor,ServiceRef> descriptors = deployServices(
      serviceManager);

    bindFields(test, testClass, serviceManager, descriptors);
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
        inject = findService(manager, descriptors, field);
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

    for (ServiceTest service : getServices()) {
      Class serviceClass = service.value();

      ServiceDescriptor descriptor = ServiceDescriptor.of(serviceClass);

      ServiceRef ref = manager.newService(serviceClass).addressAuto().ref();

      descriptors.put(descriptor, ref);
    }

    return descriptors;
  }

  public Object findService(Services manager,
                            Map<ServiceDescriptor,ServiceRef> map,
                            FrameworkField field)
  {
    final Service binding = field.getAnnotation(Service.class);

    final Class type = field.getType();

    ServiceRef service = null;

    service = matchServiceByImpl(map, type);

    if (service == null)
      service = matchServiceByAddress(map, binding);

    if (service == null)
      service = matchServiceByApi(map, type);

    if (service == null)
      service = matchDefaultService(manager, binding);

    if (service == null)
      throw new IllegalStateException(L.l(
        "unable to bind field {0}, make sure corresponding service is deployed.",
        field.getField()));

    if (ServiceRef.class == type)
      return service;
    else
      return service.as(type);
  }

  private ServiceRef matchDefaultService(Services manager,
                                         Service binding)
  {
    String address = binding.value();

    if (address == null)
      return null;

    return manager.service(address);
  }

  private ServiceRef matchServiceByApi(Map<ServiceDescriptor,ServiceRef> map,
                                       Class type)
  {
    for (Map.Entry<ServiceDescriptor,ServiceRef> entry : map.entrySet()) {
      ServiceDescriptor descriptor = entry.getKey();
      Class api = descriptor.getApi();

      if (api != null && type.isAssignableFrom(descriptor.getApi())) {
        return entry.getValue();
      }
    }

    return null;
  }

  private ServiceRef matchServiceByAddress(Map<ServiceDescriptor,ServiceRef> map,
                                           Service binding)
  {
    for (Map.Entry<ServiceDescriptor,ServiceRef> entry : map.entrySet()) {
      ServiceDescriptor descriptor = entry.getKey();

      if (descriptor.getAddress().equals(binding.value())) {
        return entry.getValue();
      }
    }

    return null;
  }

  private ServiceRef matchServiceByImpl(Map<ServiceDescriptor,ServiceRef> map,
                                        Class type)
  {
    for (Map.Entry<ServiceDescriptor,ServiceRef> entry : map.entrySet()) {
      ServiceDescriptor descriptor = entry.getKey();

      if (descriptor.getServiceClass().equals(type)) {
        return entry.getValue();
      }
    }

    return null;
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

      if (serviceClass.isInterface()) {
        _api = serviceClass;
      }
      else {
        _api = getApi(serviceClass);
      }
    }

    private Class getApi(Class serviceClass)
    {
      Api api;
      Class t = serviceClass;

      do {
        api = (Api) t.getAnnotation(Api.class);
      } while (api == null
               && (t = serviceClass.getSuperclass()) != Object.class);

      return api == null ? null : api.value();
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

      long startTime = getStartTime();

      if (startTime != -1) {
        TestTime.setTime(startTime);

        RandomUtil.setTestSeed(startTime);
      }

      Logger.getLogger("").setLevel(Level.INFO);
      Logger.getLogger("javax.management").setLevel(Level.INFO);

      try {
        VfsOld.lookup(baratineRoot).removeAll();
      } catch (Exception e) {
      }

      manager = new SystemManager("junit-test", envLoader);

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

  private ConfigurationBaratine getConfiguration()
  {
    ConfigurationBaratine config
      = getTestClass().getAnnotation(ConfigurationBaratine.class);

    if (config == null)
      config = ConfigurationBaratineDefault.INSTNANCE;

    return config;
  }

  private String getWorkDir()
  {
    final ConfigurationBaratine config = getConfiguration();

    String workDir = config.workDir();

    if (workDir.charAt(0) == '{') {
      workDir = eval(workDir);
    }

    return workDir;
  }

  private long getStartTime()
  {
    final ConfigurationBaratine config = getConfiguration();

    return config.testTime();
  }

  private String eval(String expr)
  {
    if (expr.charAt(0) != '{' || expr.charAt(expr.length() - 1) != '}')
      throw new IllegalArgumentException(L.l(
        "property {0} does not match expected format of {property}",
        expr));

    return System.getProperty(expr.substring(1, expr.length() - 1));
  }
}

class ConfigurationBaratineDefault
  extends AnnotationLiteral<ConfigurationBaratine>
  implements ConfigurationBaratine
{
  public static final long TEST_TIME = 894621091000L;

  static final ConfigurationBaratine INSTNANCE
    = new ConfigurationBaratineDefault();

  private ConfigurationBaratineDefault()
  {
  }

  @Override
  public String workDir()
  {
    return "{java.io.tmpdir}";
  }

  @Override
  public long testTime()
  {
    return TEST_TIME;
  }
}

