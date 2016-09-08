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
 * @author Scott Ferguson
 */

package io.baratine.web;

/**
 * Interface ViewResolver provides support for custom response presentation
 * <p>
 * Custom ViewResolvers should be used for rendering of a View response
 * type. View, is a named Map supplied as a result of request processing e.g.
 * <p>
 * <blockquote><pre>
 *   &#64;Get
 *   public void indexHtml(RequestWeb request) {
 *     View view = View.newView("index.velocity");
 *     view.put("greeting", "Hello");
 *     //
 *     request.ok(view);
 *   }
 *   ...
 *   public class VelocityViewResolver implements ViewResolver&lt;View&gt;{
 *     public boolean render(RequestWeb request, View view) {
 *       //call to velocity to render the request
 *       return true;
 *     }
 *   }
 * </pre></blockquote>
 * <p>
 * A ViewResolver can also support more complicated JavaBean types or simple
 * types such as Strings:
 * <p>
 * <blockquote><pre>
 *   &#64;Get
 *   public void getGreeting(RequestWeb request) {
 *     request.ok("Hello World!");
 *   }
 *   ...
 *   public class StringViewResolver implements ViewResolver&lt;String&gt; {
 *     public boolean render(RequestWeb request, String value) {
 *       request.write("greeting: ");
 *       request.write(value);
 *       request.ok();
 *       return true;
 *     }
 *   }
 * </pre></blockquote>
 * <p>
 * Along with {@code View} class, ViewResolvers provide convenient base for
 * integrating with a variety of rendering engines. Baratine provides out of the
 * box support for FreeMarker, Velocity, Mustache, JetBrick, Jade and Thymeleaf.
 * <p>
 * ViewResolvers can be registered in one of the following ways:
 * <p>
 * <ul>
 * <li>by placing into *.autoconf.* package and calling Web.scanAutoConf() </li>
 * <li>by calling Web.scan(Package) on the Package where custom ViewResolver reside</li>
 * <li>programmatically via Web.bean(MyViewResolver.class).to(key)</li>
 * </ul>
 * <p>
 * <b>Register ViewResolver from .autoconf. package</b>
 * <blockquote><pre>
 *   package org.acme.autoconf.resolvers;
 *   &#64;Include
 *   public class StringViewResolver implements ViewResolver&lt;String&gt; {
 *     ...
 *   }
 *   ...
 *   Web.scanAutoConf();//discovers and registers the StringViewResolver
 * </pre></blockquote>
 * <p>
 * <b>Register ViewResolver with scan(Package)</b>
 * <blockquote><pre>
 *   Web.scan(StringViewResolver.class.getPackage());
 * </pre></blockquote>
 * <b>Register ViewResolver programmatically</b>
 * <blockquote><pre>
 *   Type bindingType = StringViewResolver.class.getGenericInterfaces()[0];
 *   //important to bind to parameterized type ViewResolver&lt;String&gt;
 *   Web.bean(StringViewResolver.class).to(Key.of(bindingType));
 * </pre></blockquote>
 * <p>
 *
 * @param <T> type of value to render
 * @see View
 */
public interface ViewResolver<T>
{
  /**
   * Renders value into request object. Note, that request object provides all
   * capabilities necessary for writing rendering data, such as OutputStream,
   * Writer, etc.
   * <p>
   * Baratine rendering algorithm will uses return value to determine if other
   * ViewResolvers should be called on the same value.
   * e.g.
   * <blockquote><pre>
   * public class MyGreetingResolver implements ViewResolver&lt;String&gt;{
   *   public boolean render(RequestWeb request, String value) {
   *     if (value.startsWith("Hello")) {
   *       //render
   *       return true;
   *     }
   *     return false;
   *   }
   * }
   * </pre></blockquote>
   * Only the greetings will be rendered using the MyGreetingResolver.
   * Other String values will be rendered using remaining matching ViewResolver&lt;String&gt;
   * resolvers.
   * <p>
   * Resolvers can declare priority using &#64;Priority annotation.
   *
   * @param request original request.
   * @param value   value to render
   * @return true if the ViewResolver rendered the object, false otherwise
   */
  boolean render(RequestWeb request, T value);
}
