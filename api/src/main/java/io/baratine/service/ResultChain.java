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

package io.baratine.service;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import io.baratine.function.TriConsumer;
import io.baratine.service.ResultImpl.ChainResultFunExn;
import io.baratine.service.ResultImpl.ChainResultFunFutureExn;
import io.baratine.service.ResultImpl.ResultChainFun;
import io.baratine.service.ResultImpl.ResultChainFunFuture;
import io.baratine.service.ResultImpl.ResultThen;
import io.baratine.service.ResultImpl.ResultThenFuture;

/**
 * ResultChainable is a base type for chainable results.
 */
public interface ResultChain<T>
{
  void ok(T result);
  
  void fail(Throwable exn);

  /**
   * Creates a chained result.
   * 
   * <pre><code>
   * void myMiddle(Result&lt;String&gt; result)
   * {
   *   MyLeafService leaf = ...;
   *   
   *   leaf.myLeaf(result.of());
   * }
   * </code></pre>
   */
  default <R extends T> Result<R> of()
  {
    return of(this, x->x);
  }

  /**
   * Creates a chained result.
   * 
   * <pre><code>
   * void myMiddle(Result&lt;String&gt; result)
   * {
   *   MyLeafService leaf = ...;
   *   
   *   leaf.myLeaf(result.of());
   * }
   * </code></pre>
   */
  default <R> Result<R> of(Function<R,T> fun)
  {
    return of(this, fun);
  }

  /**
   * Creates a composed result that will receive its completed value from
   * a function. The function's value will become the
   * result's complete value.
   * 
   * <pre><code>
   * void myMiddle(Result&lt;String&gt; result)
   * {
   *   MyLeafService leaf = ...;
   *   
   *   leaf.myLeaf(result.of(v-&gt;"Leaf: " + v));
   * }
   * </code></pre>
   */
  static <R,T,C extends ResultChain<T>> 
  Result<R> of(C result, Function<R,T> fun)
  {
    if (result.isFuture()) {
      return new ResultChainFunFuture<>(result, fun);
    }
    else {
      return new ResultChainFun<>(result, fun);
    }
  }

  /**
   * Creates a composed result that will receive its completed value from
   * a function. The function's value will become the
   * result's complete value.
   * 
   * <pre><code>
   * void myMiddle(Result&lt;String&gt; result)
   * {
   *   MyLeafService leaf = ...;
   *   
   *   leaf.myLeaf(result.of(v-&gt;"Leaf: " + v, 
   *                         (e,r)-&gt;{ e.printStackTrace(); r.fail(e); }));
   * }
   * </code></pre>
   */
  static <T,U,R extends ResultChain<U>>
  Result<T> of(R next,
               Function<T,U> fun,
               BiConsumer<Throwable,R> exnHandler)
  {
    if (next.isFuture()) {
      return new ChainResultFunFutureExn<>(next, fun, exnHandler);
    }
    else {
      return new ChainResultFunExn<>(next, fun, exnHandler);
    }
  }
  
  /**
   * Creates a chained result for calling an internal
   * service from another service. The lambda expression will complete
   * the original result.
   * 
   * <pre><code>
   * void myMiddle(Result&lt;String&gt; result)
   * {
   *   MyLeafService leaf = ...;
   *   
   *   leaf.myLeaf(result.of((v,r)-&gt;r.ok("Leaf: " + v)));
   * }
   * </code></pre>
   */
  static <R,T,C extends ResultChain<T>>
  Result<R> then(C next, BiConsumer<R,C> consumer)
  {
    if (next.isFuture()) {
      return new ResultThenFuture<>(next, consumer);
    }
    else {
      return new ResultThen<>(next, consumer);
    }
  }
  
  //
  // internal methods for managing future results
  //

  default boolean isFuture()
  {
    return false;
  }
  
  default void completeFuture(T value)
  {
    throw new IllegalStateException(getClass().getName());
  }
  
  default <R> void completeFuture(ResultChain<R> result, R value)
  {
    throw new IllegalStateException(getClass().getName());
  }
  
  public interface ForkChain<R,T,C extends ResultChain<T>>
  {
    <V extends R> Result<V> branch();
    
    ForkChain<R,T,C> fail(TriConsumer<List<R>,List<Throwable>,C> fails);
    
    void join(Function<List<R>,T> combiner);
    
    void join(BiConsumer<List<R>,C> combiner);
  }
  
  abstract public class WrapperChain<T,U,R extends ResultChain<U>>
    implements Result<T>
  {
    private final R _delegate;
  
    protected WrapperChain(R delegate)
    {
      Objects.requireNonNull(delegate);
      
      _delegate = delegate;
    }
    
    @Override
    public boolean isFuture()
    {
      return _delegate.isFuture();
    }
    
    abstract public void ok(T value);
    
    @Override
    public <V> void completeFuture(ResultChain<V> result, V value)
    {
      _delegate.completeFuture(result, value);
    }
    
    @Override
    public void completeFuture(T value)
    {
      ok(value);
    }
    
    @Override
    public final void handle(T value, Throwable exn)
    {
      throw new UnsupportedOperationException(getClass().getName());
    }
    
    @Override
    public void fail(Throwable exn)
    {
      delegate().fail(exn);
    }
    
    protected R delegate()
    {
      return _delegate;
    }
  
    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + delegate() + "]";
    }
  }
}
