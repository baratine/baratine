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
 * Interface {@code Pipe} is a unidirectional queue between two services. Pipe
 * is created by the subscriber and connects to the publishing service.
 * <p>
 * Register a Pipe with e.g.
 * <p>
 * <blockquote>
 * <pre>
 *   &#64;Service
 *   public class QuoteService {
 *     public void connect(PipeSub&lt;String&gt; subscription) {
 *
 *     }
 *   }
 *   </pre>
 * </blockquote>
 */
public interface Pipe<T>
{
  public static final int PREFETCH_DEFAULT = 0;
  public static final int PREFETCH_DISABLE = -1;

  public static final int CREDIT_DISABLE = -1;

  /**
   * Supplies the next value. Method will block if no credits are available and
   * remain blocked until the credits are available or until timeout specified
   * with Credits.offerTimeout expires. On expiring timeout method will throw
   * IllegalStateException.
   *
   * @param value next value
   */
  void next(T value);

  /**
   * Completes sending the values to the client and signals to the client
   * that no more values are expected.
   */
  void close();

  /**
   * Signals a failure.
   * <p>
   * The pipe is closed on failure.
   *
   * @param exn sends exception to the pipe
   */
  void fail(Throwable exn);

  /**
   * Returns the credit sequence for the queue.
   *
   * @return instance of Credits
   */
  default Credits credits()
  {
    throw new IllegalStateException(getClass().getName());
  }

  /**
   * Subscriber callback to get the Credits for the pipe.
   *
   * @param credits instance of pipe's credits
   */
  default void credits(Credits credits)
  {
  }

  /**
   * True if the pipe has been closed or cancelled.
   *
   * @return true if closed
   */
  default boolean isClosed()
  {
    return false;
  }

  /**
   * Creates instance of a Pipe backed by a PipeHandler. Messages, exceptions and
   * close events arriving via a pipe will relay to the supplied instance of PipeHandler.
   *
   * @param handler instance for handling messages and other pipe events
   * @param <T>     type of message
   * @return instance of a pipe
   */
  static <T> Pipe<T> of(PipeHandler<T> handler)
  {
    return new PipeSubHandlerImpl<T>(handler);
  }

  /**
   * Interface PipeHandler provides contract for pipe message handler.
   *
   * @param <T>
   */
  interface PipeHandler<T>
  {
    /**
     * Method handle is called when associated pipe receives new messages,
     * exceptions or close events.
     *
     * @param value    message
     * @param exn      exception e.g. pipe.fail(new RuntimeException());
     * @param isCancel if true pipe is closed
     */
    void handle(T value, Throwable exn, boolean isCancel);
  }
}
