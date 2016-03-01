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

package javax.inject;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Designates fields, methods and constructors as injectable. Injectable can
 * have any access qualifier (private, package-private, protected or public).
 * It applies to static or instance members. The order of resolving injectable
 * is as following:
 * <lu>
 * <li>Constructors</li>
 * <li>Fields</li>
 * <li>Methods</li>
 * </lu>
 *
 * When resolving injectable super classes are resolved first.
 *
 * <h2>Example: injecting a servlet</h2>
 *
 * <code><pre>
 * package example;
 *
 * import javax.servlet.*;
 * import java.io.*;
 * import javax.inject.*;
 *
 * public class MyServlet extends GenericServlet {
 *   {@literal @Inject} MyBean _bean;
 *
 *   public void service(ServletRequest req, ServletResponse res)
 *     throws IOException
 *   {
 *     PrintWriter out = res.getWriter();
 *
 *     out.println("my-bean: " + _bean);
 *   }
 * }
 * </pre></code>
 */
@Documented  
@Retention(RUNTIME)
@Target({CONSTRUCTOR, FIELD, METHOD})
public @interface Inject {
}
