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
 * <code>@BeforeBatch</code> marks a method as a pre-process method, called before
 * a batch of messages are processed from the queue.
 * <br>
 * <br>
 * In this method a Baratine service can initialize its internal variables,
 * e.g open a database connection or prepare resources that need to be only
 * prepared once.
 * <br>
 * <br>
 * Releasing of resources can be done in a method marked with @AfterBatch.
 * <br>
 * <br>
 * The sequence of calling the {@code @BeforeBatch and @AfterBatch} methods is as following:
 * <ul>
 *   <li>@BeforeBatch</li>
 *   <li>Service method calls (one or more)</li>
 *   <li>@AfterBatch</li>
 * </ul>
 * <br>
 * <br>
 * Methods <code>@BeforeBatch</code>, <code>@AfterBatch</code> and Service methods
 * are called on the same <code>java.lang.Thread</code>.
 * <blockquote><pre>
 *
 * &#64;BeforeBatch
 * public void beforeBatch()
 * {
 *   _hibernateSession = _hibernateFactory.openSession();
 *   _session.getTransaction().begin();
 * }
 * </pre></blockquote>
 * <p>
 * @see io.baratine.service.AfterBatch
 */

@Documented
@Retention(RUNTIME)
@Target({METHOD})
public @interface BeforeBatch
{
}
