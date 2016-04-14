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
import java.util.function.Consumer;
import java.util.function.Function;

import io.baratine.function.TriConsumer;
import io.baratine.service.ResultImpl.AdapterMake;
import io.baratine.service.ResultImpl.ResultJoinBuilder;

/**
 * Result is a continuation callback for async service calls with a primary 
 * result filled by  * <code>ok(value)</code> oran exception return filled by
 * <code>fail(exception)</code>
 *   
 * Since Result is designed as a lambda @FunctionalInterface interface,
 * clients can use simple lambda expressions to process the results.
 * 
 * For services that call other services, Results can be chained to simplify
 * return value processing.
 * 
 * <br > <br >
 * Sample client usage:
 * <blockquote><pre>
 *    Services services = Services.newManager().get();
 *    
 *    MyService service = manager.service(new MyServiceImpl())
 *                               .as(MyService.class);
 *    
 *    // JDK8 lambda for handle callback
 *    service.hello((x,e)-&gt;System.out.println("Result: " + x));
 *    
 *    // JDK8 lambda with builder
 *    service.hello(Result.onFail(e-&gt;System.out.println("Exception: " + e))
 *                        .onOk(x-&gt;System.out.println("Result: " + x));
 *    // Explicit result
 *    service.hello(new MyHelloResult());
 *    
 *    // result chaining with function
 *    service.hello(result.of(x-&gt;"[" + x + "]"));
 *    
 *    // result chaining with consumer
 *    service.hello(result.of((x,r)-&gt;r.ok("[" + x + "]")));
 *    
 *    // result fork/join
 *    Result.Fork&lt;String,String&gt; fork = result.fork();
 *    service1.hello(fork.branch());
 *    service2.hello(fork.branch());
 *    fork.onFail((x,e,r)-&gt;r.ok("fork-fail: " + x + " " + e));
 *    fork.join((x,r)-&gt;r.ok("fork-result: " + x));
 *    ...
 *    public class MyResultHandler implements Result&lt;MyDomainObject&gt; {
 *        ...
 *      &#64;Override
 *      public void handle(MyDomainObject value, Throwable exn)
        {
          if (exn != null) {
            exn.printStackTrace();
            return;
          }
          
 *        map.put(value.name, value.value);
 *        
 *        store.add(value);
 *      }
 * </pre></blockquote>
 * 
 * Sample service usage:
 * <blockquote><pre>
 * void hello(Result&lt;String&gt; result)
 * {
 *   result.ok("Hello, world");
 * }
 * </pre></blockquote>
 * 
 * Chaining:
 * <blockquote><pre>
 * void doRouter(Result&lt;String&gt; result)
 * {
 *   HelloService hello = ...;
 *   
 *   hello.hello(result.of(x-&gt;"Hello: " + x));
 * }
 * </pre></blockquote>
 * 
 */
@FunctionalInterface
public interface Result<T> extends ResultChain<T>
{
  /**
   * Client handler for result values. The result will either contain
   * a value or a failure exception, but not both.
   * 
   * The service will call <code>ok</code> or <code>fail</code>. The client 
   * will receive a handle callback.
   * 
   * Service:
   * <pre><code>
   * void hello(Result&lt;String&gt; result)
   * {
   *   result.ok("Hello, world");
   * }
   * </code></pre>
   * 
   * Client:
   * <pre><code>
   * hello.hello((x,e)-&gt;System.out.println("Hello: " + x + " " + e));
   * </code></pre>
   * 
   * @param value the result value
   * @param fail the result failure exception
   */
  void handle(T value, Throwable fail)
    throws Exception;
  
  /**
   * Completes the Result with its value. Services call complete to finish
   * the response.
   * 
   * Service:
   * <pre><code>
   * void hello(Result&lt;String&gt; result)
   * {
   *   result.ok("Hello, world");
   * }
   * </code></pre>
   * 
   * Client:
   * <pre><code>
   * hello.hello((x,e)-&gt;System.out.println("Hello: " + x));
   * </code></pre>
   * 
   * @param result the result value
   */
  @Override
  default void ok(T result)
  {
    try {
      handle(result, null);
    } catch (Exception e) {
      fail(e);
    }
  }
  
