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

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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
   * Publishers the {@code FlowOut} callback when credits may be available
   * for the pipe.
   */
  default void flow(FlowOut<Pipe<T>> flow)
  {
    throw new IllegalStateException(getClass().getName());
  }
  
  /**
   * Publisher timeout when not using {@code FlowOut}.
   */
  default void flowTimeout(long time, TimeUnit unit)
  {
    throw new IllegalStateException(getClass().getName());
  }
  
  default FlowIn<Pipe<T>> flow()
  {
    throw new IllegalStateException(getClass().getName());
  }
  
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
  default void flow(FlowIn<Pipe<T>> flow)
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
  default long creditsInitial()
  {
    return CREDIT_DISABLE;
  }
  
  default int capacity()
  {
    return 0;
  }
  
  public static <T> PipeOutBuilder<T> out(Result<Pipe<T>> result)
  {
    return new PipeOutResultImpl<>(result);
  }
  
  public static <T> PipeOutBuilder<T> out(Consumer<Pipe<T>> onOk)
  {
    return new PipeOutResultImpl<>(onOk);
  }
  
  public static <T> PipeOutBuilder<T> out(FlowOut<Pipe<T>> flow)
  {
    return new PipeOutResultImpl<>(flow);
  }
  
  public static <T> PipeInBuilder<T> in(Pipe<T> pipe)
  {
    return new ResultPipeInImpl<>(pipe);
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
    PipeOutBuilder<T> flow(FlowOut<Pipe<T>> flow);
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
    
    ResultPipeIn<T> chain(FlowIn<Pipe<T>> flowNext);
  }
  
  
  /**
   * {@code FlowIn} controls the pipe credits from the subscriber
   */
  public interface FlowIn<T> extends Cancel
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
    
    /**
     * Adds credits.
     * 
     * Convenience method based on the {@code credits} methods.
     */
    
    default void addCredits(int newCredits)
    {
      credits(credits() + newCredits);
    }
    
    int available();
    
    void flow(FlowOut<T> flow);
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
    void ready(T pipe);
    
    default void fail(Throwable exn)
    {
    }
    
    default void cancel()
    {
    }
  }
}
