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

import io.baratine.pipe.PipeStatic.PipeSubHandlerImpl;

/**
 * {@code Pipe} sends a sequence of values from a source to a sink.
 *
 *  The sink is defined with either PipeSub.
 *
 *
 */
public interface Pipe<T>
{
  public static final int PREFETCH_DEFAULT = 0;
  public static final int PREFETCH_DISABLE = -1;
  
  public static final int CREDIT_DISABLE = -1;
  
  /**
   * Supplies the next value.
   */
  void next(T value);

  /**
   * Completes sending the values to the client and signals to the client
   * that no more values are expected.
   */
  void close();

  /**
   * Signals a failure.
   * 
   * The pipe is closed on failure.
   */
  void fail(Throwable exn);
  
  /**
   * Returns the credit sequence for the queue.
   */
  default Credits credits()
  {
    throw new IllegalStateException(getClass().getName());
  }

  /**
   * Subscriber callback to get the Credits for the pipe.
   */
  default void credits(Credits credits)
  {
  }
  
  /**
   * True if the pipe has been closed or cancelled.
   */
  default boolean isClosed()
  {
    return false;
  }
  
  static <T> Pipe<T> of(PipeHandler<T> handler)
  {
    return new PipeSubHandlerImpl<T>(handler);
  }

  interface PipeHandler<T>
  {
    void handle(T value, Throwable exn, boolean isCancel);
  }
}
