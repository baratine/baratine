package com.caucho.junit;

import com.caucho.v5.inject.AnnotationLiteral;

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
