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

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Depending on the use context annotation Path is used to bind URI to Service or
 * bind service method parameter to a named URI part.
 * <p>
 * <b>Binding URI to a service</b>
 * <blockquote><pre>
 * &#64;Service
 * &#64;Path("/hello-module")
 * public class MyService {
 *   &#64;Get("/hello")
 *   public void hello(RequestWeb request) {
 *     request.ok("Hello");
 *   }
 * }
 * </pre></blockquote>
 * <p>
 * When MyService is deployed 'hello' method is exposed at '/hello-module/hello'.
 * <p>
 * <b>Specifying Asset base URI</b>
 * <p>
 * When annotation Path is used on an Asset, the annotation specifies
 * base URI at which Assets of that type are accessible.
 * <p>
 * e.g.
 * <blockquote><pre>
 * &#64;Asset
 * &#64;Path("/items")
 * public class MyItem {
 * &#64;Id private _id;
 * }
 * </pre></blockquote>
 * The above makes Assets MyItem available at /items/{id}
 * <p>
 * <b>Injecting parameter into service method</b>
 * <p>
 * When method needs a parameter encoded in URI's path it can be injected using
 * path template as so.
 * <p>
 * <blockquote><pre>
 * &#64;Service
 * public class MyService {
 *   &#64;Get("/hello/{subj}")
 *   public void hello(@Path String subj, RequestWeb request) {
 *     request.ok("Hello " + subj);
 *   }
 * }
 * </pre></blockquote>
 * <p>
 * When method is invoked subj expands to the matching part of the request URI:
 * <p>
 * e.g. request below will output 'Hello World'<br>
 * curl http://localhost:8080/hello/World?bogus=focus
 */
@Documented
@Retention(RUNTIME)
@Target({TYPE, METHOD, PARAMETER})
public @interface Path
{
  /**
   * Path id to be assigned to the parameter.
   */
  String value();
}
