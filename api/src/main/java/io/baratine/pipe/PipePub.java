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

import java.util.function.Consumer;
import java.util.function.Function;

import io.baratine.pipe.Credits.OnAvailable;
import io.baratine.pipe.PipeStatic.PipePubImpl;
import io.baratine.service.Result;
import io.baratine.service.ResultChain;
import io.baratine.service.ServiceException;

/**
 * {@code PipePub} is used to obtain a source of a pipe. Once it is obtained
 * publishing of the messages can start
 * <p>
 * <p>
 * <blockquote>
 * <pre>
 *
 * </pre>
 * </blockquote>
 */
@FunctionalInterface
public interface PipePub<T> extends ResultChain<Pipe<T>>
{
  /**
   * The method is called on successful establishing of the pipe. Pipe obtained
   * from the pipe parameter is ready to be used for publishing
   *
   * @param pipe pipe for publishing
   * @param exn  remote exception signifying failure to establish the pipe
   * @throws Exception client exception
   */
  void handle(Pipe<T> pipe, Throwable exn) throws Exception;

  /**
   * Sets prefetch size
   * <p>
   * Prefetch automatically manages the credits available to the sender.
   * <p>
   * If {@code PREFETCH_DISABLE} is returned, use the credits instead.
   *
   * @param prefetch number of messages to prefetch
   * @return existing instance this PipePub
   */
  default PipePub<T> prefetch(int prefetch)
  {
    throw new UnsupportedOperationException(getClass().getName());
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
   * @param credits
   * @return
   */
  default PipePub<T> credits(long credits)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Sets queue capacity
   *
   * @param capacity
   */
  default void capacity(int capacity)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Calls method handle upon successful establishing of the pipe passing
   * the pipe and null for exception as arguments to the handle method call.
   *
   * @param pipe established pipe
   */
  @Override
  default void ok(Pipe<T> pipe)
  {
    try {
      handle(pipe, null);
    } catch (Throwable e) {
      fail(e);
    }
  }

  /**
   * Calls method handle upon failure to establish a pipe passing null and
   * exception as arguments to the handle method call.
   *
   * @param exn exception that prevented establishign of a pipe
   */
  @Override
  default void fail(Throwable exn)
  {
    try {
      handle(null, exn);
    } catch (RuntimeException e) {
      throw e;
    } catch (Error e) {
      throw e;
    } catch (Throwable e) {
      throw ServiceException.createAndRethrow(e);
    }
  }

  /**
   * Creates an instance of PipePubBuilder with an instance of Result which will
   * asynchronously receive initialized instance of a Pipe
   *
   * @param result future for receiving initialized pipe
   * @param <T> type of the messages
   * @return instance PipePubBuilder
   */
  static <T> PipePubBuilder<T> of(Result<Pipe<T>> result)
  {
    return new PipePubImpl<>(result);
  }

  static <T> PipePubBuilder<T> of(Function<Pipe<T>,OnAvailable> onOk)
  {
    return new PipePubImpl<>(onOk);
  }

  static <T> PipePubBuilder<T> out(OnAvailable flow)
  {
    return new PipePubImpl<>(flow);
  }

  /**
   * PipePubBuilder is a builder for PipeSub
   *
   * @param <T> type of the messages
   */
  interface PipePubBuilder<T> extends PipePub<T>
  {
    /**
     * Configures an instance of PipePubBuilder supplying it with an instance
     * of OnAvailable implementation
     *
     * @param flow instance of OnAvailable
     * @return instance of PipePubBuilder for chaining calls
     * @see OnAvailable
     */
    PipePubBuilder<T> flow(OnAvailable flow);

    /**
     * Configures an instance of PipePubBuilder supplying it with an instance
     * of a exception handler specified as a Consumer for exceptions
     *
     * @param onFail consumer for exceptions
     * @return instance of PipePubBuilder for chaining calls
     * @see Consumer
     */
    PipePubBuilder<T> fail(Consumer<Throwable> onFail);
  }
}
