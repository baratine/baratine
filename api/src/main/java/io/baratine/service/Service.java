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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.inject.Qualifier;

/**
 * {@code @Service} marks a Service, which is an object with a single thread.
 * 
 * Method calls to the service are queued in its inbox and 
 * are invoked sequentially by the service thread.
 *
 * <blockquote>
 * <pre>
 * &#64;Service("public:///employee")
 * &#64;Startup
 * public class EmployeeService {
 *
 *   List&lt;Employee&gt; employees = new ArrayList&lt;&gt;();
 *
 *   public boolean addEmployee(Employee employee) {
 *       employees.add(employee);
 *       return true;
 *   }
 *
 *   public boolean removeEmployee(Employee employee) {
 *       employees.remove(employee);
 *       return true;
 *   }
 *   ...
 *
 * }
 * </pre></blockquote>
 * <br>
 * 
 * You could bind the above to an interface that looks like this:
 * 
 * <blockquote><pre>
 *  public interface EmployeeServiceNonBlocking {
 *
 *
 *   //Non blocking method
 *   public void addEmployee(Employee employee, Result&lt;Boolean&gt; resultHandler) ;
 *
 *   //Blocking method
 *   public boolean removeEmployee(Employee employee);
 *   
 *   ...
 *
 *}
 * </pre></blockquote>
 *
 * Service address can use "public" space to make service available externally.
 */

@Documented
@Retention(RUNTIME)
@Target({TYPE, METHOD, FIELD, PARAMETER})
@Qualifier
public @interface Service
{
  /**
   * Optional address of the service.
   */
  String value() default "";
}
