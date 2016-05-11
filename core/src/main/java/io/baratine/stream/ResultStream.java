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

package io.baratine.stream;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

import io.baratine.service.Cancel;
import io.baratine.service.Result;
import io.baratine.stream.ResultStreamImpl.ResultAsync;


/**
 * Interface {@code ResultStream} is used as a sink for returning an unbounded
 * sequence of values from a service method.
 *
 * The interface is used as a parameter in implementation of a method
 * corresponding to a service interface method returning
 * {@code ResultStreamBuilder}.
 *
 * e.g.
 * <pre>
 * <code>
 *   public interface MyService {
 *     ResultStreamBuilder&lt;String&gt; results();
 *   }
 *
 *   &#64;io.baratine.core.Service("public://pod/service")
 *   public class MyServiceImpl implements MyService {
 *     public void results(ResultStream&lt;String&gt; stream) {
 *       stream.accept("Hello");
 *       stream.accept("World");
 *
 *       stream.complete();
 *     }
 *
 *     &#64;Override
 *     public void ResultStreamBuilder&lt;String&gt; results() {
 *       throw new UnsupportedOperationException();
 *     }
 *   }
 *
 * </code>
 * </pre>
 * <p>
 * Sending a new value can be accomplished with passing it to method {@code accept}.
 *
 * After the last value is submitted the service needs to call method {@code complete}
 * to signal the {@code ResultStream} that last value has been submitted}
 * <p>
 * The client obtaining the results from the corresponding {@code ResultStreamBuilder}
 * may choose to cancel further results. When {@code ResultStream} is cancelled
 * its cancelled state is set to true and method {@code onCancell(CancelHandle)}
 * is called. Method {@code isCancelled()} can be used to test if the stream
 * was cancelled to avoid sending values to a cancelled stream.
 *
 * <p>
 *
 * @see io.baratine.stream.ResultStreamBuilder
 */
@FunctionalInterface
public interface ResultStream<T> extends Consumer<T>, Serializable
{
  public static final int CREDIT_MAX = 1 << 24;

  /**
   * Method {@code start()} performs initial operations on preparing
   * an instance of {@code ResultStream} to accepting values. This method is
   * called internally by Baratine.
   */
  default void start() {}

  /**
   * Supplies next value into the client's {@code ResultStreamBuilder}
   * @param value
   */
  @Override
  default void accept(T value)
  {
    handle(value, null, false);
  }

  /**
   * Completes sending the values to the client and signals to the client
   * that no more values is expected.
   */
  default void ok()
  {
    handle(null, null, true);
  }

  /**
   * Signals a failure to the client passing exception.
   * @param exn
   */
  default void fail(Throwable exn)
  {
    handle(null, exn, false);
  }
  
  void handle(T value, Throwable exn, boolean isEnd);

  /**
   * Uses a {@code Supplier} to generate set of values and calls
   * method {@code complete()} on itself.
   *
   * A first null value returned by the {@code get()} method of the {@code Supplier}
   * completes accept() loop and calls complete() on the instance of {@code ResultStream}.
   *
   * @param supplier supplies value to be accepted by the instance of {@code ResultStream}
   */
  default void generate(Supplier<T> supplier)
  {
    T value;
    
    while (! isCancelled() && (value = supplier.get()) != null) {
      accept(value);
    }
    
    ok();
  }
  
  //
  // cancel methods
  //

  /**
   * Tests if the instance of {@code ResultStream} was cancelled.
   *
   * @return true if cancelled, false otherwise.
   */
  default boolean isCancelled()
  {
    return false;
  }

  /**
   * A callback method that is called when the {@code ResultStream} is cancelled.
   *
   * @param cancel
   */
  default void onCancel(Cancel cancel)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  //
  // flow methods
  //
  
  /*
  default int getCredit()
  {
    return CREDIT_MAX;
  }
  
  default void onCreditAvailable(Runnable task)
  {
    task.run();
  }
  */
  
  default <U> Result<U> of(BiConsumer<U,ResultStream<T>> consumer)
  {
    return Result.of(x->consumer.accept(x,this), exn->fail(exn));
  }

  /**
   * @return
   */
  default boolean isFuture()
  {
    return false;
  }
  
  default <U> void acceptFuture(ResultStream<U> result, 
                               Iterable<U> values, 
                               boolean isComplete)
  {
    for (U value : values) {
      result.accept(value);
    }
    
    if (isComplete) {
      result.ok();
    }
  }
  
  default ResultStream<T> flush()
  {
    return this;
  }
  
  default ResultStream<?> createJoin()
  {
    return this;
  }
  
  default ResultStream<T> createFork(ResultStream<Object> resultJoin)
  {
    return (ResultStream) resultJoin;
  }
  
  @SuppressWarnings("serial")
  abstract public static class Wrapper<U,V> implements ResultStream<U>
  {
    private final ResultStream<? super V> _next;
    
    public Wrapper(ResultStream<? super V> next)
    {
      Objects.requireNonNull(next);
      
      _next = next;
    }
    
