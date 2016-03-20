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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;

import com.caucho.v5.amp.ServiceManagerAmp;
import com.caucho.v5.amp.ServiceRefAmp;
import com.caucho.v5.amp.manager.ServiceManagerAmpWrapper;
import com.caucho.v5.amp.service.ServiceRefDynamic;
import com.caucho.v5.baratine.ServicePod;
import com.caucho.v5.baratine.client.BaratineClient;
import com.caucho.v5.bartender.pod.PodBartender;
import com.caucho.v5.util.AnnotationRef;
import com.caucho.v5.util.CurrentTimeTest;
import com.caucho.v5.util.L10N;

import io.baratine.service.Lookup;
import io.baratine.service.Service;
import io.baratine.service.ServiceManager;
import io.baratine.service.ServiceRef;
import io.baratine.service.Startup;

public class RunnerBaratine extends BlockJUnit4ClassRunner
{
  private static final Logger log
    = Logger.getLogger(RunnerBaratine.class.getName());
  private final L10N L = new L10N(RunnerBaratine.class);

  private Map<String,BaratineContainer> _baratineMap = new LinkedHashMap<>();

//  private ServiceServer _baratine;

  private Object _test;

  public RunnerBaratine(Class<?> cl) throws InitializationError
  {
    super(cl);

    for (ConfigurationBaratine cfg
      : cl.getAnnotationsByType(ConfigurationBaratine.class)) {
      BaratineContainer baratineContainer = new BaratineContainer(cfg);

      _baratineMap.put(baratineContainer.getPodName(), baratineContainer);
    }

    if (_baratineMap.size() == 0) {
      ConfigurationBaratine clientConfig = new ClientConfiguration();
      BaratineContainer baratineContainer = new BaratineContainer(clientConfig);
      _baratineMap.put(baratineContainer.getPodName(), baratineContainer);
    }
  }

  @Override
  protected Object createTest() throws Exception
  {
    _test = super.createTest();

    initTestInstance();

    return _test;
  }

  private void initTestInstance() throws IllegalAccessException
  {
    TestClass testClass = getTestClass();

    bindLookupFields(testClass.getAnnotatedFields(Lookup.class));

    bindInjectFields(testClass.getAnnotatedFields(Inject.class));
  }

  private void bindLookupFields(List<FrameworkField> fields)
    throws IllegalAccessException
  {
    for (FrameworkField frameworkField : fields) {
      Field field = frameworkField.getField();

      Lookup lookup = field.getAnnotation(Lookup.class);

      BaratineContainer baratineController = getPod(lookup);

      if (baratineController == null)
        throw new IllegalStateException(
          String.format("can't find a controller for %1s %2s", lookup, field)
        );

      baratineController.bind(frameworkField, lookup);
    }
  }

  private void bindInjectFields(List<FrameworkField> fields)
    throws IllegalAccessException
  {
    for (FrameworkField frameworkField : fields) {
      Field field = frameworkField.getField();

      if (field.getAnnotation(Lookup.class) != null)
        continue;

      Object value = resolveInject(field);

      if (value == null) {
        continue;
      }

      field.setAccessible(true);
      field.set(_test, value);
    }
  }

  Object resolveInject(Field field)
  {
    Class<?> fieldType = field.getType();

    if (RunnerBaratine.class.equals(fieldType)) {
      return RunnerBaratine.this;
    }
    else if (ServiceManager.class.equals(fieldType) &&
             _baratineMap.size() == 1) {
      return _baratineMap.values()
                         .iterator()
                         .next()
                         .getServiceManager();
    }

    return null;
  }

  @Override
  public void run(RunNotifier notifier)
  {
    try {
      startBaratineContainers(StartMode.START);

      deployBaratineContainers();

      super.run(notifier);
    } finally {
      stopBaratineContainers();
    }
  }

  public void closeImmediate()
  {
    closeImmediate(100, TimeUnit.MILLISECONDS);
  }

