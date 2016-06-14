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
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.amp.Amp;
import com.caucho.v5.amp.ServicesAmp;
import com.caucho.v5.amp.ensure.EnsureDriverImpl;
import com.caucho.v5.amp.journal.JournalDriverImpl;
import com.caucho.v5.amp.manager.InjectAutoBindService;
import com.caucho.v5.amp.spi.ServiceManagerBuilderAmp;
import com.caucho.v5.amp.spi.ShutdownModeAmp;
import com.caucho.v5.amp.vault.StubGeneratorVault;
import com.caucho.v5.amp.vault.StubGeneratorVaultDriver;
import com.caucho.v5.amp.vault.VaultDriver;
import com.caucho.v5.config.Configs;
import com.caucho.v5.config.inject.BaratineProducer;
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
import io.baratine.pipe.PipeIn;
import io.baratine.service.Api;
import io.baratine.service.Result;
import io.baratine.service.Service;
import io.baratine.service.ServiceRef;
import io.baratine.service.Services;
import io.baratine.vault.Asset;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;

/**
 * RunnerBaratine is a junit Runner used to test services deployed in Baratine.
 */
public class RunnerBaratine extends BaseRunner<RunnerInjectionTestPoint>
{
  private static final Logger log
    = Logger.getLogger(RunnerBaratine.class.getName());

  private static final L10N L = new L10N(RunnerBaratine.class);

  private BaratineContainer _baratine;

  public RunnerBaratine(Class<?> cl) throws InitializationError
  {
    super(cl);
  }

  @Override
  protected void initTestInstance() throws Throwable
  {
    _baratine.initialize(_test);
  }

  @Override
  public RunnerInjectionTestPoint createInjectionPoint(Class<?> type,
                                                       Annotation[] annotations,
                                                       MethodHandle setter)
  {
    return new RunnerInjectionTestPoint(type, annotations, setter);
  }

  @Override
  protected Object resolve(RunnerInjectionTestPoint ip)
  {
    return _baratine.findService(ip);
  }

  @Override
  public void stop()
  {
    _baratine.stop();
  }

