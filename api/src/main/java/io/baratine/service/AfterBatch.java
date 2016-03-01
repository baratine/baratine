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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * <code>@AfterBatch</code> marks a method as a post-process method, called after
 * messages are processed from the queue, when the queue is empty.
 * <br>
 * <br>
 * This can be used to do batching operations to optimize IO throughput,
 * e.g. writing changes to a file or an external database.
 * <br>
 * <br>
 * In this method can also be closed resources used by the service methods.
 * <br>
 * <br>
 * Methods marked with <code>@AfterBatch</code>, <code>@BeforeBatch</code> and
 * service methods are called on the same <code>java.lang.Thread.</code>.
 * <br>
 * <br>
 * <blockquote><pre>
 * &#64;AfterBatch
 * public void afterBatch()
 * {
 *   _hibernateSession.getTransaction().commit();
 *   _hibernateSession.close();
 * }
 * </pre></blockquote>
 * <br>
 * See <a target="__new" href="https://github.com/baratine/example-update-service-with-hibernate/blob/master/src/main/java/stock/StockServiceBean.java">
 * https://github.com/baratine/example-update-service-with-hibernate/blob/master/src/main/java/stock/StockServiceBean.java</a> for complete example.
 *
 * @see io.baratine.service.BeforeBatch
 */

@Documented
@Retention(RUNTIME)
@Target({METHOD})
public @interface AfterBatch
{
}
