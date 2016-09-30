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

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <code>@AfterBatch</code> marks a method as a post-process method, called after
 * messages are processed from the queue, when the queue is empty.
 * <p>
 * This can be used to do batching operations to optimize IO throughput,
 * e.g. writing changes to a file or an external database.
 * <p>
 * In this method can also be closed resources used by the service methods.
 * <p>
 * Methods marked with <code>@AfterBatch</code>, <code>@BeforeBatch</code> and
 * service methods are called on the same <code>java.lang.Thread</code>.
 * <p>
 * <blockquote>
 * <pre>
 * &#64;AfterBatch
 * public void afterBatch()
 * {
 *   _hibernateSession.getTransaction().commit();
 *   _hibernateSession.close();
 * }
 * </pre>
 * </blockquote>
 * <p>
 * Demonstration of effect of batching:
 * <blockquote>
 * <pre>
 * &#64;Service
 * public static class BatchAwareService
 * {
 *   private long counter;
 *
 *   &#64;BeforeBatch
 *   public void beforeBatch()
 *   {
 *     System.out.println("BatchAwareService.beforeBatch: " + counter);
 *   }
 *
 *   public void foo(Result&lt;Long&gt; result)
 *   {
 *     result.ok(counter++);
 *   }
 *
 *   &#64;AfterBatch
 *   public void afterBatch()
 *   {
 *     System.out.println("BatchAwareService.afterBatch: " + counter);
 *   }
 * }
 * </pre>
 * </blockquote>
 *
 * When above service called with the following code
 * <blockquote>
 * <pre>
 * for (int i = 0; i &lt; 100; i++)
 * service.foo(Result.ignore());
 * Thread.sleep(1000);
 *
 * for (int i = 0; i &lt; 100; i++)
 * service.foo(Result.ignore());
 * </pre>
 * </blockquote>
 * it will produce output similar to:
 * <blockquote>
 * <pre>
 * BatchAwareService.beforeBatch: 0
 * BatchAwareService.afterBatch: 100
 * BatchAwareService.beforeBatch: 100
 * BatchAwareService.afterBatch: 200
 * </pre>
 * </blockquote>
 * <br>
 *
 * @see io.baratine.service.BeforeBatch
 */

@Documented
@Retention(RUNTIME)
@Target({METHOD})
public @interface AfterBatch
{
}