  public void closeImmediate(long pause, TimeUnit unit)
  {
    try {
      Thread.sleep(unit.toMillis(pause));
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    /*
    ServiceServer baratine = _baratine;
    _baratine = null;

    baratine.closeImmediate();
    */
  }

  public void start()
  {
    startBaratineContainers(StartMode.RESTART);

    for (BaratineContainer container : _baratineMap.values()) {
      try {
        container.deployServices();

        container.reBind();
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void startBaratineContainers(StartMode startMode)
  {
    BaratineContainer seed = null;

    for (BaratineContainer baratineController : _baratineMap.values()) {
      try {
        baratineController.baratineStart(seed, startMode);

        if (seed == null) {
          seed = baratineController;
        }

      } catch (RuntimeException e) {
        e.printStackTrace();
        throw e;
      } catch (Exception e) {
        e.printStackTrace();

        throw new RuntimeException(e);
      }
    }
  }

  private void deployBaratineContainers()
  {
    for (BaratineContainer container : _baratineMap.values()) {
      try {
        container.baratineDeploy();
      } catch (RuntimeException e) {
        log.log(Level.FINER, e.getMessage(), e);

        throw e;
      } catch (Exception e) {
        log.log(Level.FINER, e.getMessage(), e);

        throw new RuntimeException(e);
      }
    }
  }

  private void stopBaratineContainers()
  {
    for (BaratineContainer baratineController : _baratineMap.values()) {
      try {
        baratineController.close();
      } catch (Exception e) {
        log.log(Level.WARNING, e.getMessage(), e);
      }
    }

    try {
      //_baratine.close();
    } catch (Exception e) {
      log.log(Level.WARNING, e.getMessage(), e);
    }
  }

  BaratineContainer getPod(Lookup lookup)
  {
    if (_baratineMap.size() == 1) {
      return _baratineMap.values().iterator().next();
    }

    String address = lookup.value();

    if (address.startsWith("pod://")) {
      int podLen = "pod://".length();

      String pod = address.substring(podLen, address.indexOf('/', podLen + 1));

      return _baratineMap.get(pod);
    }
    else if (address.startsWith("session://")) {
      int sessionLen = "session://".length();

      String pod = address.substring(sessionLen, address.indexOf('/',
                                                                 sessionLen
                                                                 + 1));

      return _baratineMap.get(pod);
    }

    return null;
  }

  public void addTime(int i, TimeUnit unit)
  {
    for (BaratineContainer controller : _baratineMap.values()) {
      controller.addTime(i, unit);
    }
  }

  public class BaratineContainer
  {
    private ConfigurationBaratine _config;
    private String _podName;
    private ServicePod _pod;
    private BaratineClient _baratineClient;

    private List<Field> _fields = new ArrayList<>();

    BaratineContainer(ConfigurationBaratine config)
    {
      _config = config;

      _podName = config.pod();
    }

    public void addTime(long delta, TimeUnit unit)
    {
      if (_config.testTime() < 0) {
        throw new IllegalStateException();
      }

      CurrentTimeTest.addTime(unit.toMillis(delta));
    }

    void close()
    {
      try {
        if (_baratineClient != null) {
          _baratineClient.close();
        }
      } catch (Exception e) {
        log.log(Level.WARNING, e.getMessage(), e);
      }
    }

    BaratineClient getClientHamp()
    {
      if (_baratineClient != null)
        return _baratineClient;

      String pod = "pod";
      String url = "http://" + getHost() + ":" + getPort() + "/s/" + pod + "/";

      _baratineClient = new BaratineClient(url);

      return _baratineClient;
    }

    ServiceManagerAmp getServiceManager()
    {
      /*
      if ("client".equals(_podName)) {
        return (ServiceManagerAmp) _baratine.client();
      }

      // baratine/b000
      ServiceManagerAmp manager
        = (ServiceManagerAmp) _baratine.pod(_podName).manager();

      manager = (ServiceManagerAmp) _pod.manager();

      //ServiceManagerAmp manager = (ServiceManagerAmp) _baratine.client();

      return manager;
        */
      return null;
    }

    void bind(FrameworkField frameworkField, Lookup lookup)
      throws IllegalAccessException
    {
      Field field = frameworkField.getField();

      _fields.add(field);

      bindImpl(field, lookup);
    }

    void reBind() throws IllegalAccessException
    {
      for (Field field : _fields) {
        Lookup lookup = field.getAnnotation(Lookup.class);
        bindImpl(field, lookup);
      }
    }

    private void bindImpl(Field field, Lookup lookup)
      throws IllegalAccessException
    {
      Object value = resolveLookup(field, lookup);

      field.setAccessible(true);

      field.set(_test, value);

      if (value == null)
        throw new IllegalStateException(L.l("can't bind field {0}", field));
    }

    private Object resolveLookup(Field field, Lookup lookup)
    {
      Class<?> fieldType = field.getType();

      if (ServiceManager.class.isAssignableFrom(fieldType)) {
        return new ServiceManagerProxy();
      }

      String address = lookup.value();

      ServiceRef serviceRef;

      if (address.startsWith("remote:")) {
        serviceRef = getClientHamp().service(address);
      }
      else if (address.startsWith("pod://" + _podName)) {
        //address = address.substring(("pod://" + _podName).length());

        serviceRef = new ServiceRefTestProxy(address);
      }
      else {
        serviceRef = new ServiceRefTestProxy(address);
      }

      if (ServiceRef.class.isAssignableFrom(fieldType)) {
        return serviceRef;
      }
      else {
        return serviceRef.as(fieldType);
      }
    }

    String getHost()
    {
      return _config.host();
    }

    int getPort()
    {
      return _config.port();
    }

    String getRootDirectory()
    {
      String directory = _config.rootDirectoryBase()
                         + File.separatorChar
                         + "junit-baratine-runner";

      return directory;
    }

    void baratineStart(BaratineContainer base, StartMode startMode)
      throws IOException
    {
      if (base == null && startMode == StartMode.START) {
        startCommon();
      }

      /*
      ServiceServer baratine = buildServer(base, startMode);

      ServicePod pod = baratine.pod(getPodName());

      if (true || pod == null) {
        ServiceServer.PodBuilder podBuilder = baratine.newPod(getPodName());

        final PodBartender.PodType podType = getPodType();

        switch (podType) {
        case solo:
          podBuilder.solo();
          break;
        case pair:
          podBuilder.pair();
          break;
        case triad:
          podBuilder.triad();
          break;
        case cluster:
          podBuilder.cluster();
          break;
        default:
          throw new RuntimeException(L.l("{0} is invalid pod type", podType));
        }

        podBuilder.journalDelay(_config.journalDelay(), TimeUnit.MILLISECONDS);

        if (_config != null) {
          for (String deploy : _config.deploy()) {
            podBuilder.deploy(deploy);
          }
        }

        pod = podBuilder.build();

        Objects.requireNonNull(pod);
      }

      _pod = pod;
      */
    }

    private void startCommon()
    {
      if (_config.testTime() >= 0) {
        long now;

        if (_config.testTime() == 0) {
          now = System.currentTimeMillis();
        }
        else {
          now = _config.testTime();
        }

        CurrentTimeTest.setTime(now);
      }

      final String confLevel;

      confLevel = _config.logLevel().toUpperCase();

      Level level = Level.parse(confLevel);

      Logger.getLogger("javax.management").setLevel(Level.INFO);

      for (String log : _config.logNames()) {
        Logger.getLogger(log).setLevel(level);
      }

      if (_config.logs().length > 0) {
        for (ConfigurationBaratine.Log log : _config.logs()) {
          Level l = Level.parse(log.level().toUpperCase());
          Logger logger = Logger.getLogger(log.name());
          logger.setLevel(l);
        }
      }
      else {
        Logger.getLogger("").setLevel(level);
      }
    }

    /*
    private ServiceServer buildServer(BaratineContainer base,
                                       StartMode startMode)
      throws IOException
    {
      if (_baratine != null) {
        return _baratine;
      }

      ServiceServer.Builder builder = ServiceServer.newServer();
      
      builder.client(false);

      if (base == null) {
        builder.port(getPort());
      }

      if (getPort() > 0) {
        builder.port(getPort());
      }

      if (base != null) {
        builder.seed("localhost", base.getPort());
      }

      if (getPort() > 0) {
        builder.port(getPort());
      }
      
      String rootDirectory = getRootDirectory();

      PathImpl path = Vfs.lookup(rootDirectory);

      if (startMode == StartMode.START && path.exists()) {
        path.removeAll();
      }

      builder.root(getRootDirectory());

      _baratine = builder.build();

      return _baratine;
    }
    */

    public ServicePod getPod()
    {
      return _pod;
    }

    void baratineDeploy()
    {
      deployServices();
    }

    void deployServices()
    {
      for (int i = 0; i < getPod().getNodeCount(); i++) {
        ServiceManager manager = getPod().node(i).manager();
        
        for (Class<?> serviceClass : _config.services()) {
          log.finer(L.l("deploying {0} to {1}", serviceClass, manager));
          
          Service service = serviceClass.getAnnotation(Service.class);
          
          if (service != null
              && ! service.value().isEmpty()
              && ! manager.service(service.value()).isClosed()) {
            // XXX: baratine/b0b0 double bind
            continue;
          }

          ServiceRef ref = manager.newService(serviceClass).ref();

          if (serviceClass.getAnnotation(Startup.class) != null) {
            ref.start();
          }
        }
      }
    }

    public String getPodName()
    {
      return _config.pod();
    }

    public PodBartender.PodType getPodType()
    {
      return _config.podType();
    }

    @SuppressWarnings("serial") 
    class ServiceRefTestProxy
      extends ServiceRefDynamic
    {
      private String _address;

      private ServiceRefAmp _delegate;
      private ServiceManagerAmp _serviceManager;

      ServiceRefTestProxy(String address)
      {
        _address = address;
      }

      @Override
      public ServiceRefAmp delegate()
      {
        ServiceManagerAmp serviceManager = getServiceManager();

        if (serviceManager != _serviceManager) {
          _delegate = serviceManager.service(_address);

          _serviceManager = serviceManager;

          _delegate.start();

          // baratine/b081
          try { Thread.sleep(10); } catch (Exception e) {}
        }

        return _delegate;
      }

      @Override
      public ServiceRefAmp node(int hash)
      {
        return delegate().node(hash);
      }

      @Override
      public int nodeCount()
      {
        return delegate().nodeCount();
      }
    }

    class ServiceManagerProxy extends ServiceManagerAmpWrapper
    {
      @Override
      public ServiceManagerAmp delegate()
      {
        return getServiceManager();
      }
    }
  }

  static class ClientConfiguration
    extends AnnotationRef<ConfigurationBaratine>
    implements ConfigurationBaratine
  {
    @Override
    public String host()
    {
      return "host";
    }

    @Override
    public String rootDirectoryBase()
    {
      return "/tmp/";
    }

    @Override
    public int port()
    {
      return -1;
    }

    @Override
    public String[] deploy()
    {
      return new String[0];
    }

    @Override
    public Class<?>[] services()
    {
      return new Class<?>[0];
    }

    @Override
    public String logLevel()
    {
      return "OFF";
    }

    @Override
    public String[] logNames()
    {
      return new String[0];
    }

    @Override
    public Log[] logs()
    {
      return new Log[0];
    }

    @Override
    public String pod()
    {
      return "client";
    }

    @Override
    public PodBartender.PodType podType()
    {
      return PodBartender.PodType.solo;
    }

    @Override
    public long testTime()
    {
      return -1;
    }

    @Override
    public long journalDelay()
    {
      return -1;
    }
  }

  enum StartMode
  {
    START, RESTART;
  }
}

