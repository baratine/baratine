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

package io.baratine.web;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation FilterBefore used to declare a RequestWeb filter.
 *
 * e.g.
 * <blockquote><pre>
 * //define filter
 * public class MyBeforeFilter implements ServiceWeb
 * {
 *   &#64;Override
 *   public void service(RequestWeb requestWeb) throws Exception
 *   {
 *     //do filter processing
 *     requestWeb.ok(); // required to allow processing continue
 *   }
 * }
 *
 * //invoke filter before request
 * &#64;Service
 * public class MyService {
 *   &#64;Get
 *   &#64;BeforeFilter(MyBeforeFilter.class)
 *   public void data(Result&lt;String&gt; result) {
 *     result.ok("Hello World");
 *   }
 * }
 * </pre></blockquote>
 *
 * Filter can apply to a method or a class. When it applies to a class the filter
 * will be invoked for all web requests dispatched to methods of this class.
 *
 * Can be used as a meta annotation for annotating filter annotations, for example
 * see {@code io.baratine.web.cors.CrossOrigin} annotation.
 *
 * @see ServiceWeb
 *
 */
@Retention(RUNTIME)
@Target({METHOD,TYPE})
@Repeatable(FiltersBefore.class)
public @interface FilterBefore
{
  /**
   * Specifies filter implementation class
   *
   * @return filter class
   */
  Class<? extends ServiceWeb> value();

  /** Specifies filter name
   *
   * @return name
   */
  String name() default "";
}
