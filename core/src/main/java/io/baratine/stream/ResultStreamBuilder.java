/*
 * Copyright (c) 1998-2015 Caucho Technology -- all rights reserved
 *
 * This file is part of Baratine(TM)
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

import io.baratine.function.BiConsumerSync;
import io.baratine.function.BiFunctionSync;
import io.baratine.function.BinaryOperatorSync;
import io.baratine.function.ConsumerAsync;
import io.baratine.function.ConsumerSync;
import io.baratine.function.FunctionAsync;
import io.baratine.function.FunctionSync;
import io.baratine.function.PredicateAsync;
import io.baratine.function.PredicateSync;
import io.baratine.function.SupplierSync;
import io.baratine.service.Cancel;
import io.baratine.service.Result;

import java.util.stream.Stream;

/**
 * Interface {@code ResultStreamBuilder} provides access to a sequence of
 * elements supplied by a remote service.
 * <p>
 * It supports a set of remote and local filtering, mapping and aggregate
 * operations.
 * <p>
 * For service methods that produce a stream of values {@code ResultStreamBuilder}
 * is used as a return type in the corresponding method declaration. e.g.
 * <p>
 * <pre>
 * <code>
 *   public interface Graduates {
 *     ResultStreamBuilder&lt;String&gt; streamNames(int year, char nameStartsWith);
 *   }
 * </code>
 * </pre>
 * <p>
 * A service implementing {@code Graduates} interface must provide a
 * distinct corresponding service method that fulfills obligation declared with
 * {@code ResultStreamBuilder&lt;String&gt; Graduates#streamNames(int, char)}.
 * <p>
 * The corresponding service method must have the same name and accept
 * the same list of parameters as the declaration.
 * <p>
 * In addition to the original parameters the method must accept a parameter of
 * type {@code ResultStream&lt;T&gt;} where T is the same type as the type in the
 * interface. By convention that parameter should be declared last.
 * <p>
 * e.g.
 * <p>
 * <pre>
 * <code>
 *   &#64;io.baratine.core.Service("public://pod/graduates")
 *   public class GraduatesImpl implements Graduates {
 *     public void streamNames(int year, char nameStartsWith, ResultStream&lt;String&gt; stream) {
 *       stream.accept("John Doe");
 *       stream.accept("John Oliver");
 *
 *       stream.complete();
 *     }
 *
 *     &#64;Override
 *     public void ResultStreamBuilder&lt;String&gt; streamNames(int year, char nameStartsWith) {
 *       throw new UnsupportedOperationException();
 *     }
 *   }
 * </code>
 * </pre>
 * <p>
 * <p>
 * Java implementation of the method required as part of the {@code implements}
 * contract can be empty, or since it's never actually used throw an
 * {@code UnsupportedOperationException}. Throwing the exception is prefered as
 * as part of good practice.
 * <p>
 * <p>
 * <p>
 * Client code working with a &#64;ResultStreamBuilder can process results using
 * methods provided by the interface.
 * <p>
 * <pre>
 * <code>
 *   public class Client {
 *
 *     &#64;javax.inject.Inject
 *     &#64;io.baratine.core.Lookup("public://pod/graduates")
 *     private Graduates service;
 *
 *     public void test() {
 *       ResultStreamBuilder&lt;String&gt; builder = service.streamNames(2000, 'J');
 *
 *       builder.forEach(s-&gt;System.out.println(s)).exec();
 *     }
 *   }
 * </code>
 * </pre>
 * <p>
 * Methods of the {@code ResultStreamBuilder} interface are designed to resemble
 * methods provided by the Stream class of the Java SE Streams. The important difference is that
 * ResultStreamBuilder works with remote services so methods will divide into
 * two groups: methods which are executed remotely and methods that are executed
 * by locally.
 * <p>
 * Methods from remote group will execute remotely on each of the
 * nodes participating in servicing a call providing {@code ResultStreamBuilder}.
 * <p>
 * Methods from local group will execute locally.
 * <p>
 * When invoking a remotely executed method the provided arguments are
 * serialized and re-instantiated on a remote node.
 * <p>
 * <p>
 * Executing mapping, filtering or reduction operation on a data-owning node will
 * provide better combined performance and / or load balancing. The main reason
 * for having the code execute remotely is to harness faster access to data
 * located on remote nodes.
 * <p>
 * Note: for description of Baratine distributed architecture see
 * <a href="http://baratine.io">http://baratine.io</a>.
 * <p>
 * <p>
 * When a method from remote group needs to be executed locally
 * {@code ResultStreamBuilder} needs to be adapted with a call to method
 * {@code ResultStreamBuilder<T> local()}. Call to {@code local} produces a stream
 * that will run all operations locally.
 * <p>
 * Methods from remote group can accept lambda expressions that will execute
 * remotely, on all the nodes of a pod (see http://baratine.io/ for description
 * of Baratine distributed architecture)
 * <p>
 * Baratine's ability to execute code remotely lands a hand to improving performance.
 * <p>
 * Whenever possible operations should take advantage of executing remotely if
 * processed data resides on remote servers or if processing remotely reduces
 * amount of data that travels across the network. Operations such as map, reduce
 * and filter can potentially all provide performance advantages if performed
 * on service nodes.
 * <p>
 * When the {@code ResultStreamBuilder} is filled in with all the needed
 * map, filter, collect, reduce and for each operations it is ready to
 * be executed via result(...) or exec() operations.
 *
 * @param <T> A type of item in resulting stream.
 * @see io.baratine.stream.ResultStream
 */

