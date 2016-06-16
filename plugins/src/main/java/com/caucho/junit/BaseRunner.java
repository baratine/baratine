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
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.v5.util.L10N;
import org.junit.Test;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;

abstract class BaseRunner<T extends InjectionTestPoint>
  extends BlockJUnit4ClassRunner
{
  private final static Logger log
    = Logger.getLogger(BaseRunner.class.getName());

  private final static L10N L = new L10N(BaseRunner.class);
  protected Object _test;

  private static final Level defaultBaratineLoggingLevel = Level.INFO;

  private Map<Class<?>,Class<?>> _replacements = new HashMap<>();

  public BaseRunner(Class<?> klass) throws InitializationError
  {
    super(klass);
  }

  final protected ConfigurationBaratine getConfiguration()
  {
    ConfigurationBaratine config
      = getTestClass().getAnnotation(ConfigurationBaratine.class);

    if (config == null) {
      config = ConfigurationBaratineDefault.INSTANCE;
    }

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
  protected final Object createTest() throws Exception
  {
    Object test = super.createTest();

    _test = test;

    try {
      initTestInstance();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw e;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }

    return test;
  }

  protected abstract void initTestInstance() throws Throwable;

  public List<T> getInjectionTestPoints()
    throws IllegalAccessException
  {
    List<T> result = new ArrayList<>();

    List<FrameworkField> fields = getTestClass().getAnnotatedFields();

    final MethodHandles.Lookup lookup = MethodHandles.lookup();

    for (FrameworkField field : fields) {
      Field javaField = field.getField();
      javaField.setAccessible(true);

      MethodHandle setter = lookup.unreflectSetter(javaField);

      T ip = createInjectionPoint(javaField.getType(),
                                  javaField.getAnnotations(),
                                  setter);

      result.add(ip);
    }

    return result;
  }

  public abstract T
  createInjectionPoint(Class<?> type,
                       Annotation[] annotations,
                       MethodHandle setter);

  @Override
  protected TestClass createTestClass(Class<?> testClass)
  {
    return new BaratineTestClass(testClass);
  }

  abstract protected Object resolve(T t);

  protected long getStartTime()
  {
    final ConfigurationBaratine config = getConfiguration();

    return config.testTime();
  }

  protected String getWorkDir()
  {
    final ConfigurationBaratine config = getConfiguration();

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

  public void replaceService(Class<?> target, Class<?> replacement)
  {
    _replacements.put(target, replacement);
  }

  public Class<?> resove(Class<?> target)
  {
    Class<?> result = _replacements.get(target);

    if (result == null)
      result = target;

    return result;
  }

  protected void setLoggingLevels()
  {
    LogConfigs logConfigs = getTestClass().getAnnotation(LogConfigs.class);
    LogConfig[] logs = null;

    if (logConfigs != null)
      logs = logConfigs.value();

    if (logs == null) {
      List<LogConfig> temp = new ArrayList<>();
      Annotation[] annotations = getTestClass().getAnnotations();
      for (Annotation annotation : annotations) {
        if (annotation instanceof LogConfig) {
          temp.add((LogConfig) annotation);
        }
      }
      logs = temp.toArray(new LogConfig[temp.size()]);
    }

    for (LogConfig config : logs) {
      Level level = Level.parse(config.level());
      Logger.getLogger(config.value()).setLevel(level);
    }
  }

  public abstract void stop();

  public abstract void start();

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
      throws Throwable
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

        MethodHandle handle = MethodHandles.arrayElementSetter(Object[].class);
        T ip = createInjectionPoint(type, annotations, handle);
        Object obj = resolve(ip);
        ip.inject(args, i, obj);
      }

      return args;
    }
  }
}
