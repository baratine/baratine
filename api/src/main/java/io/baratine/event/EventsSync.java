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

package io.baratine.event;

import io.baratine.service.Cancel;
import io.baratine.service.Pin;
import io.baratine.service.Result;
import io.baratine.service.Service;

/**
 * Synchronous interface to the Baratine Event Service.
 * <p>
 * See {@code Events} class for documentation
 *
 * @see Events
 */
@Service("event:")
public interface EventsSync extends Events
{
  /**
   * Registers publisher at path.
   * See Events#publisherPath(String, Class, Result) for documentation
   *
   * @see Events#publisherPath(String, Class, Result)
   */
  @Pin
  <T> T publisherPath(String path, Class<T> api);

  /**
   * Registers publisher using a given api
   * See Events#publisher(Class, Result) for documentation
   *
   * @see Events#publisher(Class, Result)
   */
  @Pin
  <T> T publisher(Class<T> api);

  /**
   * Registers consumer for a given path.
   * See Events#consumer(String, Object, Result) for documentation
   *
   * @see Events#consumer(String, Object, Result)
   */
  <T> Cancel consumer(String path, @Pin T consumer);

  /**
   * Registers consumer using a given api
   * See Events#consumer(Class, Object, Result) for documentation
   *
   * @see Events#consumer(Class, Object, Result)
   */
  <T> Cancel consumer(Class<T> api, @Pin T consumer);

  /**
   * Registers subscriber for a given path
   * See Events#subscriber(String, Object, Result) for documentation
   *
   * @see Events#subscriber(String, Object, Result)
   */
  <T> Cancel subscriber(String path, @Pin T subscriber);

  /**
   * Registers subscribers using a given api
   * See Events#subscriber(Class, Object, Result) for documentation
   *
   * @see Events#subscriber(Class, Object, Result)
   */
  <T> Cancel subscriber(Class<T> api, @Pin T subscriber);
}
