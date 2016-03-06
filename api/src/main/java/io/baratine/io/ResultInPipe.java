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

package io.baratine.io;

import io.baratine.service.Result;


/**
 * {@code ResultInPipe} returns a pipe subscription.
 */
@FunctionalInterface
public interface ResultInPipe<T> extends Result<Void>
{
  /**
   * The subscriber's pipe.
   */
  InPipe<T> pipe();
  
  /**
   * Callee's out-pipe with a flow callback. 
   */
  default OutPipe<T> outPipe(OutFlow flow)
  {
    throw new IllegalStateException(getClass().getName());
  }
  
  default OutPipe<T> ok()
  {
    throw new IllegalStateException(getClass().getName());
  }
  
  /**
   * Sets the initial prefetch. Return -1 to disable the prefetch.
   */
  default int prefetch()
  {
    return 0;
  }

  @Override
  default void handle(Void value, Throwable exn)
  {
    if (exn != null) {
      pipe().fail(exn);
    }
  }
}