    protected ResultStream<? super V> next()
    {
      return _next;
    }
    
    @Override
    public boolean isFuture()
    {
      return next().isFuture();
    }
    
    @Override
    public void start()
    {
      next().start();
    }
    
    @Override
    public void fail(Throwable e)
    {
      next().fail(e);
    }
    
    @Override
    abstract public void accept(U value);
    
    @Override
    public void ok()
    {
      next().ok();
    }
    
    @Override
    public void handle(U value, Throwable exn, boolean isEnd)
    {
      if (isEnd) {
        ok();
      }
      else if (exn != null) {
        fail(exn);
      }
      else {
        accept(value);
      }
    }
    
    //
    // cancel
    //
    
    @Override
    public boolean isCancelled()
    {
      return next().isCancelled();
    }
    
    @Override
    public void onCancel(Cancel cancel)
    {
      next().onCancel(cancel);
    }
    
    /*
    @Override
    public int getCredit()
    {
      return getNext().getCredit();
    }
    
    @Override
    public void onCreditAvailable(Runnable task)
    {
      if (getCredit() > 0) {
        task.run();
      }
      else {
        getNext().onCreditAvailable(task);
      }
    }
    */
    
    @Override
    public <S> void acceptFuture(ResultStream<S> result,
                                Iterable<S> values,
                                boolean isComplete)
    {
      next().acceptFuture(result, values, isComplete);
    }
    
    /*
    @Override
    public ResultStream<?> createReduce()
    {
      return getNext().createReduce();
    }
    
    @Override
    public ResultStream<U> createMap(ResultStream<Object> resultReduce)
    {
      ResultStream<V> nextMap = getNext().createMap(resultReduce);
      
      return new ResultStreamMap<U>(this, nextMap);
    }
    */
  }
  
  @SuppressWarnings("serial")
  abstract public static class ResultWrapper<U,R> extends Base<U>
  {
    private final Result<? super R> _result;
    
    public ResultWrapper(Result<? super R> result)
    {
      Objects.requireNonNull(result);
      
      _result = result;
    }
    
    protected Result<? super R> getNext()
    {
      return _result;
    }
    
    @Override
    public boolean isFuture()
    {
      return getNext().isFuture();
    }
    
    @Override
    public void fail(Throwable e)
    {
      getNext().fail(e);
    }
    
    @Override
    public <S> void acceptFuture(ResultStream<S> result,
                                Iterable<S> values,
                                boolean isComplete)
    {
      // async result support for blocking futures
      getNext().completeFuture(new ResultAsync<S>(result, values, isComplete),
                                null);
    }
  }
  
  @SuppressWarnings("serial")
  abstract static class Base<V> implements ResultStream<V>, Cancel
  {
    private transient boolean _isCancelled;
    private transient Cancel _cancel;
    
    @Override
    public boolean isCancelled()
    {
      return _isCancelled;
    }
    
    @Override
    public void cancel()
    {
      if (! _isCancelled) {
        _isCancelled = true;
        
        Cancel cancel = _cancel;
        
        if (cancel != null) {
          _cancel= null;
          
          cancel.cancel();
        }
      }
    }
    
    @Override
    public void onCancel(Cancel cancel)
    {
      Objects.requireNonNull(cancel);
      
      if (_isCancelled) {
        cancel.cancel();
      }
      else if (_cancel != null) {
        throw new IllegalStateException("Cancel is already assigned");
      }
      else {
        _cancel = cancel;
      }
    }
    
    @Override
    public void handle(V value, Throwable exn, boolean isEnd)
    {
      if (isEnd) {
        ok();
      }
      else if (exn != null) {
        fail(exn);
      }
      else {
        accept(value);
      }
    }
  }
  
  @SuppressWarnings("serial")
  public static class ForEach<V> extends Base<V> {
    private final Consumer<V> _accept;
    private final Runnable _complete;
    private final Consumer<Throwable> _fail;
    
    public ForEach(Consumer<V> accept, 
                   Runnable complete, 
                   Consumer<Throwable> fail)
    {
      Objects.requireNonNull(accept);
      Objects.requireNonNull(complete);
      Objects.requireNonNull(fail);
      
      _accept = accept;
      _complete = complete;
      _fail = fail;
    }
    
    public ForEach(Consumer<V> accept, 
                   Runnable complete)
    {
      Objects.requireNonNull(accept);
      Objects.requireNonNull(complete);
      
      _accept = accept;
      _complete = complete;
      _fail = null;
    }
    
    public ForEach(Consumer<V> accept)
    {
      Objects.requireNonNull(accept);
      
      _accept = accept;
      _complete = null;
      _fail = null;
    }
    
    @Override
    public void accept(V value)
    {
      _accept.accept(value);
    }
    
    @Override
    public void ok()
    {
      if (_complete != null) {
        _complete.run();
      }
    }
    
    @Override
    public void fail(Throwable e)
    {
      if (_fail != null) {
        _fail.accept(e);
      }
      else {
        super.fail(e);
      }
    }
  }
}