public interface ResultStreamBuilder<T>
{
  //
  // terminals
  //

  /**
   * Terminal that starts execution of the stream. The call is non-blocking;
   * the stream will execute after the call completes.
   * <p>
   * The CancelHandle can be used for subscription patterns, where cancel
   * unsubscribes the stream.
   * <p>
   * <pre>
   * <code>
   *   ResultStreamBuilder&lt;String&gt; stream = ...;
   *   stream.forEach(s -&gt; System.out.println(s)).exec();
   * </code>
   * </pre>
   *
   * @return a CancelHandle to cancel a stream.
   */
  Cancel exec();

  /**
   * Terminal that chains the new stream "to" an existing stream.
   *
   * @param result - an instance of ResultStream to funnel the results to.
   */
  void to(ResultStream<? super T> result);

  /**
   * Terminal that returns the last value of the stream.
   * <p>
   * Can be used as a completion callback for streams like <code>forEach </code>
   * that do not return values, or as a return value for streams that
   * calculate a final value like <code>collect</code> or <code>reduce</code>.
   *
   * @param result instance of Result to complete with the product of
   *               ResultStreamBuilder.
   */
  void result(Result<? super T> result);

  // foreach

  /**
   * Calls method accept() of the consumer on every item in the stream.
   * <p>
   * Operation is local
   *
   * @param consumer consumer to be called on every item in the stream
   * @return an instance of a Void ResultStreamBuilder ready for execution via
   * result() or exec() methods.
   */
  ResultStreamBuilder<Void> forEach(ConsumerSync<? super T> consumer);

  /**
   * Calls method accept() of the consumer on every item in the stream.
   * <p>
   * Operation is local
   *
   * @param consumer consumer to be called on every item in the stream
   * @return an instance of a Void ResultStreamBuilder ready for execution via
   * result() or exec() methods.
   */
  ResultStreamBuilder<Void> forEach(ConsumerAsync<? super T> consumer);

  /**
   * Method {@code first()} obtains the first item from every service node
   * and cancels execution of the corresponding instances of {@code ResultStream}
   * on all participating nodes.
   *
   * @return instance of {@code ResultStreamBuilder} ready for execution via
   * result() and exec() methods.
   */
  ResultStreamBuilder<T> first();

  /**
   * Creates an implementation of {@code ResultStreamBuilder} which ignores
   * returned values.
   *
   * @return an instance of a Void ResultStreamBuilder ready for execution via
   * result() and exec() methods.
   */
  ResultStreamBuilder<Void> ignore();

