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
 * Interface ServiceWeb is used in three following ways
 * a) define a before RequestWeb filter
 * b) define an after RequestWeb filter
 * c) define a RequestWeb processor
 * <p>
 * e.g.
 * <p>
 * <blockquote><pre>
 *   Web.get("/hello").to(HelloService.class);
 *
 *   class HelloService implements ServiceWeb {
 *     &#64;Override
 *     public void service(RequestWeb request)
 *     {
 *       request.ok("hello");
 *     }
 *   }
 * </pre></blockquote>
 *
 * @see FilterBefore
 * @see FilterAfter
 * @see io.baratine.web.WebBuilder.RouteBuilder#before(Class)
 * @see io.baratine.web.WebBuilder.RouteBuilder#to(Class)
 * @see io.baratine.web.WebBuilder.RouteBuilder#after(Class)
 */
@FunctionalInterface
public interface ServiceWeb
{
  /**
   * service method invoked on the ServiceWeb implementation
   *
   * @param request client request
   * @throws Exception processing exception
   */
  void service(RequestWeb request)
    throws Exception;
}