  /**
   * Fails the Result with an exception. The exception will be passed to
   * the calling client.
   * 
   * @param exn the exception
   */
  @Override
  default void fail(Throwable exn)
  {
    try {
      handle(null, exn);
    } catch (Exception e) {
      throw new ServiceExceptionExecution(e);
    }
  }
  
  /**
   * Create an empty Result that ignores the <code>ok</code>.
   */
  static <T> Result<T> ignore()
  {
    return ResultImpl.Ignore.create();
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
  /*
  @Override
  default <R extends T> Result<R> of()
  {
    return of(this, x->x);
  }
  */

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
  /*
  default <U> Result<U> of(Function<U,T> fun)
  {
    if (isFuture()) {
      return new ResultChainFunFuture<U,T>(this, fun);
    }
    else {
      return new ResultChainFun<U,T>(this, fun);
    }
  }
    */

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
  /*
  default <U> Result<U> of(Function<U,T> fun,
                           BiConsumer<Throwable,Result<T>> exnHandler)
  {
    if (isFuture()) {
      return new ChainResultFunFutureExn<U,T>(this, fun, exnHandler);
    }
    else {
      return new ChainResultFunExn<U,T>(this, fun, exnHandler);
    }
  }
  */
  
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
  default <R> Result<R> then(BiConsumer<R,Result<T>> consumer)
  {
    return ResultChain.then(this, consumer);
    /*
    if (isFuture()) {
      return new ChainResultAsync<U,T,Result<T>>(this, consumer);
    }
    else {
      return new ChainResult<U,T,Result<T>>(this, consumer);
    }
    */
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
  /*
  static <T,U,R extends Result<T>> Result<U> then(R result, BiConsumer<U,R> consumer)
  {
    if (result.isFuture()) {
      return new ResultThenFuture<U,T,R>(result, consumer);
    }
    else {
      return new ResultThen<U,T,R>(result, consumer);
    }
  }
  */
  
  /**
   * Creates a Result as a pair of lambda consumers, one to process normal
   * results, and one to handle exceptions.
   * 
   * <pre><code>
   * hello.hello(Result.of(e-&gt;System.out.println("exception: " + e))
   *                   .onOk(x-&gt;System.out.println("result: " + x));
   *                         
   * </code></pre>
   *
   * @param result a consumer to handle a normal result value.
   * @param exn a consumer to handle an exception result
   * 
   * @return the constructed Result
   */
  static <T> Result<T> of(Consumer<T> result, Consumer<Throwable> exn)
  {
    return new AdapterMake<T>(result, exn);
  }

  static <T> Result<T> onOk(Consumer<T> consumer)
  {
    Objects.requireNonNull(consumer);
    
    return new ResultImpl.ResultBuilder<>(consumer, null);
  }

  static <T> Builder<T> onFail(Consumer<Throwable> fail)
  {
    Objects.requireNonNull(fail);
    
    return new ResultImpl.ResultBuilder<>(null, fail);
  }
  
  interface Builder<U>
  {
    Result<U> onOk(Consumer<U> consumer);
  }
  
  /**
   * <pre><code>
   * Result.Fork&lt;String,String&gt; fork = result.fork();
   * 
   * service1.hello(fork.branch());
   * service2.hello(fork.branch());
   * 
   * fork.join(x-&gt;System.out.println("Fork: " + x));
   * </code></pre>
   * 
   * @return fork/join builder
   */
  
  default <U> Fork<U,T> fork()
  {
    return new ResultJoinBuilder<>(this);
  }
  
  //
  // internal methods for managing future results
  //

  /*
  default boolean isFuture()
  {
    return false;
  }
  
  default void completeFuture(T value)
  {
    throw new IllegalStateException(getClass().getName());
  }
  
  default <U> void completeFuture(Result<U> result, U value)
  {
    throw new IllegalStateException(getClass().getName());
  }
  */
  
  public interface Fork<U,T>
  {
    <V extends U> Result<V> branch();
    
    Fork<U,T> fail(TriConsumer<List<U>,List<Throwable>,Result<T>> fails);
    
    void join(Function<List<U>,T> combiner);
    
    void join(BiConsumer<List<U>,Result<T>> combiner);
  }
  
  abstract public static class Wrapper<R,T> extends WrapperChain<R,T,ResultChain<T>>
  {
    protected Wrapper(ResultChain<T> delegate)
    {
      super(delegate);
    }
  }
}