  /**
   * Reduce with a binary function. The operation is remote by default.
   * <p>
   * In the remote mode the reducing function is serialized and sent to all
   * nodes for execution. Every remote node will execute the function for every
   * item of their respective instance of {@code ResultStreamBuilder}
   * <p>
   * <pre><code>
   * s = t1;
   * s = op(s, t2);
   * s = op(s, t3);
   * result = s;
   * </code></pre>
   * <p>
   * <p>
   * <pre>
   * <code>
   *   ResultStreamBuilder&lt;Integer^gt; stream = ...;
   *
   *   ResultFuture&lt;Integer&gt; sum = new ResultFuture&lt;&gt;();
   *   stream.reduce((a, b) -&gt; a + b) //a) executed remotely
   *   .local()
   *   .reduce((a, b) -&gt; a + b) //b) executed locally
   *   .result(sum);
   * </code>
   * </pre>
   * <p>
   * Operation is remote by default
   *
   * @param op reduce binary function which accepts two arguments and returns a
   *           result of the same type
   * @return an instance of {@code ResultStreamBuilder} ready for execution
   * via result() and exec() methods.
   */
  ResultStreamBuilder<T> reduce(BinaryOperatorSync<T> op);

  /**
   * Reduce with a binary function and an initial value. The operation is remote
   * by default. The binary function serialized and sent to all nodes for
   * execution. It is re-instantiated on all nodes participating in the call
   * and called for every item in their respective instance of
   * {@code ResultStreamBuilder}
   * <p>
   * <pre><code>
   * s = init;
   * s = op(s, t1);
   * s = op(s, t2);
   * result = s;
   * </code></pre>
   * <p>
   * <pre>
   * <code>
   *   ResultStreamBuilder&lt;Integer&gt;stream = ...;
   *
   *   Result&lt;Future&lt;Integer&gt; sum = new ResultFuture&lt;^gt;();
   *   stream.reduce(0, (a, b) -&gt; a + b) //a) executed remotely
   *   .local()
   *   .reduce(0, (a, b) -&gt; a + b) //b) executed locally
   *   .result(sum);
   * </code>
   * </pre>
   * <p>
   * Note: in the example above reduce (a) will execute on a remote node
   * while b) will execute locally. Local reduce will operate on the sub-sums
   * provided by each individual node.
   * <p>
   * Operation is remote by default
   *
   * @param init initial value
   * @param op   binary function
   * @return an instance of ResultStreamBuilder ready for execution via
   * result() or exec() method.
   */
  ResultStreamBuilder<T> reduce(T init, BinaryOperatorSync<T> op);

  /**
   * Reduce with a binary function and an initial value and then combine results
   * using dedicated binary function. The operation is remote by default.
   * <p>
   * The accumulator binary function is serialized and sent to every participating
   * node where it is re-instantiated and called for every item in their
   * respective instance of {@code ResultStreamBuilder}
   * <p>
   * Combiner function is executed one time for a {@code ResultStreamBuilder}
   * which combines reduced values from every node.
   * <p>
   * <p>
   * <pre>
   * <code>
   *   ResultStreamBuilder&lt;Integer&gt; stream = ...;
   *
   *   ResultFuture&lt;Integer&gt; sum = new ResultFuture&lt;&gt;();
   *   stream.reduce(0, (a, b) -&gt; a + b, (x, y)-&gt; x + y)
   *   .result(sum);
   * </code>
   * </pre>
   * <p>
   * <p>
   * In the example above reduce (a + b) will execute in the context of each
   * individual node. At that point the node has a chance to reduce their
   * local results. Reduce (x + y) is executed once for all of the nodes to
   * combine results coming from the nodes.
   * <p>
   * Note: reduce (a + b) will execute on a remote node while (x + y) will
   * execute locally. Local reduce will operate on the sub-sums
   * provided by each individual node.
   * <p>
   * Operation is remote by default
   *
   * @param identity    initial value
   * @param accumulator accumulator function
   * @param combiner    combining function
   * @param <R>         type of the result
   * @return instance of {@code ResultStreamBuilder} ready for execution
   * via result() and exec() methods.
   */
  <R> ResultStreamBuilder<R> reduce(R identity,
                                    BiFunctionSync<R,? super T,R> accumulator,
                                    BinaryOperatorSync<R> combiner);

