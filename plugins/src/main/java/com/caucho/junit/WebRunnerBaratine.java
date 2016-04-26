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

import static io.baratine.web.Web.port;
import static io.baratine.web.Web.start;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.loader.EnvironmentClassLoader;
import com.caucho.v5.util.L10N;
import com.caucho.v5.util.RandomUtil;
import com.caucho.v5.vfs.VfsOld;
import io.baratine.web.Web;
import io.baratine.web.WebServer;
import org.junit.Test;
import org.junit.internal.runners.statements.InvokeMethod;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

public class WebRunnerBaratine extends BaseRunner
{
  private final static L10N L = new L10N(WebRunnerBaratine.class);

  private WebServer _web;

  public WebRunnerBaratine(Class<?> klass) throws InitializationError
  {
    super(klass);
  }

  @Override
  protected void validatePublicVoidNoArgMethods(Class<? extends Annotation> annotation,
                                                boolean isStatic,
                                                List<Throwable> errors)
  {
    super.validatePublicVoidNoArgMethods(annotation, isStatic, errors);
  }

  @Override
  protected TestClass createTestClass(Class<?> testClass)
  {
    return new WebTestClass(testClass);
  }

  @Override
  protected Statement methodInvoker(FrameworkMethod method, Object test)
  {
    return new InvokeWebMethod(method, test);
  }

  private String httpHost()
  {
    return "localhost";
  }

  private int httpPort()
  {
    return 8080;
  }

  private String httpUrl()
  {
    return "http://" + httpHost() + ':' + httpPort();
  }

  @Override
  public void runChild(FrameworkMethod child, RunNotifier notifier)
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    EnvironmentClassLoader envLoader = EnvironmentClassLoader.create(oldLoader,
                                                                     "test-loader");

    try {
      thread.setContextClassLoader(envLoader);

      ConfigurationBaratine config = getConfiguration();

      if (config.testTime() != -1) {
        TestTime.setTime(config.testTime());
        RandomUtil.setTestSeed(config.testTime());
      }

      State.clear();

      Logger.getLogger("").setLevel(Level.FINER);
      Logger.getLogger("javax.management").setLevel(Level.INFO);

      String user = System.getProperty("user.name");
      String baratineRoot = "/tmp/" + user + "/qa";
      System.setProperty("baratine.root", baratineRoot);

      try {
        VfsOld.lookup(baratineRoot).removeAll();
      } catch (Exception e) {
      }

      //
      port(httpPort());

      for (ServiceTest serviceTest : getServices()) {
        Web.include(serviceTest.value());
      }

      _web = start();

      super.runChild(child, notifier);

    } finally {
      Logger.getLogger("").setLevel(Level.INFO);

      try {
        envLoader.close();
      } catch (Throwable e) {
        e.printStackTrace();
      }

      thread.setContextClassLoader(oldLoader);
    }
  }

  class InvokeWebMethod extends InvokeMethod
  {
    private Object _target;
    private FrameworkMethod _testMethod;

    public InvokeWebMethod(FrameworkMethod testMethod, Object target)
    {
      super(testMethod, target);

      _target = target;
      _testMethod = testMethod;
    }

    @Override
    public void evaluate() throws Throwable
    {
      this._testMethod.invokeExplosively(this._target, new Object[0]);
    }
  }

  class WebTestClass extends TestClass
  {
    public WebTestClass(Class<?> clazz)
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
              methods.add(new WebFrameworkMethod(javaMethod));
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

  class WebFrameworkMethod extends FrameworkMethod
  {
    public WebFrameworkMethod(Method method)
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
    {
      Class<?>[] types = getMethod().getParameterTypes();

      if (v.length == types.length)
        return v;

      Object[] args = new Object[types.length];

      int vStart = types.length - v.length;

      System.arraycopy(v, 0, args, vStart, v.length);

      for (int i = 0; i < vStart; i++) {
        Class type = types[i];

        if (HttpClient.class.equals(type)) {
          args[i] = new HttpClient(httpUrl());
        }
        else {
          throw new IllegalArgumentException(L.l("type {0} is not supported",
                                                 type));
        }
      }

      return args;
    }
  }
}
