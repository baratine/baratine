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

import io.baratine.service.Cancel;

/**
 * {@code Pipe} sends a sequence of values from a source to a sink.
 */
public interface Pipe<T>
{
  public static final int PREFETCH_DEFAULT = 0;
  public static final int PREFETCH_DISABLE = -1;
  
  public static final int CREDIT_DISABLE = -1;
  
  /**
   * Supplies the next value.
   * 
   * @param value
   */
  void next(T value);

  /**
   * Completes sending the values to the client and signals to the client
   * that no more values are expected.
   */
  void close();

  /**
   * Signals a failure to the client passing exception.
   * @param exn
   */
  void fail(Throwable exn);
  
  /**
   * Returns the available credits in the queue.
   */
  default int available()
  {
    throw new IllegalStateException(getClass().getName());
  }
  
  /**
   * Returns the credit sequence for the queue.
   */
  default long credits()
  {
    throw new IllegalStateException(getClass().getName());
  }
  
  /**
   * True if the stream has been cancelled by the reader.
   *
   * @return true if cancelled
   */
  default boolean isClosed()
  {
    return false;
  }
  
  /**
   * Accept the {@code Flow} object for finer flow control.
   * 
   * The {@code Flow} object can pause the prefetch, or add credits
   * manually when the credit system is used. 
   */
  default void flow(FlowIn flow)
  {
  }
  
  /**
   * The prefetch size.
   * 
   * Prefetch automatically manages the credits available to the sender.
   * 
   * If {@code PREFETCH_DISABLE} is returned, use the credits instead. 
   */
  default int prefetch()
  {
    return PREFETCH_DEFAULT;
  }

  /**
   * The initial number of credits. Can be zero if no initial credits.
   * 
   * To enable credits and disable the prefetch queue, return a non-negative
   * value.
   * 
   * If {@code CREDIT_DISABLE} is returned, use the prefetch instead. This
   * is the default behavior. 
   */
  default int creditsInitial()
  {
    return CREDIT_DISABLE;
  }
  
  default int capacity()
  {
    return 0;
  }
  
  
  /**
   * {@code FlowIn} controls the pipe credits from the subscriber
   */
  public interface FlowIn extends Cancel
  {
    /**
     * Returns the current credit sequence.
     */
    long credits();
    
    /**
     * Sets the new credit sequence when prefetch is disabled. Used by 
     * applications that need finer control.
     * 
     * Applications using credit need to continually add credits.
     * 
     * @param creditSequence next credit in the sequence
     * 
     * @throws IllegalStateException if prefetch is used
     */
    void credits(long creditSequence);
  }
  /**
   * {@code FlowOut} is a callback to wake the publisher when credits are
   * available for the pipe.
   * 
   * Called after the publisher would block, calculated as when the number
   * of {@code OutPipe.next()} calls match a previous {@code OutPipe.credits()}.
   */
  public interface FlowOut<T>
  {
    void ready(Pipe<T> pipe);
    
    default void fail(Throwable exn)
    {
    }
    
    default void cancel()
    {
    }
  }
  
  public interface InHandler<T> {
    void handle(T next, Throwable exn, boolean isCancel);
  }
}
