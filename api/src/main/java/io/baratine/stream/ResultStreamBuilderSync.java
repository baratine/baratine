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
import io.baratine.service.ResultStream;

import java.util.stream.Stream;

/**
 * ResultStream builder that allows for blocking terminal calls.
 */
public interface ResultStreamBuilderSync<T> extends ResultStreamBuilder<T>
{
  //
  // terminals
  //
  
  T result();
  
  // foreach

  @Override
  ResultStreamBuilderSync<Void> forEach(ConsumerSync<? super T> consumer);
  
  @Override
  ResultStreamBuilderSync<Void> forEach(ConsumerAsync<? super T> consumer);
  
  @Override
  ResultStreamBuilderSync<T> first();
  
  @Override
  ResultStreamBuilderSync<Void> ignore();
  
  /**
   * Reduce with a binary function
   * 
   * <pre><code>
   * s = t1;
   * s = op(s, t2);
   * s = op(s, t3);
   * result = s;
   * </code></pre>
   */
  @Override
  ResultStreamBuilderSync<T> reduce(BinaryOperatorSync<T> op);
  
  /**
   * Reduce with a binary function and an initial value
   * 
   * <pre><code>
   * s = init;
   * s = op(s, t1);
   * s = op(s, t2);
   * result = s;
   * </code></pre>
   */
  @Override
  ResultStreamBuilderSync<T> reduce(T init, 
                                BinaryOperatorSync<T> op);
  
  @Override
  <R> ResultStreamBuilderSync<R> 
  reduce(R identity, 
         BiFunctionSync<R, ? super T, R> accumulator,
         BinaryOperatorSync<R> combiner);
  
  @Override
  <R> ResultStreamBuilderSync<R>
  collect(SupplierSync<R> init,
          BiConsumerSync<R,T> accum,
          BiConsumerSync<R,R> combiner);
  
  //
  // map/filter
  //
  
  @Override
  <R> ResultStreamBuilderSync<R> map(FunctionSync<? super T,? extends R> map);

  @Override
  <R> ResultStreamBuilderSync<R> map(FunctionAsync<? super T,? extends R> map);
  
  @Override
  ResultStreamBuilderSync<T> filter(PredicateSync<? super T> test);
  
  @Override
  ResultStreamBuilderSync<T> filter(PredicateAsync<? super T> test);
  
  //
  //
  //

  @Override
  <R> ResultStreamBuilderSync<R> custom(FunctionSync<ResultStream<R>,ResultStream<T>> factory);

  @Override
  ResultStreamBuilderSync<T> peek(ConsumerSync<? super T> consumer);
  
  @Override
  ResultStreamBuilderSync<T> local();
  
  // StreamBuilder<T> prefetch(int prefetch);
  
  @Override
  ResultStreamBuilderSync<Iterable<T>> iter();
  
  @Override
  ResultStreamBuilderSync<Stream<T>> stream();
}
