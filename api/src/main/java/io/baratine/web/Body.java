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

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation @Body is used to assign a value from POST body to a service method parameter
 *
 * Body may be one of the following
 * a) a JSON formatted value with content-type 'application/json'
 * b) a form encoded with 'application/x-www-form-urlencoded'
 * c) a form encoded with 'multipart/form-data'
 *
 * Parameter may be on of the following types
 * a) io.baratine.web.Form e.g. void foo(@Body Form form) {}
 * b) primitive type e.g. void foo(@Body("user") String username, ...) {}
 * c) a type for JSON conversion, e.g. UserBean
 *
 */
@Documented
@Retention(RUNTIME)
@Target({PARAMETER})
public @interface Body
{
  /**
   * Specifies name in a submitted form name / value pair
   *
   * @return
   */
  String value() default "";
}
