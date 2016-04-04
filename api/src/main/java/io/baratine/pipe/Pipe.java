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
import io.baratine.pipe.PipeStatic.PipeOutResultImpl;
import io.baratine.pipe.PipeStatic.ResultPipeInHandlerImpl;
import io.baratine.pipe.PipeStatic.ResultPipeInImpl;
import io.baratine.service.Cancel;
import io.baratine.service.Result;

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

  public static <T> PipeOutBuilder<T> out(Result<Pipe<T>> result)
  {
    return new PipeOutResultImpl<>(result);
  }
  
  public static <T> PipeOutBuilder<T> out(Function<Pipe<T>,OnAvailable> onOk)
  {
    return new PipeOutResultImpl<>(onOk);
  }
  
  public static <T> PipeOutBuilder<T> out(OnAvailable flow)
  {
    return new PipeOutResultImpl<>(flow);
  }
  
  public static <T> PipeInBuilder<T> in(Pipe<T> pipe)
  {
    return new ResultPipeInImpl<>(pipe);
  }
  
  public static <T> PipeInBuilder<T> in(Consumer<T> next)
  {
    return new ResultPipeInImpl<>(next);
  }
  
  public static <T> Pipe<T> in(InHandler<T> handler)
  {
    return new ResultPipeInHandlerImpl<T>(handler);
  }
  
  public interface InHandler<T>
  {
    void handle(T next, Throwable exn, boolean isCancel);
  }
  
  public interface PipeOutBuilder<T> extends ResultPipeOut<T>
  {
    PipeOutBuilder<T> flow(OnAvailable flow);
    PipeOutBuilder<T> fail(Consumer<Throwable> onFail);
  }
  
  public interface PipeInBuilder<T> extends ResultPipeIn<T>
  {
    PipeInBuilder<T> ok(Consumer<Void> onOkSubscription);
    
    PipeInBuilder<T> fail(Consumer<Throwable> onFail);
    PipeInBuilder<T> close(Runnable onClose);
    
    PipeInBuilder<T> credits(long initialCredit);
    
    PipeInBuilder<T> prefetch(int prefetch);
    
    PipeInBuilder<T> capacity(int size);
    
    ResultPipeIn<T> chain(Credits creditsNext);
  }
  
  
  /**
   * {@code FlowIn} controls the pipe credits from the subscriber
   */
  /*
  public interface FlowIn<T> extends Credits, Cancel
  {
  }
  */
  
  /**
   * {@code FlowOut} is a callback to wake the publisher when credits are
   * available for the pipe.
   * 
   * Called after the publisher would block, calculated as when the number
   * of {@code OutPipe.next()} calls match a previous {@code OutPipe.credits()}.
   */
  /*
  public interface FlowOut<T>
  {
    void ready(T pipe);
    
    default void fail(Throwable exn)
    {
    }
    
    default void cancel()
    {
    }
  }
  */
}
