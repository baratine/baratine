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

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * {@code @Queue} marks a Service that uses a single-threaded actor model where message-method calls are queued.
 * The following defines a local AmpService
 * 
 * <pre>
 * <code>@Service</code>
 * <code>@Startup</code>
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
 * </pre>
 * 
 * You could bind the above to an interface that looks like this:
 * 
 * <pre>
 *  public interface EmployeeServiceNonBlocking {
 *
 *
 *   //Non blocking method
 *   public void addEmployee(Employee employee, Result&lt;Boolean&gt; result)
 *
 *   //Blocking method
 *   public boolean removeEmployee(Employee employee);
 *   
 *   ...
 *
 *}
 * </pre>
 * 
 */

@Documented
@Retention(RUNTIME)
@Target({TYPE})
public @interface Queue
{
  /**
   * How many workers should be in the pool to start, defaults to letting
   * the Amp system decide.
   */
  int initial() default -1;
  
  /**
   * The maximum number of working in the pool.
   */
  int capacity() default -1;
  
  /**
   * Timeout in milliseconds for an offer to a full queue.
   */
  long offerTimeout() default -1;
  
  /**
   * Handler when the queue is full and times out.
   */
  /*
  Class<? extends QueueFullHandler> queueFullHandler()
    default QueueFullHandler.class;
    */
}
