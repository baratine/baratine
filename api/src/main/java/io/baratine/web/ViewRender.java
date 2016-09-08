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
 * Interface ViewRender provides contract for custom renderers for values of
 * specific type.
 * <p>
 * E.g.
 * <p>
 * <b>Creating a custom renderer</b>
 * <blockquote><pre>
 * public class MyBeanRender implements ViewRender&lt;MyBean&gt;
 * {
 *   &#64;Override
 *   public void render(RequestWeb requestWeb, MyBean myBean)
 *   {
 *     requestWeb.write("&lt;to-string&gt;");
 *     requestWeb.write(myBean.toString());
 *     requestWeb.write("&lt;/to-string&gt;");
 *     requestWeb.ok();
 *   }
 * }
 * </pre></blockquote>
 * <p>
 * <b>register renderer in one of the following ways:</b>
 * <blockquote><pre>
 *   //using instance
 *   Web.view(new MyBeanRender());
 *   // or using class
 *   Web.view(MyBeanRender.class);
 * </pre></blockquote>
 * <p>
 * <b>use with service</b>
 * <p>
 * View renderer is choosen based on the type of the result passed into
 * ok(Object) method of the RequestWeb.
 * <p>
 * <blockquote><pre>
 *   &#64;Service
 *   public class MyService {
 *     &#64;Get
 *     public void myBean(RequestWeb request) {
 *       request.ok(new MyBean());
 *     }
 *   }
 * </pre></blockquote>
 *
 * @param <T> type of value to render
 */
public interface ViewRender<T>
{
  /**
   * Directly renders the value
   *
   * @param req   the web request
   * @param value the value to be rendered
   */
  void render(RequestWeb req, T value);
}
