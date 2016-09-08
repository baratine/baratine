package com.caucho.v5.config;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

/**
 * Annotation Priority is used to determine order of matching bindings for
 * Beans, ViewResolvers, ViewRenders, Converters
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({TYPE,METHOD})
public @interface Priority
{
  /**
   * Priority value for selecting the top most match
   *
   * @return priority value
   */
  int value();
}
