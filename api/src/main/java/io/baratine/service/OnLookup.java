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

package io.baratine.service;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * {@code OnLookup} is used to facilitate looking up child services. In that case
 * parent services are responsible for implementing a lookup method which should
 * return an instance of a child server. The instance of a child service, once created,
 * will be cached by Services manager.
 * <p>
 * The lookup method of a parent service must be marked with &#64;OnLookup to be
 * discovered the Baratine framework.
 * <p>
 * &#64;OnLookup method must correspond to the following signature with two caveats:
 * <b>method name can be anything</b> and <b>method return type could also be anything</b>
 * <p>
 * <blockquote>
 * <pre>
 *      &#64;OnLookup
 *      public Object lookup(String childPath);
 *   </pre>
 * </blockquote>
 * <p>
 *
 * Example of a parent and child service lookup:
 *
 * <blockquote>
 * <pre>
 * &#64;Service("/ParentService")
 * public static class ParentService
 * {
 *   &#64;OnLookup
 *   public ChildService lookup(String path)
 *   {
 *     return new ChildService(path);
 *   }
 * }
 *
 * public static class ChildService
 * {
 *   private String path;
 *
 *   public ChildService()
 *   {
 *   }
 *
 *   public ChildService(String path)
 *   {
 *     this.path = path;
 *   }
 * }
 *
 * //looking up:
 * Services services = ...;//obtain instance of service manager with &#64;Inject or current()
 * services.service("/ParentService/child");
 * </pre>
 * </blockquote>
 */

@Documented
@Retention(RUNTIME)
@Target({METHOD})
public @interface OnLookup
{
  /**
   * Reserved for future use
   *
   * @return
   */
  boolean shared() default false;
}