  /**
   * Method collect will use an accumulator and combiner functions to collect
   * results from nodes into a container provided by the Supplier function.
   * <p>
   * This operation is remote by default. In the remote mode the {@code init}
   * and {$code accum} functions will be serialized and sent to all participating
   * nodes. Re-instantiated supplier function (init) will be used to obtain an
   * instance of the container. Re-instantiated accumulator function will be
   * called on every item in node's respective ResultStreamBuilder.
   * <p>
   * The combiner will be executed once to combine results from participating
   * nodes.
   * e.g.
   * <p>
   * <pre>
   * <code>
   *   ResultStreamBuilder&lt;String&gt; stream = ...;
   *
   *   Result&lt;List&lt;String&gt;&gt; result = ...;
   *
   *   stream.collect(java.util.ArrayList&lt;String&gt;::new,
   *                  (l, e) -&gt; l.add(e),
   *                  (a, b) -&gt; a.addAll(b))
   *                  .result(result);
   * </code>
   * </pre>
   *
   * @param init     supplier of the container
   * @param accum    an accumulator function
   * @param combiner combiner funcation
   * @param <R>      type of the result
   * @return instance of {@code ResultStreamBuilder} ready for execution
   */
  <R> ResultStreamBuilder<R> collect(SupplierSync<R> init,
                                     BiConsumerSync<R,T> accum,
                                     BiConsumerSync<R,R> combiner);

  //
  // map/filter
  //

  /**
   * Method map produces a stream of values using provided {@code map} function.
   * <p>
   * The operation is remote by default.
   * <p>
   * In the remote mode the {@code map} function represented by the map
   * parameter is serialized and sent to every node participating in the call.
   * <p>
   * Re-instantiated map function is then called for every item in the node's
   * respective {@code ResultStreamBuilder}.
   * <p>
   * Operation is remote by default
   * <pre>
   * <code>
   *   ResultStreamBuilder&lt;String&gt; stream = ...;
   *   //map stream of strings into stream of their respective lengths.
   *   stream.map((a) -&gt; a.length()).forEach(i -&gt; System.out.println(i)).exec();
   * </code>
   * </pre>
   *
   * @param map mapping function
   * @param <R> type of the resulting stream.
   * @return an instance of {@code ResultStreamBuilder} containing mapped values,
   * ready for execution via result() or exec() methods.
   * @see #map(FunctionAsync)
   */
  <R> ResultStreamBuilder<R> map(FunctionSync<? super T,? extends R> map);

  /**
   * Method map produces a stream of values using provided asynchonous {@code map}
   * function.
   * Operation is remote by default. In the remote mode function {@code map} is
   * serialized and sent to every node participating in the call.
   * <p>
   * A re-instantiated {@code map} function is used to map values from node's
   * respective instance of {@code ResultStreamBuilder}.
   * <p>
   * Being async assumes that the function will use a provided Result&lt;R&gt; to
   * fulfill its promise.
   * <p>
   * Provided Result can be used to pass into a mapping service.
   * <p>
   * <pre>
   * <code>
   *   ResultStreamBuilder&lt;String&gt; stream = ...;
   *   //map stream of strings into stream of their respective lengths.
   *
   *   ResultStreamBuilder&lt;Integer&gt; intStream
   *     = stream.map((a, r) -&gt; _mapService.map(a, r));
   *
   *   intStream.forEach(i -&gt; System.out.println(i)).exec();
   *
   *   // _mapService interface should provide method with the following signature:
   *   // void map(String value, Result&lt;Integer&gt; result);
   * </code>
   * </pre>
   *
   * @param map function that maps an item to a resulting value
   * @param <R> type  of the resulting {@code ResultStreamBuilder}
   * @return instance of {@code ResultStreamBuilder} containing mapped values
   * ready for execution via result() or exec() methods.
   * @see #map(FunctionSync)
   */
  <R> ResultStreamBuilder<R> map(FunctionAsync<? super T,? extends R> map);

