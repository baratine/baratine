/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)(TM)
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

package io.baratine.web;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation Get makes service method accessible via HTTP for GET requests.
 *
 * By default the URI is inferred from the method name but can be overridden
 * using Get's value() attribute.
 *
 * e.g.
 *
 * <pre>
 *   <code>
 *     &#64;Get
 *     public void foo(Result&lt;String&gt; result) {  result.ok("Hello World");  }
 *   </code>
 * </pre>
 *
 * The above GET maps requests to /foo URI to method foo().
 */
@Documented
@Retention(RUNTIME)
@Target({TYPE, METHOD})
public @interface Get
{
  /**
   * Optional address of the service.
   */
  String value() default "";
}
