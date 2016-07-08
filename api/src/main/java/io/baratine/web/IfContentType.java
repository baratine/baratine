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

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation IfContentType allows resolving method based on the content-type.
 *
 * It is used when one path needs to bind to two or more different methods based on
 * the value of the Content-Type request header.
 * e.g.
 * <pre>
 *   <code>
 *   @Get("/get")
 *   @IfContentType("text/plain")
 *   public void getTxt(Result&lth;String> result) {
 *     result.ok("hello");
 *   }
 *
 *   @Get("/get")
 *   @IfContentType("text/xml")
 *   public void getTxt(Result<String> result) {
 *     result.ok("<msg>hello</msg>");
 *   }
 *   </code>
 * </pre>
 */
@Documented
@Retention(RUNTIME)
@Target({METHOD})
public @interface IfContentType
{
  /**
   * Content types matched by the method.
   */
  String []value();
}
