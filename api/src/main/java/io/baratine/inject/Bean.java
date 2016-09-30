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

package io.baratine.inject;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.inject.Qualifier;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation {@code @Bean} is a default injection qualifier which can be used
 * to do the following:
 * <p>
 * <ul>
 * <li>Define a bean producer</li>
 * <li>Inject value into a field</li>
 * <li>Inject value via a method</li>
 * <li>Inject value via a constructor</li>
 * </ul>
 * <p>
 * <b>Define a producer of specific type of beans</b>
 * <blockquote>
 * <pre>
 * public static class FooProducer
 * {
 *   &#64;Bean
 *   public Foo produceFoo()
 *   {
 *     return new FooImpl();
 *   }
 * }
 * </pre>
 * </blockquote>
 * <b>Inject into a service via {@code @Inject} field</b>
 * <blockquote>
 * <pre>
 * &#64;Service
 * public static class MyService
 * {
 *   &#64;Inject &#64;Bean
 *   Foo _foo;
 *
 *   &#64;Get
 *   public void test(RequestWeb request)
 *   {
 *     request.ok(_foo);
 *   }
 * }
 * </pre>
 * </blockquote>
 * <b>Inject into a service via {@code @Inject} method</b>
 * <blockquote>
 * <pre>
 * &#64;Service
 * public static class MyService
 * {
 *   Foo _foo;
 *
 *   &#64;Inject
 *   private void inject(&#64;Bean Foo foo)
 *   {
 *     _foo = foo;
 *   }
 *
 *   &#64;Get
 *   public void test(RequestWeb request)
 *   {
 *     request.ok(_foo);
 *   }
 * }
 * </pre>
 * </blockquote>
 * <b>Inject into a service via {@code @Inject} constructor</b>
 * <blockquote>
 * <pre>
 * &#64;Service
 * public static class MyService
 * {
 *   Foo _foo;
 *   public MyService()
 *   {
 *   }
 *
 *   &#64;Inject
 *   public MyService(@Bean Foo foo)
 *   {
 *     _foo = foo;
 *   }
 *
 *   &#64;Get
 *   public void test(RequestWeb request)
 *   {
 *     request.ok(_foo);
 *   }
 * }
 * </pre>
 * </blockquote>
 */
@Retention(RUNTIME)
@Target({TYPE, METHOD, FIELD, PARAMETER})
@Qualifier
public @interface Bean
{
}