  /**
   * Sends a filter to be used for testing values passed into a corresponding
   * accept method.
   * <p>
   * Predicate submitted to the method is evaluated in blocking mode, which means
   * <p>
   * <pre>
   * <code>
   *   ResultStreamBuilder&lt;String&gt; stream = ...;
   *   stream.filter(s -&gt; s.contains("Hello"))
   *         .forEach(s -&gt; System.out.println(s))
   *         .exec();
   * </code>
   * </pre>
   * <p>
   * Operation is remote by default
   *
   * @param test predicate to be tested
   * @return an instance of {@code ResultStreamBuilder} containing filtered values,
   * ready for execution via result() or exec() methods.
   */
  ResultStreamBuilder<T> filter(PredicateSync<? super T> test);

  /**
   * Sends a filter to be used for testing values passed into a corresponding
   * accept method. PredicateAsync accepts a Result parameter which can be passed
   * into a remote service.
   * <p>
   * <p>
   * When the Predicate needs time to complete using PredicateAsync will allow
   * the corresponding ResultStream accept values without delay.
   * <p>
   * <p>
   * <p>
   * <pre>
   * <code>
   *   ResultStreamBuilder&lt;String&gt; stream = ...;
   *   Filter filter = ...;
   *
   *   stream.filter((s, r) -&gt; filter.test(s, r))
   *         .forEach(s -&gt; System.out.println(s)).exec();
   *
   * //Filter provides method with signature void test(String value, Result&lt;Boolean&gt; result).
   * </code>
   *
   *
   * </pre>
   * <p>
   * Operation is remote by default
   *
   * @param test predicate to be tested
   * @return an instance of {@code ResultStreamBuilder} containing filtered values,
   * ready for execution via result() or exec() methods.
   */
  ResultStreamBuilder<T> filter(PredicateAsync<? super T> test);

  //
  //
  //

  /**
   * Method {@code peek()} invokes method {@code accept()} of the consumer on
   * each item in a stream and passes that item along to the next operation
   * in the stream. It allows to visit every item before it's processed by the
   * next operation.
   * <p>
   * e.g
   * <pre>
   * <code>
   *   ResultStreamBuilder&lt;String&gt; stream = ...;
   *   stream.peek(s -&gt; visit(s))
   *         .forEach(s -&gt; System.out.println(s))
   *         .exec();
   * </code>
   * </pre>
   * <p>
   * Operation is remote by default
   *
   * @param consumer
   * @return instance of ResultStreamBuilder
   */
  ResultStreamBuilder<T> peek(ConsumerSync<? super T> consumer);

  /**
   * Produces an instance of a ResultStreamBuilder with local execution of
   * all operations. e.g. filter, peek, map, etc.
   *
   * @return instance of a local ResultStreamBuilder implementation
   */
  ResultStreamBuilder<T> local();

  // StreamBuilder<T> prefetch(int prefetch);

  /**
   * Collects the stream's values into a <code>java.util.Iterable</code>.
   * <p>
   * The <code>Iterable</code> may be available before the ResultStream
   * completes, which means the iterator might block waiting for new data.
   */
  ResultStreamBuilder<Iterable<T>> iter();

  /**
   * Collects the stream's values into a <code>java.util.Stream</code>.
   * <p>
   * The <code>Stream</code> may be available before the ResultStream
   * completes, which means the stream might block waiting for new data if
   * a blocking method if called on the returned stream.
   *
   * e.g.
   * <pre>
   * <code>
   *   public void test(Result&lt;List&lt;MyEntry&gt;&gt; result)
   *   {
   *     ResultStreamBuilder&lt;String&gt; stream = ...;
   *
   *     stream.stream().result(
   *       result.from(s -&gt; s.collect(Collectors.toCollection(ArrayList::new)))
   *     );
   *   }
   * </code>
   * </pre>
   *
   * @return ResultStreamBuilder&lt;Stream&lt;T&gt;&gt; containing an instance of Stream&lt;T&gt;
   *   to be obtained using result() method.
   */

  ResultStreamBuilder<Stream<T>> stream();

  /**
   * Creates a custom ResultStream stage builder. Used when the built-in
   * capabilities are not sufficient.
   * <p>
   * The argument is a factory to build the stage, called when the stream
   * is built. The function argument is the next ResultStream. The return
   * is the custom ResultStream.
   *
   * @param factory the custom factory for buildingt he result stream.
   */
  <R> ResultStreamBuilder<R> custom(FunctionSync<ResultStream<R>,ResultStream<T>> factory);
}
