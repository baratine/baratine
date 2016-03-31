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

package io.baratine.pipe;

import io.baratine.pipe.Pipe.FlowOut;
import io.baratine.pipe.Pipes.PipeInResultImpl;
import io.baratine.service.Result;

/**
 * {@code ResultInPipe} returns a pipe subscription.
 */
@FunctionalInterface
public interface ResultPipeIn<T> extends Result<FlowOut<T>>
{
  //
  // caller/subscriber side
  //
  
  /**
   * The subscriber's {@code Pipe} handler will be registered as
   * the pipe consumer.
   */
  default Pipe<T> pipe()
  {
    return new PipeInResultImpl<>(this);
  }

  /**
   * Subscription lambda for basic clients.
   * 
   * Clients that need more control over the flow should use the pipe().
   */
  void handle(T next, Throwable fail, boolean ok);
  
  @Override
  default void handle(FlowOut<T> ok, Throwable fail)
  {
    if (fail != null) {
      handle(null, fail, false);
    }
  }
}
