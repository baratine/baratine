/*
 * Copyright (c) 1998-2016 Caucho Technology -- all rights reserved
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
 * @author Scott Ferguson
 */

package io.baratine.config;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation Var marks a field for injecting value from configuration.
 * e.g. injecting a single value:
 * <blockquote><pre>
 *  &#64;Inject &#64;Var("key")
 *  String value;
 * </pre></blockquote>
 * e.g. inject a bean <br>
 * conf.yml
 * <blockquote><pre>
 * bean.foo: Foo-Value
 * bean.bar: Bar-Value
 * </pre></blockquote>
 * <blockquote><pre>
 * public class MyBean {
 *   String foo; //uses value from bean.foo key
 *   int bar; //uses value from bean.bar key
 * }
 * <br>
 * public class MyBeanClient {
 *   &#64;Inject
 *   &#64;Var("bean") //specifies values to be filled from "bean.*" keys
 *   MyBean _bean;
 * }
 * </pre></blockquote>
 *
 * @see Config
 */
@Documented
@Retention(RUNTIME)
@Target({FIELD, TYPE})
public @interface Var
{
  /**
   * Specifies key for the property
   *
   * @return key name
   */
  String value() default "";

  /**
   * Specifies default value represented as String
   *
   * @return default value
   */
  String defaultValue() default "";
}
