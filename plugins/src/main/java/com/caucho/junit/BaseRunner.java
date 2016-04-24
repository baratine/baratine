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
