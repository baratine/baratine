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
 * {@code OnActive} marks a method used to inform the service implementer that
 * the service is being started up. 
 *
 * <p>
 * If this is a journaled service then the the marked
 * method gets called before the journal is replayed after all dependencies have been
 * resolved. The marked method would need to return true to indicate that the service
 * started successfully if it returned anything. 
 * <p>
 * This marked method would be a good time for the service provider to 
 * read its state from storage before journal playback. If reading the service state from storage
 * is an async operation or a multistep operation, then instead of returning true from this
 * method, the service provider would add a <code>Result&lt;Boolean&gt;</code> argument, and call
 * <code>Result.ok(true)</code> when the service was done reading its state.
 *
 * @see io.baratine.service.Journal
 * @see io.baratine.service.OnSave
 */

@Documented
@Retention(RUNTIME)
@Target({METHOD})
public @interface OnActive {
}