  @Override
  public void start()
  {
    _baratine = new BaratineContainer();

    try {
      _baratine.initialize(_test);
      _baratine.start(false);
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  class BaratineContainer
  {
    private Map<ServiceTestDescriptor,ServiceRef> _descriptors;
    private Services _services;
    private SystemManager _manager;
    private RootDirectorySystem _rootDir;
    private EnvironmentClassLoader _envLoader;
    private ClassLoader _oldLoader;

    public void start()
    {
      try {
        initialize(_test);

        start(false);
      } catch (RuntimeException e) {
        throw e;
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    private void start(boolean isClean) throws Exception
    {
      Thread thread = Thread.currentThread();

      _oldLoader = thread.getContextClassLoader();

      _envLoader = EnvironmentClassLoader.create(_oldLoader, "test-loader");

      String baratineRoot = getWorkDir();
      System.setProperty("baratine.root", baratineRoot);

      thread.setContextClassLoader(_envLoader);

      long startTime = getStartTime();

      if (startTime != -1) {
        TestTime.setTime(startTime);

        RandomUtil.setTestSeed(startTime);
      }

      Logger.getLogger("").setLevel(Level.FINER);
      Logger.getLogger("com.caucho").setLevel(Level.FINER);
      Logger.getLogger("javax.management").setLevel(Level.FINER);

      try {
        if (isClean)
          VfsOld.lookup(baratineRoot).removeAll();
      } catch (Exception e) {
      }

      _manager = new SystemManager("junit-test", _envLoader);

      _rootDir = RootDirectorySystem.createAndAddSystem(Vfs.path(baratineRoot));

      _rootDir.start();
    }

    public void stop()
    {
      Logger.getLogger("").setLevel(Level.INFO);

      try {
        _rootDir.stop(ShutdownModeAmp.GRACEFUL);
      } catch (Throwable t) {
        t.printStackTrace();
      }

      try {
        _envLoader.close();
      } catch (Throwable t) {
        t.printStackTrace();
      }

      try {
        _manager.close();
      } catch (Throwable t) {
        t.printStackTrace();
      }

      Thread.currentThread().setContextClassLoader(_oldLoader);
    }

    void initialize(Object test) throws Throwable
    {
      InjectorAmp.create();

      ClassLoader cl = Thread.currentThread().getContextClassLoader();

      InjectorAmp.InjectBuilderAmp injectBuilder = InjectorAmp.manager(cl);

      injectBuilder.context(true);

      injectBuilder.include(BaratineProducer.class);

      Config.ConfigBuilder configBuilder = Configs.config();

      injectBuilder.bean(configBuilder.get()).to(Config.class);

      ServiceManagerBuilderAmp serviceBuilder = ServicesAmp.newManager();
      serviceBuilder.name("junit-test");
      serviceBuilder.autoServices(true);
      serviceBuilder.injector(injectBuilder);

      StubGeneratorVault gen = new StubGeneratorVaultDriver();

      gen.driver(new VaultResourceDriver());

      serviceBuilder.stubGenerator(gen);

      serviceBuilder.journalDriver(new JournalDriverImpl());
      serviceBuilder.ensureDriver(new EnsureDriverImpl());

      serviceBuilder.contextManager(true);

      ServicesAmp serviceManager = serviceBuilder.get();

      Amp.contextManager(serviceManager);

      injectBuilder.bean(serviceManager).to(Services.class);

      injectBuilder.autoBind(new InjectAutoBindService(serviceManager));

      injectBuilder.get();

      serviceBuilder.start();

      Map<ServiceTestDescriptor,ServiceRef> descriptors
        = deployServices(serviceManager);

      _descriptors = descriptors;
      _services = serviceManager;

      bindFields(test);
    }

    protected void bindFields(Object test)
      throws Throwable
    {
      getInjectionTestPoints();

      for (int i = 0; i < getInjectionTestPoints().size(); i++) {
        RunnerInjectionTestPoint ip = getInjectionTestPoints().get(i);

        Object value = null;

        if (ip.getService() != null)
          value = findService(ip);
        else if (ip.getInject() != null)
          value = findInject(ip);
        else
          continue;

        ip.inject(test, value);
      }
    }

    private Map<ServiceTestDescriptor,ServiceRef> deployServices(Services manager)
    {
      Map<ServiceTestDescriptor,ServiceRef> descriptors = new HashMap<>();

      for (ServiceTest service : getServices()) {
        Class serviceClass = service.value();
        serviceClass = resove(serviceClass);

        ServiceTestDescriptor descriptor
          = ServiceTestDescriptor.of(serviceClass);

        ServiceRef ref = manager.newService(serviceClass).addressAuto().ref();

        descriptors.put(descriptor, ref);
      }

      for (Map.Entry<ServiceTestDescriptor,ServiceRef> e : descriptors.entrySet()) {
        ServiceTestDescriptor desc = e.getKey();

        if (desc.isStart()) {
          e.getValue().start();
        }
      }

      return descriptors;
    }

    private Object findService(RunnerInjectionTestPoint ip)
    {
      if (Services.class.equals(ip.getType()))
        return _services;

      Map.Entry<ServiceTestDescriptor,ServiceRef> match = null;

      for (Map.Entry<ServiceTestDescriptor,ServiceRef> entry : _descriptors.entrySet()) {
        if (ip.isMatch(entry.getKey())) {
          match = entry;

          break;
        }
      }

      ServiceRef service = null;
      if (match != null) {
        service = match.getValue();
      }

      if (service == null)
        service = matchDefaultService(ip);

      if (service == null)
        throw new IllegalStateException(L.l(
          "unable to bind {0}, make sure corresponding service is deployed.",
          ip));

      if (ServiceRef.class == ip.getType())
        return service;
      else
        return service.as(ip.getType());
    }

    private ServiceRef matchDefaultService(RunnerInjectionTestPoint ip)
    {
      if (ip.address() != null && ip.address().isEmpty())
        return null;

      return _services.service(ip.address());
    }

    private Object findInject(RunnerInjectionTestPoint ip)
    {
      Object inject = null;

      if (Services.class.equals(ip.getType())) {
        inject = _services;
      }
      else if (RunnerBaratine.class.equals(ip.getType())) {
        inject = RunnerBaratine.this;
      }

      if (inject == null)
        throw new IllegalStateException(L.l("unable to bind {0}",
                                            ip));

      return inject;
    }
  }

  private class VaultResourceDriver
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

  static class ServiceTestDescriptor
  {
    private Class<?> _api;
    private Class<?> _serviceClass;
    private Service _service;

    private ServiceTestDescriptor(final Class serviceClass)
    {
      Objects.requireNonNull(serviceClass);

      _serviceClass = serviceClass;
      _service = getServiceAnnotation(serviceClass);

      if (_service == null)
        throw new IllegalStateException(L.l(
          "{0} must declare @Service annotation",
          _serviceClass));

      if (serviceClass.isInterface()) {
        _api = serviceClass;
      }
      else {
        _api = discoverApi();
      }
    }

    private Service getServiceAnnotation(Class serviceClass)
    {
      Service service;
      Class t = serviceClass;

      do {
        service = (Service) t.getAnnotation(Service.class);
      } while (service == null
               && t.getSuperclass() != null
               && (t = t.getSuperclass()) != Object.class);

      if (service == null) {
        Class[] interfaces = serviceClass.getInterfaces();
        for (Class face : interfaces) {
          service = (Service) face.getAnnotation(Service.class);

          if (service != null)
            break;
        }
      }

      return service;
    }

    private Class discoverApi()
    {
      Api api;
      Class t = _serviceClass;

      do {
        api = (Api) t.getAnnotation(Api.class);
      } while (api == null
               && (t = t.getSuperclass()) != Object.class);

      Class type = null;

      if (api != null)
        type = api.value();

      if (type == null) {
        Class[] interfaces = _serviceClass.getInterfaces();
        out:
        for (Class face : interfaces) {
          final Method[] methods = face.getDeclaredMethods();
          for (Method method : methods) {
            final Class<?>[] pTypes = method.getParameterTypes();
            for (Class<?> pType : pTypes) {
              if (Result.class.isAssignableFrom(pType)) {
                type = face;

                break out;
              }
            }
          }
        }
      }

      if (type == null)
        type = _serviceClass;

      return type;
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

    public boolean isStart()
    {
      final Method[] methods = _serviceClass.getDeclaredMethods();

      for (Method method : methods) {
        if (method.getAnnotation(PipeIn.class) != null)
          return true;
      }

      return false;
    }

    public static ServiceTestDescriptor of(Class t)
    {
      return new ServiceTestDescriptor(t);
    }

    @Override
    public boolean equals(Object o)
    {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ServiceTestDescriptor that = (ServiceTestDescriptor) o;

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

    @Override
    public String toString()
    {
      return this.getClass().getSimpleName() + "["
             + _api
             + ", "
             + _serviceClass
             + ", "
             + _service
             + ']';
    }
  }

  @Override
  protected void runChild(FrameworkMethod method, RunNotifier notifier)
  {
    try {
      _baratine = new BaratineContainer();

      _baratine.start(true);

      super.runChild(method, notifier);
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    } finally {
      _baratine.stop();
    }
  }
}

