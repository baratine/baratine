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

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.baratine.pipe.Pipe.PipeHandler;
import io.baratine.pipe.PipeStatic.PipeImplSub;
import io.baratine.pipe.PipeStatic.PipeSubBuilderImpl;
import io.baratine.service.Result;
import io.baratine.service.ResultChain;

/**
 * {@code PipeSub} defines a subscriber for incoming messages. In a
 * simplest use case it can be specified as a lambda which handles the incoming
 * messages, exceptions, if fail is called by the publisher, or pipe close event.
 * <p>
 * To handle all of the three possible cases the PipeSub defines a handle() method
 * with three parameters &lt;T&gt;, Throwable and a boolean for pipe close event.
 */
@FunctionalInterface
public interface PipeSub<T> extends ResultChain<Void>
{
  //
  // caller/subscriber side
  //

  /**
   * The subscriber's {@code Pipe} handler will be registered as
   * the pipe consumer.
   *
   * @return Pipe pipe associated with the consumer
   */
  default Pipe<T> pipe()
  {
    return new PipeImplSub<>(this);
  }

  /**
   * Method handle should be implemented by the subscribers. The method
   * will be called when pipe receives new message, exception or a close event.
   * <p>
   * In the simplest case pipe can be specified as a lambda. Clients that
   * need more control over the flow should use pipe().
   *
   * @param value    incoming message
   * @param fail     exception
   * @param isClosed closed event
   */
  void handle(T value, Throwable fail, boolean isClosed);

  /**
   * Implementation for this method is empty and is only provided for fulfilling
   * functional interface requirement of having only one abstract method.
   *
   * @param value ok value e.g. null
   */
  @Override
  default void ok(Void value)
  {
  }

  /**
   * Makes a call to method handle with the following values:<br>
   * null – for message (next)
   *
   * @param exn exception to pass to the handle method
   */
  @Override
  default void fail(Throwable exn)
  {
    handle(null, exn, false);
  }

  default <R> Result<R> then(BiConsumer<R,PipeSub<T>> consumer)
  {
    return ResultChain.then(this, consumer);
  }

  /**
   * The prefetch size.
   * <p>
   * Prefetch automatically manages the credits available to the sender.
   * <p>
   * If {@code PREFETCH_DISABLE} is returned, use the credits instead.
   */
  default int prefetch()
  {
    return Pipe.PREFETCH_DEFAULT;
  }

  /**
   * The initial number of credits. Can be zero if no initial credits.
   * <p>
   * To enable credits and disable the prefetch queue, return a non-negative
   * value.
   * <p>
   * If {@code CREDIT_DISABLE} is returned, use the prefetch instead. This
   * is the default behavior.
   *
   * @return
   */
  default long creditsInitial()
  {
    return Pipe.CREDIT_DISABLE;
  }

  /**
   * Returns subscriber requested capacity for the queue
   * <p>
   * Final capacity is calculated based on the values supplied for credits
   * and capacity
   *
   * @return requested queue capacity
   */
  default int capacity()
  {
    return 0;
  }

  /**
   * Creates new instance of PipeSubBuilder which uses supplied pipe as a message
   * consumer.
   *
   * @param pipe target pipe to relay messages to
   * @param <T> type of the message
   * @return this instance of PipeSubBuilder for chaining calls
   */
  static <T> PipeSubBuilder<T> of(Pipe<T> pipe)
  {
    return new PipeSubBuilderImpl<>(pipe);
  }

  /**
   * Creates a new instance of PipeSubBuilder that will use a given consumer
   * to consume messages
   *
   * @param next
   * @param <T>
   * @return this instance of PipeSubBuilder for chaining calls
   */
  static <T> PipeSubBuilder<T> of(Consumer<T> next)
  {
    return new PipeSubBuilderImpl<>(next);
  }

  /**
   * Builds a pipe using supplied PipeHandler implementation.
   *
   * @param handler message handler for the pipe
   * @param <T>     type of the message
   * @return this instance of PipeSubBuilder for further configuration
   */
  static <T> PipeSubBuilder<T> of(PipeHandler<T> handler)
  {
    return of(Pipe.of(handler));
  }

  /**
   * PipeSubBuilder provides a builder for composing a subscriber by
   * configuring available properties of the pipe.
   *
   * @param <T>
   */
  interface PipeSubBuilder<T> extends PipeSub<T>
  {
    /**
     * Method ok configures a consumer for event signifying that
     * pipe subscription had successfully completed.
     *
     * @param onOkSubscription consumer for pipe connect event
     * @return this instance of PipeSubBuilder for chaining calls
     */
    PipeSubBuilder<T> ok(Consumer<Void> onOkSubscription);

    /**
     * Method fail configures a consumer for exceptions that may arise
     *
     * @param onFail consumer for exception / exception handler
     * @return this instance of PipeSubBuilder for chaining calls
     */
    PipeSubBuilder<T> fail(Consumer<Throwable> onFail);

    /**
     * Method close configures consumer for close event on a pipe.
     *
     * @param onClose consumer for close event
     * @return this instance of PipeSubBuilder for chaining calls
     */
    PipeSubBuilder<T> close(Runnable onClose);

    /**
     * Method configures consumer for Credits object representing flow
     * control mechanism. Using credits rate at which sender sends messages
     * can be controlled.
     *
     * @param onCredits consumer for credits object
     * @return this instance of PipeSubBuilder for chaining calls
     */
    PipeSubBuilder<T> credits(Consumer<Credits> onCredits);

    /**
     * Method configures initial credits value
     *
     * @param initialCredit initial credits
     * @return this instance of PipeSubBuilder for chaining calls
     */
    PipeSubBuilder<T> credits(long initialCredit);

    /**
     * @param prefetch
     * @return this instance of PipeSubBuilder for chaining calls
     */
    PipeSubBuilder<T> prefetch(int prefetch);

    /**
     * Specifies capacity for the queue
     * <p>
     * Final capacity is calculated based on the values supplied for credits
     * and capacity
     *
     * @param size
     * @return this instance of PipeSubBuilder for chaining calls
     */
    PipeSubBuilder<T> capacity(int size);

    /**
     * @param creditsNext
     * @return
     */
    PipeSub<T> chain(Credits creditsNext);
  }
}
